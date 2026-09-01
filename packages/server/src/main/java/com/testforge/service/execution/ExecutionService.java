package com.testforge.service.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.testforge.common.error.ApiException;
import com.testforge.dto.common.StatusView;
import com.testforge.dto.execution.ExecutionCompletePayload;
import com.testforge.dto.execution.ExecutionCompleteRequest;
import com.testforge.dto.execution.ExecutionProgressPayload;
import com.testforge.dto.execution.ExecutionRecipeView;
import com.testforge.dto.execution.ExecutionResponse;
import com.testforge.dto.execution.ExecutionStartRequest;
import com.testforge.dto.execution.ExecutionStepView;
import com.testforge.entity.conversation.Conversation;
import com.testforge.entity.conversation.enums.ConversationStatus;
import com.testforge.entity.execution.Execution;
import com.testforge.entity.execution.ExecutionRecipe;
import com.testforge.entity.execution.ExecutionStep;
import com.testforge.entity.execution.enums.ExecutionMode;
import com.testforge.entity.execution.enums.ExecutionRecipeStatus;
import com.testforge.entity.execution.enums.ExecutionStatus;
import com.testforge.entity.execution.enums.ExecutionStepStatus;
import com.testforge.entity.execution.enums.ExecutionType;
import com.testforge.entity.execution.enums.StepType;
import com.testforge.entity.recipe.Recipe;
import com.testforge.lock.ConversationLock;
import com.testforge.repository.conversation.ConversationRepository;
import com.testforge.repository.execution.ExecutionRecipeRepository;
import com.testforge.repository.execution.ExecutionRepository;
import com.testforge.repository.execution.ExecutionStepRepository;
import com.testforge.repository.recipe.RecipeRepository;
import com.testforge.sse.SseEventPublisher;
import com.testforge.sse.enums.SseEventType;
import com.testforge.utils.RecipeJsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 레시피 실행의 서버측 오케스트레이션. 실제 스텝 실행(API 호출 등)은 FE 브라우저가 수행하고,
 * 이 서비스는 실행의 <b>시작</b>(스냅샷/레코드 생성 + 대화방 executing 전이)과 <b>종료</b>
 * (상태 확정 + execution_complete + idle 전이 + 락 해제)를 담당한다.
 *
 * <p>대화방 락은 AI 처리와 동일한 {@link ConversationLock}을 공유한다. execute_recipe 카드가 뜬
 * 시점의 대화방은 idle(AI 처리 종결됨)이므로, 실행 시작이 새로 락을 잡고 executing으로 전이한 뒤
 * 종료 시 해제한다. 스텝 단위 보고/중지/이어서 실행/플랜은 다음 조각에서 추가한다.
 *
 * <p>JSON 필드는 RecipeService/ConversationService와 동일하게 문자열로 저장하고 응답에서 파싱한다.
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final ExecutionRepository executionRepository;
    private final ExecutionRecipeRepository executionRecipeRepository;
    private final ExecutionStepRepository executionStepRepository;
    private final RecipeRepository recipeRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationLock conversationLock;
    private final SseEventPublisher ssePublisher;

    // 스냅샷 직렬화용 로컬 매퍼 (공용 빈에 의존하지 않는 기존 패턴과 일관)
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExecutionService(ExecutionRepository executionRepository,
                            ExecutionRecipeRepository executionRecipeRepository,
                            ExecutionStepRepository executionStepRepository,
                            RecipeRepository recipeRepository,
                            ConversationRepository conversationRepository,
                            ConversationLock conversationLock,
                            SseEventPublisher ssePublisher) {
        this.executionRepository = executionRepository;
        this.executionRecipeRepository = executionRecipeRepository;
        this.executionStepRepository = executionStepRepository;
        this.recipeRepository = recipeRepository;
        this.conversationRepository = conversationRepository;
        this.conversationLock = conversationLock;
        this.ssePublisher = ssePublisher;
    }

    /**
     * 단일 레시피 실행 시작. 대화방 락을 잡고 executing으로 전이한 뒤, 레시피 스냅샷을 저장하고
     * EXECUTION / EXECUTION_RECIPE / EXECUTION_STEP(PENDING) 3계층 레코드를 생성한다.
     * {@code execution_progress}(시작)와 {@code session_status: executing}을 커밋 후 발행한다.
     *
     * <p>이미 처리 중인 대화방이면(락 경합) 409 CONVERSATION_BUSY. 레시피/대화방이 없으면 404.
     * 처리 중 예외로 실제 시작에 이르지 못하면 락을 해제한다(영구 잠금 방지).
     */
    @Transactional
    public ExecutionResponse start(Long conversationId, ExecutionStartRequest request) {
        if (request.userId() == null) {
            throw ApiException.invalidRequest("userId is required");
        }
        if (request.recipeId() == null) {
            throw ApiException.invalidRequest("recipeId is required");
        }

        // 대화방 선점: 이미 처리 중이면(락 경합) 이중 실행이므로 409로 거절
        if (!conversationLock.tryLock(conversationId)) {
            throw ApiException.conversationBusy(conversationId);
        }
        boolean started = false;
        try {
            Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                    .orElseThrow(() -> ApiException.conversationNotFound(conversationId));

            Recipe recipe = recipeRepository.findByIdAndDeletedAtIsNull(request.recipeId())
                    .orElseThrow(() -> ApiException.recipeNotFound(request.recipeId()));

            ExecutionMode mode = request.mode() == null ? ExecutionMode.AUTO : request.mode();

            // 1) EXECUTION 생성 (단일 = 레시피 1개짜리 플랜)
            Execution execution = new Execution(request.userId(), ExecutionType.SINGLE, mode);
            execution.setConversationId(conversationId);
            execution.setApiSpecId(recipe.getApiSpecId());
            execution.setTitle(recipe.getName());
            Execution savedExecution = executionRepository.save(execution);

            // 2) EXECUTION_RECIPE 생성 + 레시피 스냅샷 저장 (원본 독립)
            ExecutionRecipe executionRecipe = new ExecutionRecipe(savedExecution.getId(), 0);
            executionRecipe.setRecipeId(recipe.getId());
            executionRecipe.setRecipeName(recipe.getName());
            executionRecipe.setRecipeVersionNo(recipe.getCurrentVersion());
            executionRecipe.setRecipeSnapshotJson(snapshotOf(recipe));
            executionRecipe.setStatus(ExecutionRecipeStatus.RUNNING);
            executionRecipe.setStartedAt(LocalDateTime.now());
            ExecutionRecipe savedRecipe = executionRecipeRepository.save(executionRecipe);

            // 3) EXECUTION_STEP(PENDING) 생성 — 레시피 스텝 스냅샷 기준
            List<Map<String, Object>> steps = RecipeJsonUtil.parseSteps(recipe.getStepsJson());
            for (int i = 0; i < steps.size(); i++) {
                Map<String, Object> step = steps.get(i);
                ExecutionStep executionStep = new ExecutionStep(
                        savedRecipe.getId(), i, resolveStepType(step.get("type")));
                executionStep.setStepName(asString(step.get("name")));
                executionStepRepository.save(executionStep);
            }

            // 4) 대화방 executing 전이 (락은 유지 → 종료 시 해제)
            conversation.setStatus(ConversationStatus.EXECUTING);
            conversationRepository.save(conversation);

            // SSE: 실행 진행 시작 + 대화방 상태(executing). 커밋 후 발행.
            Long ownerId = conversation.getUserId();
            Long executionId = savedExecution.getId();
            publishAfterCommit(ownerId, SseEventType.SESSION_STATUS, conversationId,
                    com.testforge.dto.conversation.SessionStatusPayload.of(conversationId, ConversationStatus.EXECUTING));
            publishAfterCommit(ownerId, SseEventType.EXECUTION_PROGRESS, conversationId,
                    ExecutionProgressPayload.started(conversationId, executionId));

            started = true;
            log.info("Execution started: executionId={}, conversationId={}, recipeId={}, steps={}",
                    executionId, conversationId, recipe.getId(), steps.size());

            return toResponse(savedExecution);
        } finally {
            // 정상 시작이면 락 유지(종료 시 해제), 예외로 미시작이면 즉시 해제.
            if (!started) {
                conversationLock.unlock(conversationId);
            }
        }
    }

    /**
     * 실행 종료 보고. FE가 스텝 실행을 마쳤을 때(성공/부분/실패/중지) 최종 상태를 알린다.
     * EXECUTION 상태/종료시각/소요시간을 확정하고, 대화방을 idle로 되돌리며(락 해제),
     * {@code execution_complete} + {@code session_status: idle}을 커밋 후 발행한다.
     *
     * <p>이미 종료된 실행에 대한 재호출은 <b>멱등 no-op</b>(현재 상태 그대로 반환). RUNNING을 최종
     * 상태로 보고하면 400. 실행/대화방이 없으면 404.
     */
    @Transactional
    public ExecutionResponse complete(Long executionId, ExecutionCompleteRequest request) {
        if (request.status() == null || request.status() == ExecutionStatus.RUNNING) {
            throw ApiException.invalidRequest("terminal status is required (not RUNNING)");
        }
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> ApiException.executionNotFound(executionId));

        // 멱등: 이미 종료된 실행이면 상태 변경 없이 그대로 반환 (락/발행 재수행 안 함)
        if (execution.getStatus().isTerminal()) {
            log.info("Execution complete is no-op (already terminal): executionId={}, status={}",
                    executionId, execution.getStatus());
            return toResponse(execution);
        }

        // 상태/종료시각/소요시간 확정
        execution.setStatus(request.status());
        execution.setResultSummary(request.resultSummary());
        LocalDateTime finishedAt = LocalDateTime.now();
        execution.setFinishedAt(finishedAt);
        if (execution.getStartedAt() != null) {
            execution.setDurationMs(Duration.between(execution.getStartedAt(), finishedAt).toMillis());
        }
        Execution saved = executionRepository.save(execution);

        Long conversationId = saved.getConversationId();
        Long ownerId = resolveOwnerId(saved);

        // 대화방을 idle로 되돌리고 락 해제 (실행이 대화방에 연결된 경우에만)
        if (conversationId != null) {
            conversationRepository.findByIdAndDeletedAtIsNull(conversationId).ifPresent(conversation -> {
                conversation.setStatus(ConversationStatus.IDLE);
                conversationRepository.save(conversation);
            });
            conversationLock.unlock(conversationId);

            publishAfterCommit(ownerId, SseEventType.EXECUTION_COMPLETE, conversationId,
                    ExecutionCompletePayload.of(conversationId, executionId, saved.getStatus()));
            publishAfterCommit(ownerId, SseEventType.SESSION_STATUS, conversationId,
                    com.testforge.dto.conversation.SessionStatusPayload.of(conversationId, ConversationStatus.IDLE));
        }

        log.info("Execution completed: executionId={}, status={}, conversationId={}",
                executionId, saved.getStatus(), conversationId);
        return toResponse(saved);
    }

    /** 실행 상세 조회 (없으면 404) */
    @Transactional(readOnly = true)
    public ExecutionResponse detail(Long executionId) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> ApiException.executionNotFound(executionId));
        return toResponse(execution);
    }

    // ── helpers ──

    /** 레시피 전체 스냅샷 JSON (메타+스텝+변수+결과정의). 원본 독립 재현용 */
    private String snapshotOf(Recipe recipe) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("recipeId", recipe.getId());
        node.put("name", recipe.getName());
        node.put("description", recipe.getDescription());
        node.put("apiSpecId", recipe.getApiSpecId());
        node.put("versionNo", recipe.getCurrentVersion());
        node.put("tagsJson", recipe.getTags());
        node.put("variablesJson", recipe.getVariablesJson());
        node.put("stepsJson", recipe.getStepsJson());
        node.put("resultDefinitionJson", recipe.getResultDefinitionJson());
        node.put("resultTemplate", recipe.getResultTemplate());
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize recipe snapshot", e);
        }
    }

    /** 스텝 정의의 type 값을 StepType으로 매핑. 알 수 없으면 API로 간주(방어적 기본값) */
    private StepType resolveStepType(Object typeValue) {
        if (typeValue == null) {
            return StepType.API;
        }
        String raw = typeValue.toString().trim().toUpperCase(Locale.ROOT);
        try {
            return StepType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown step type '{}', defaulting to API", raw);
            return StepType.API;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /** 실행 소유자 ID. execution.userId를 신뢰(대화 삭제로 conversation이 없어도 SSE 대상 유지) */
    private Long resolveOwnerId(Execution execution) {
        return execution.getUserId();
    }

    /** 실행 상세를 응답으로 매핑 (하위 레시피/스텝 포함) */
    private ExecutionResponse toResponse(Execution execution) {
        List<ExecutionRecipe> recipes =
                executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(execution.getId());
        List<ExecutionRecipeView> recipeViews = recipes.stream().map(this::toRecipeView).toList();

        return new ExecutionResponse(
                execution.getId(),
                execution.getUserId(),
                execution.getConversationId(),
                execution.getApiSpecId(),
                StatusView.of(execution.getType()),
                execution.getTitle(),
                StatusView.of(execution.getMode()),
                StatusView.of(execution.getStatus()),
                RecipeJsonUtil.toObject(execution.getContextJson()),
                execution.getResultSummary(),
                recipeViews,
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getDurationMs());
    }

    private ExecutionRecipeView toRecipeView(ExecutionRecipe recipe) {
        List<ExecutionStep> steps =
                executionStepRepository.findByExecutionRecipeIdOrderByStepIndexAsc(recipe.getId());
        List<ExecutionStepView> stepViews = steps.stream().map(this::toStepView).toList();

        return new ExecutionRecipeView(
                recipe.getId(),
                recipe.getRecipeId(),
                recipe.getRecipeName(),
                recipe.getRecipeVersionNo(),
                recipe.getSequence(),
                StatusView.of(recipe.getStatus()),
                RecipeJsonUtil.toObject(recipe.getRecipeSnapshotJson()),
                RecipeJsonUtil.toObject(recipe.getResultValuesJson()),
                stepViews,
                recipe.getStartedAt(),
                recipe.getFinishedAt());
    }

    private ExecutionStepView toStepView(ExecutionStep step) {
        return new ExecutionStepView(
                step.getId(),
                step.getStepIndex(),
                step.getStepName(),
                StatusView.of(step.getStepType()),
                StatusView.of(step.getStatus()),
                step.getSummary(),
                RecipeJsonUtil.toObject(step.getUserInputJson()),
                RecipeJsonUtil.toObject(step.getResponseJson()),
                step.getErrorMessage(),
                step.getStartedAt(),
                step.getFinishedAt());
    }

    /**
     * 트랜잭션 커밋 후 SSE 발행 (ConversationService와 동일 패턴). 활성 트랜잭션이 있으면 afterCommit
     * 콜백으로 미루고, 없으면 즉시 발행한다. userId가 null이면 no-op.
     */
    private void publishAfterCommit(Long userId, SseEventType type, Long sessionId, Object data) {
        if (userId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ssePublisher.toUser(userId, type, sessionId, data);
                }
            });
        } else {
            ssePublisher.toUser(userId, type, sessionId, data);
        }
    }
}
