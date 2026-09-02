package com.testforge.service.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.testforge.common.error.ApiException;
import com.testforge.dto.common.CursorPage;
import com.testforge.dto.common.StatusView;
import com.testforge.dto.execution.ExecutionCompletePayload;
import com.testforge.dto.execution.ExecutionSummaryView;
import com.testforge.dto.execution.ExecutionCompleteRequest;
import com.testforge.dto.execution.ExecutionProgressPayload;
import com.testforge.dto.execution.ExecutionRecipeView;
import com.testforge.dto.execution.ExecutionResponse;
import com.testforge.dto.execution.ExecutionStartRequest;
import com.testforge.dto.execution.ExecutionStepView;
import com.testforge.dto.execution.StepReportRequest;
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
import org.springframework.data.domain.PageRequest;
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
        // complete는 정상 완료 보고 전용(SUCCESS/PARTIAL/FAILED). 중지/취소(STOPPED/CANCELLED)는
        // 반드시 stop/cancel API 경유여야 대화방 해제·안내 메시지·요약이 일관되게 처리되므로 거부한다.
        if (request.status() == null
                || request.status() == ExecutionStatus.RUNNING
                || request.status() == ExecutionStatus.STOPPED
                || request.status() == ExecutionStatus.CANCELLED) {
            throw ApiException.invalidRequest(
                    "complete accepts SUCCESS/PARTIAL/FAILED only; use stop/cancel for STOPPED/CANCELLED");
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

        // 계층 정합: 아직 진행 중인 하위 레시피(EXECUTION_RECIPE)를 실행 최종 상태에 맞춰 종료한다.
        // (단일 실행 기준. 플랜의 레시피별 세밀한 성공/실패 롤업은 플랜 조각에서 다룬다.)
        finalizeRunningRecipes(executionId, request.status(), finishedAt);

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

    /** 히스토리 목록 페이지 기본/최대 크기 (무한 스크롤 UX + 과도 로딩 방지) */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 사용자 실행 히스토리의 커서 페이지 조회 (최신순 무한 스크롤). 상태/키워드는 옵션 필터다.
     * 경량 요약({@link ExecutionSummaryView})만 담아 목록 부하를 줄인다(상세는 detail로 별도 조회).
     *
     * @param userId 필수. 없으면 400
     * @param status 옵션 상태 필터 (null이면 전체)
     * @param keyword 옵션 제목 키워드 (null/빈이면 전체)
     * @param cursor 옵션 커서 (null이면 첫 페이지). 이전 응답의 nextCursor를 그대로 전달
     * @param size 페이지 크기 (기본 20, 최대 50)
     */
    @Transactional(readOnly = true)
    public CursorPage<ExecutionSummaryView> history(Long userId, ExecutionStatus status,
                                                    String keyword, String cursor, Integer size) {
        if (userId == null) {
            throw ApiException.invalidRequest("userId is required");
        }
        int limit = normalizeSize(size);
        String normalizedKeyword = escapeLike((keyword == null || keyword.isBlank()) ? null : keyword.trim());
        Long cursorId = decodeCursor(cursor);

        // hasNext 판정을 위해 limit+1건 조회 (정렬은 쿼리에 포함, Pageable은 limit 용도)
        List<Execution> rows = executionRepository.findHistoryByCursor(
                userId, status, normalizedKeyword, cursorId, PageRequest.of(0, limit + 1));
        return toCursorPage(rows, limit);
    }

    /**
     * 특정 대화방의 실행 목록 커서 페이지 (패널에서 현재 대화방 실행 보기). 대화방 존재/삭제 검증 후
     * 커서 페이지를 돌려준다.
     */
    @Transactional(readOnly = true)
    public CursorPage<ExecutionSummaryView> historyByConversation(Long conversationId, String cursor,
                                                                  Integer size) {
        conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> ApiException.conversationNotFound(conversationId));
        int limit = normalizeSize(size);
        Long cursorId = decodeCursor(cursor);

        List<Execution> rows = executionRepository.findByConversationIdByCursor(
                conversationId, cursorId, PageRequest.of(0, limit + 1));
        return toCursorPage(rows, limit);
    }

    /** limit+1건 조회 결과를 커서 페이지로 변환 (초과분 유무로 hasNext 판정, 초과분은 잘라냄) */
    private CursorPage<ExecutionSummaryView> toCursorPage(List<Execution> rows, int limit) {
        boolean hasNext = rows.size() > limit;
        List<Execution> pageRows = hasNext ? rows.subList(0, limit) : rows;
        List<ExecutionSummaryView> items = pageRows.stream().map(this::toSummaryView).toList();
        if (!hasNext) {
            return CursorPage.last(items);
        }
        Execution lastRow = pageRows.get(pageRows.size() - 1);
        // 커서 = 마지막 항목의 id (불투명 문자열). id는 정렬 키이자 유일 키라 이거면 충분하다.
        return CursorPage.of(items, String.valueOf(lastRow.getId()));
    }

    /** size 정규화: null이면 기본값, 범위(1~MAX)로 클램프 */
    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** 커서 디코딩(id). null/빈/형식 불량이면 첫 페이지(null)로 간주 */
    private Long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid cursor value, treating as first page: {}", cursor);
            return null;
        }
    }

    /**
     * LIKE 검색어의 와일드카드를 이스케이프한다. {@code \ % _}를 리터럴로 매칭하기 위해 앞에 {@code \}를
     * 붙인다(쿼리는 {@code ESCAPE '\'} 사용). 역슬래시를 먼저 처리해 이중 이스케이프를 피한다.
     */
    private String escapeLike(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /** 실행 → 경량 요약 뷰 (스텝 상세 제외) */
    private ExecutionSummaryView toSummaryView(Execution execution) {
        return new ExecutionSummaryView(
                execution.getId(),
                execution.getConversationId(),
                execution.getApiSpecId(),
                StatusView.of(execution.getType()),
                execution.getTitle(),
                StatusView.of(execution.getStatus()),
                execution.getResultSummary(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getDurationMs());
    }

    /**
     * 대화방에서 진행 중(RUNNING)인 실행을 지정 종료 상태로 마감한다(취소/중지 모두 STOPPED).
     * 대화방 상태 전이(idle)와 락 해제는 호출측(ConversationService.releaseToIdle)이 담당하므로,
     * 여기서는 EXECUTION 레코드 종료 + {@code execution_complete} 발행만 수행한다.
     *
     * <p>RUNNING 실행이 없으면(이미 종료됐거나 실행이 없던 대화) no-op. FE의 별도 complete 호출과
     * 겹쳐도 complete가 멱등이라 안전하다. 실행 중이던 스텝의 상태는 현재 값 그대로 보존한다
     * (히스토리에 그대로 남는다). 중지=STOPPED / 취소=CANCELLED로 구분해 기록하며, 사용자가 히스토리에서
     * 무엇이 있었는지 알 수 있도록 {@code resultSummary}를 "사유 · 완료/전체 스텝" 형식으로 자동 채운다.
     *
     * @param conversationId 대상 대화방
     * @param terminalStatus 종료 상태 (STOPPED 또는 CANCELLED)
     */
    @Transactional
    public void terminateRunningForConversation(Long conversationId, ExecutionStatus terminalStatus) {
        if (conversationId == null) {
            return;
        }
        List<Execution> running = executionRepository.findByConversationIdAndStatus(
                conversationId, ExecutionStatus.RUNNING);
        for (Execution execution : running) {
            execution.setStatus(terminalStatus);
            LocalDateTime finishedAt = LocalDateTime.now();
            execution.setFinishedAt(finishedAt);
            if (execution.getStartedAt() != null) {
                execution.setDurationMs(Duration.between(execution.getStartedAt(), finishedAt).toMillis());
            }
            // 히스토리 표시용 요약 자동 생성 (예: "취소됨 · 1/3 스텝 완료"). 사용자가 이미 요약을
            // 남겼다면(드묾) 덮어쓰지 않는다.
            if (execution.getResultSummary() == null || execution.getResultSummary().isBlank()) {
                execution.setResultSummary(buildTerminationSummary(execution.getId(), terminalStatus));
            }
            executionRepository.save(execution);

            // 계층 정합: 진행 중인 하위 레시피도 같은 종료 상태로 맞춘다 (Execution만 종료되고
            // ExecutionRecipe는 RUNNING으로 남는 불일치 방지)
            finalizeRunningRecipes(execution.getId(), terminalStatus, finishedAt);

            publishAfterCommit(execution.getUserId(), SseEventType.EXECUTION_COMPLETE, conversationId,
                    ExecutionCompletePayload.of(conversationId, execution.getId(), terminalStatus));
            log.info("Execution terminated by conversation control: executionId={}, status={}",
                    execution.getId(), terminalStatus);
        }
    }

    /**
     * 중단(중지/취소) 시 히스토리 표시용 요약을 만든다. 사유(중지/취소)와 완료 스텝 수를 담아
     * 사용자가 히스토리에서 "무슨 일이 있었나"를 바로 알 수 있게 한다.
     * 예: {@code "취소됨 · 1/3 스텝 완료"}, {@code "중지됨 · 2/3 스텝 완료"}.
     */
    private String buildTerminationSummary(Long executionId, ExecutionStatus terminalStatus) {
        int total = 0;
        int done = 0;
        for (ExecutionRecipe recipe : executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId)) {
            for (ExecutionStep step : executionStepRepository.findByExecutionRecipeIdOrderByStepIndexAsc(recipe.getId())) {
                total++;
                if (step.getStatus() == ExecutionStepStatus.SUCCESS) {
                    done++;
                }
            }
        }
        String reason = terminalStatus == ExecutionStatus.CANCELLED ? "취소됨" : "중지됨";
        return reason + " · " + done + "/" + total + " 스텝 완료";
    }

    /** 원시 응답 저장 상한 (1MB 초과 시 절단, execution.md) */
    private static final int RESPONSE_MAX_CHARS = 1_000_000;

    /**
     * 스텝 실행 결과 보고. FE가 한 스텝을 실행한 뒤 결과를 보고하면 EXECUTION_STEP을 갱신하고,
     * {@code extractedValues}를 실행 전역 context(EXECUTION.CONTEXT_JSON)에 누적한 뒤
     * {@code execution_progress}(stepIndex/status/summary)를 발행한다.
     *
     * <p>스텝은 {@code executionId}에 속해야 하며(경로 검증), 실행이 이미 종료됐으면 400
     * (종료된 실행에 스텝 보고 불가). 스텝/실행이 없으면 404. 응답 본문은 상한을 넘으면 잘라 저장한다.
     *
     * @param executionId 소속 실행 (소유 검증용)
     * @param stepId      보고 대상 스텝
     */
    @Transactional
    public ExecutionStepView reportStep(Long executionId, Long stepId, StepReportRequest request) {
        if (request.status() == null || request.status() == ExecutionStepStatus.PENDING) {
            throw ApiException.invalidRequest("terminal step status is required (not PENDING)");
        }

        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> ApiException.executionNotFound(executionId));
        if (execution.getStatus().isTerminal()) {
            throw ApiException.invalidRequest("execution is already terminal: " + executionId);
        }

        ExecutionStep step = executionStepRepository.findById(stepId)
                .orElseThrow(() -> ApiException.executionStepNotFound(stepId));

        // 스텝이 이 실행에 속하는지 검증 (step → recipe → execution)
        ExecutionRecipe recipe = executionRecipeRepository.findById(step.getExecutionRecipeId())
                .orElseThrow(() -> ApiException.executionStepNotFound(stepId));
        if (!recipe.getExecutionId().equals(executionId)) {
            throw ApiException.invalidRequest(
                    "step " + stepId + " does not belong to execution " + executionId);
        }

        // 스텝 결과 반영
        step.setStatus(request.status());
        step.setSummary(request.summary());
        step.setUserInputJson(RecipeJsonUtil.toJsonString(request.userInput()));
        step.setResponseJson(truncateResponse(RecipeJsonUtil.toJsonString(request.response())));
        step.setErrorMessage(request.errorMessage());
        step.setFinishedAt(LocalDateTime.now());
        if (step.getStartedAt() == null) {
            step.setStartedAt(step.getFinishedAt());
        }
        executionStepRepository.save(step);

        // context 누적: 기존 context에 extractedValues를 병합
        if (request.extractedValues() != null && !request.extractedValues().isEmpty()) {
            execution.setContextJson(mergeContext(execution.getContextJson(), request.extractedValues()));
            executionRepository.save(execution);
        }

        // SSE: 스텝 진행 발행
        Long conversationId = execution.getConversationId();
        publishAfterCommit(execution.getUserId(), SseEventType.EXECUTION_PROGRESS, conversationId,
                new ExecutionProgressPayload(conversationId, executionId,
                        step.getStepIndex(), request.status().getCode(), request.summary()));

        log.info("Step reported: executionId={}, stepId={}, stepIndex={}, status={}",
                executionId, stepId, step.getStepIndex(), request.status());
        return toStepView(step);
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

    /**
     * 실행 종료 시 아직 진행 중(RUNNING)인 하위 EXECUTION_RECIPE를 실행 최종 상태에 대응하는 종료
     * 상태로 맞춘다. Execution만 종료되고 ExecutionRecipe가 RUNNING으로 남아 계층 상태가 어긋나는
     * 것을 막는다. (스텝(EXECUTION_STEP) 레벨의 세밀한 롤업은 플랜/재개 조각에서 다룬다.)
     */
    private void finalizeRunningRecipes(Long executionId, ExecutionStatus executionStatus,
                                        LocalDateTime finishedAt) {
        ExecutionRecipeStatus recipeStatus = toRecipeStatus(executionStatus);
        List<ExecutionRecipe> recipes =
                executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId);
        for (ExecutionRecipe recipe : recipes) {
            if (recipe.getStatus() == ExecutionRecipeStatus.RUNNING
                    || recipe.getStatus() == ExecutionRecipeStatus.PENDING) {
                recipe.setStatus(recipeStatus);
                if (recipe.getFinishedAt() == null) {
                    recipe.setFinishedAt(finishedAt);
                }
                executionRecipeRepository.save(recipe);
            }
        }
    }

    /** 실행 상태 → 레시피 실행 상태 매핑. PARTIAL은 레시피 레벨에 없어 FAILED로 수렴(단일 실행 기준) */
    private ExecutionRecipeStatus toRecipeStatus(ExecutionStatus executionStatus) {
        return switch (executionStatus) {
            case SUCCESS -> ExecutionRecipeStatus.SUCCESS;
            case STOPPED -> ExecutionRecipeStatus.STOPPED;
            case CANCELLED -> ExecutionRecipeStatus.CANCELLED;
            case FAILED, PARTIAL -> ExecutionRecipeStatus.FAILED;
            case RUNNING -> ExecutionRecipeStatus.RUNNING; // 종료 경로에선 도달하지 않음
        };
    }

    /** 원시 응답 문자열을 상한으로 절단 (1MB 초과 시, execution.md). null이면 null */
    private String truncateResponse(String responseJson) {
        if (responseJson == null) {
            return null;
        }
        if (responseJson.length() <= RESPONSE_MAX_CHARS) {
            return responseJson;
        }
        log.warn("Response truncated: {} chars -> {} chars", responseJson.length(), RESPONSE_MAX_CHARS);
        return responseJson.substring(0, RESPONSE_MAX_CHARS);
    }

    /**
     * 기존 context JSON에 새 추출값을 병합해 JSON 문자열로 돌려준다. 같은 키는 새 값으로 덮어쓴다.
     * 기존 context가 없으면 새 값만으로 구성한다.
     */
    @SuppressWarnings("unchecked")
    private String mergeContext(String currentContextJson, Map<String, Object> extractedValues) {
        Map<String, Object> merged = new java.util.LinkedHashMap<>();
        Object current = RecipeJsonUtil.toObject(currentContextJson);
        if (current instanceof Map<?, ?> currentMap) {
            merged.putAll((Map<String, Object>) currentMap);
        }
        merged.putAll(extractedValues);
        return RecipeJsonUtil.toJsonString(merged);
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
