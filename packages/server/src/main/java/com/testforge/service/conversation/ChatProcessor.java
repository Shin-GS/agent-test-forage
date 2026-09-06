package com.testforge.service.conversation;

import com.testforge.ai.IntentContext;
import com.testforge.ai.IntentResolver;
import com.testforge.ai.IntentResult;
import com.testforge.ai.RecipeCandidate;
import com.testforge.ai.ServiceOption;
import com.testforge.common.error.ApiException;
import com.testforge.common.error.ErrorCode;
import com.testforge.dto.conversation.AssistantMessageDraft;
import com.testforge.entity.conversation.Message;
import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.enums.SpecStatus;
import com.testforge.repository.conversation.ConversationRepository;
import com.testforge.repository.conversation.MessageRepository;
import com.testforge.repository.recipe.RecipeRepository;
import com.testforge.repository.spec.ApiSpecRepository;
import com.testforge.utils.RecipeJsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 채팅 처리 오케스트레이터. 사용자 메시지 접수 후 "무엇을 응답할지"를 결정한다:
 * <ol>
 *   <li>대화방/이력/레시피·서비스로 {@link IntentContext} 조립 (intent-classification.md 컨텍스트 분기)</li>
 *   <li>{@link IntentResolver}로 tool 선택 (키 없으면 규칙 기반 목, 있으면 OpenAI 호환 실제 AI)</li>
 *   <li>tool 결과를 {@link AssistantMessageDraft}로 변환 (message 정책 + 카드 metadata)</li>
 *   <li>{@link ConversationService#completeAssistantTurn}로 저장/발행/종결(idle+락 해제) 위임</li>
 * </ol>
 *
 * <p>"어떻게 저장/발행/종결하는가"는 ConversationService가 담당하고, 이 클래스는 tool 분기와
 * 컨텍스트 조립만 책임진다. execute_recipe/propose_plan은 이번 조각에서는 실행 엔진 대신
 * "실행 진입 카드"만 남긴다(실제 실행은 다음 조각).
 */
@Component
public class ChatProcessor {

    private static final Logger log = LoggerFactory.getLogger(ChatProcessor.class);

    /** AI에게 전달하는 최근 대화 이력 건수 (ai-config.md 기본값). 목 단계에서도 동일 상한 적용 */
    private static final int HISTORY_LIMIT = 15;

    /** no_match 고정 안내 (intent-classification.md: message 없음 → FE/서버 고정 문구) */
    private static final String NO_MATCH_NOTICE =
            "요청하신 작업과 매칭되는 레시피를 찾지 못했어요. 다른 표현으로 다시 말씀해 주시겠어요?";

    private final IntentResolver intentResolver;
    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final RecipeRepository recipeRepository;
    private final ApiSpecRepository apiSpecRepository;
    private final InvestigateLoop investigateLoop;

    public ChatProcessor(IntentResolver intentResolver,
                         ConversationService conversationService,
                         ConversationRepository conversationRepository,
                         MessageRepository messageRepository,
                         RecipeRepository recipeRepository,
                         ApiSpecRepository apiSpecRepository,
                         InvestigateLoop investigateLoop) {
        this.intentResolver = intentResolver;
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.recipeRepository = recipeRepository;
        this.apiSpecRepository = apiSpecRepository;
        this.investigateLoop = investigateLoop;
    }

    /**
     * 접수된 사용자 메시지에 대한 AI 처리를 수행하고 assistant 턴을 확정한다.
     * 예외가 나도 대화방이 {@code ai_responding}에 갇히지 않도록, 실패 시 시스템 안내 메시지로 종결한다
     * (messaging.md 종결 보장). 종결(락 해제 포함)은 completeAssistantTurn이 담당한다.
     *
     * @param conversationId 대상 대화방
     * @param userId         발화 소유자 (SSE 대상)
     */
    public void process(Long conversationId, Long userId) {
        try {
            IntentContext context = buildContext(conversationId, userId);
            IntentResult result = intentResolver.resolve(context);

            // investigate는 단발 tool과 달리 반복 조회 루프를 돈다(investigation.md). 전용 서비스로 위임한다.
            if (result.tool() == com.testforge.ai.enums.ToolName.INVESTIGATE) {
                // hard guard: 서비스 미지정이면 조회할 스펙 컨텍스트가 없으므로 조회 없이 select_service로
                // 전환한다(프롬프트 유도에만 의존하지 않음 — investigation.md 조회 범위 제한).
                if (!context.hasService()) {
                    log.info("investigate without service -> select_service (hard guard): conversationId={}",
                            conversationId);
                    conversationService.completeAssistantTurn(conversationId,
                            AssistantMessageDraft.card(serviceSelectCard(List.of())));
                    return;
                }
                // 루프가 진행 블록/최종 답변/종결(idle+락 해제)을 자체 try/finally로 보장한다.
                investigateLoop.run(context);
                return;
            }

            AssistantMessageDraft draft = toDraft(result);
            conversationService.completeAssistantTurn(conversationId, draft);
        } catch (Exception e) {
            // 어떤 실패에도 대화방을 idle로 종결(무한 로딩 방지). 종결 자체가 또 실패하면 로그만 남긴다.
            log.error("Chat processing failed, releasing conversation to idle: conversationId={}",
                    conversationId, e);
            try {
                conversationService.completeAssistantTurn(conversationId, errorDraft(e));
            } catch (Exception releaseError) {
                log.error("Failed to release conversation to idle after processing error: conversationId={}",
                        conversationId, releaseError);
            }
        }
    }

    /**
     * 실패 종류에 맞는 시스템 안내 메시지를 만든다. AI 크레딧/한도 소진(AI_QUOTA_EXCEEDED)은 원인이
     * 명확하므로 일반 오류와 구분해 전용 문구로 안내한다(ai-config.md 크레딧 소진 처리, 목 폴백 없음).
     */
    private AssistantMessageDraft errorDraft(Exception e) {
        if (e instanceof ApiException api && api.getCode() == ErrorCode.AI_QUOTA_EXCEEDED) {
            return AssistantMessageDraft.system(
                    "AI 사용 한도에 도달했어요. 잠시 후 다시 시도해 주세요.",
                    RecipeJsonUtil.toJsonString(Map.of("level", "warn")));
        }
        return AssistantMessageDraft.system(
                "처리 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요.",
                RecipeJsonUtil.toJsonString(Map.of("level", "error")));
    }

    // ── 컨텍스트 조립 ──

    /**
     * 대화방 상태를 읽어 IntentContext를 만든다. 서비스 지정 여부에 따라 레시피/서비스 중 하나만 채운다
     * (intent-classification.md: 지정 시 레시피 목록, 미지정 시 서비스 목록).
     *
     * <p>여기서 읽는 필드(레시피 태그/이름, 스펙 설명 등)는 모두 즉시 로딩되는 스칼라라 지연 로딩이
     * 없다. 따라서 별도 트랜잭션 경계 없이 각 repository 호출이 독립 실행되어도 안전하다
     * (이 메서드는 같은 빈의 process()에서 호출되어 @Transactional 자기호출이 무효하기도 하다).
     */
    private IntentContext buildContext(Long conversationId, Long userId) {
        var conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Conversation not found for processing: " + conversationId));

        Long apiSpecId = conversation.getApiSpecId();

        // 최근 이력: 현재(마지막) 사용자 메시지를 제외하고, 오래된→최신 순으로 최대 HISTORY_LIMIT건
        List<Message> allMessages = messageRepository.findByConversationIdOrderBySeqAsc(conversationId);
        String utterance = latestUserContent(allMessages);
        List<IntentContext.HistoryTurn> history = toHistory(allMessages);

        List<RecipeCandidate> recipes = List.of();
        List<ServiceOption> services = List.of();
        if (apiSpecId != null) {
            // 소유 격리(auth.md): 대화방 소유자(userId) 기준 "COMMON + 본인 PRIVATE"만 후보로 로드.
            // 남의 PRIVATE가 AI 후보로 노출되지 않게 한다.
            recipes = loadRecipes(apiSpecId, userId);
        } else {
            services = loadServices();
        }

        String referenceId = latestUserReferenceId(allMessages);
        return new IntentContext(userId, conversationId, utterance, apiSpecId,
                recipes, services, referenceId, history);
    }

    /** 마지막 USER 메시지 content (없으면 빈 문자열) */
    private String latestUserContent(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.getRole() != null && "USER".equals(m.getRole().name())) {
                return m.getContent() == null ? "" : m.getContent();
            }
        }
        return "";
    }

    /** 마지막 USER 메시지의 referenceId (없으면 null) */
    private String latestUserReferenceId(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.getRole() != null && "USER".equals(m.getRole().name())) {
                return m.getReferenceId();
            }
        }
        return null;
    }

    /**
     * 이력 변환: 마지막 USER 메시지(현재 발화) 1건을 제외하고 최대 HISTORY_LIMIT건을 최신 쪽에서 취해
     * 오래된→최신 순으로 돌려준다. content가 없는 메시지(카드 등)는 타입 표기로 대체한다.
     */
    private List<IntentContext.HistoryTurn> toHistory(List<Message> messages) {
        // 현재 발화(마지막 USER 메시지)의 인덱스를 찾아 그 이전까지만 이력으로 사용
        int currentUserIdx = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.getRole() != null && "USER".equals(m.getRole().name())) {
                currentUserIdx = i;
                break;
            }
        }
        List<Message> prior = currentUserIdx >= 0 ? messages.subList(0, currentUserIdx) : messages;

        // 최신 HISTORY_LIMIT건만
        int from = Math.max(0, prior.size() - HISTORY_LIMIT);
        List<IntentContext.HistoryTurn> turns = new ArrayList<>();
        for (Message m : prior.subList(from, prior.size())) {
            String role = m.getRole() == null ? "assistant" : m.getRole().name().toLowerCase();
            String content = m.getContent();
            if (content == null || content.isBlank()) {
                // 카드/진행 메시지 등 본문 없는 항목은 타입만 표기(토큰 절약)
                content = "[" + (m.getType() == null ? "message" : m.getType().name().toLowerCase()) + "]";
            }
            turns.add(new IntentContext.HistoryTurn(role, content));
        }
        return turns;
    }

    /**
     * 서비스(스펙)의 미삭제 레시피를 후보 표현으로 로드 (변수 스키마 요약 포함 — 발화값 추출용).
     * 소유 격리(auth.md): "COMMON + 요청자 본인 PRIVATE"만 로드해 남의 PRIVATE 노출을 막는다.
     *
     * @param apiSpecId 대상 서비스(스펙)
     * @param actorId   요청자(대화방 소유자) ID — PRIVATE 소유 격리용
     */
    private List<RecipeCandidate> loadRecipes(Long apiSpecId, Long actorId) {
        List<Recipe> recipes = recipeRepository.findVisibleByApiSpecId(apiSpecId, actorId);
        List<RecipeCandidate> candidates = new ArrayList<>();
        for (Recipe r : recipes) {
            candidates.add(new RecipeCandidate(
                    r.getId(), r.getName(), r.getDescription(),
                    RecipeJsonUtil.parseTags(r.getTags()),
                    summarizeVariables(r.getVariablesJson())));
        }
        return candidates;
    }

    /**
     * 레시피 변수 정의(variablesJson)를 발화값 추출용 최소 요약으로 변환한다
     * (ai-config.md "레시피 변수 스키마 전달": key/label/type/required + 있으면 description).
     * key는 {@code key} 우선, 없으면 {@code name}(userInput 매칭 키와 정합). 변수가 없으면 빈 리스트.
     */
    private List<RecipeCandidate.VariableSummary> summarizeVariables(String variablesJson) {
        List<Map<String, Object>> variables = RecipeJsonUtil.parseSteps(variablesJson);
        List<RecipeCandidate.VariableSummary> summaries = new ArrayList<>();
        for (Map<String, Object> variable : variables) {
            String key = variableKey(variable);
            if (key == null) {
                continue;
            }
            summaries.add(new RecipeCandidate.VariableSummary(
                    key,
                    asStringOrNull(variable.get("label")),
                    asStringOrNull(variable.get("type")),
                    isRequired(variable),
                    asStringOrNull(variable.get("description"))));
        }
        return summaries;
    }

    /** 값을 문자열로 변환하되 null/빈이면 null (토큰 절약: 빈 필드는 생략 대상) */
    private String asStringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * ACTIVE 서비스 목록을 서비스 옵션으로 로드 (미지정 대화방에서 서비스 선택용).
     * ACTIVE-only 필터는 쿼리에서 보장한다(SpecQueryService 공용 목록과 정책 일원화).
     */
    private List<ServiceOption> loadServices() {
        List<ApiSpec> specs =
                apiSpecRepository.findByStatusAndDeletedAtIsNullOrderByNameAsc(SpecStatus.ACTIVE);
        List<ServiceOption> options = new ArrayList<>();
        for (ApiSpec spec : specs) {
            options.add(ServiceOption.of(spec.getId(), spec.getName(), spec.getServiceDescription()));
        }
        return options;
    }

    // ── tool → draft 변환 (message 정책 + 카드 metadata: messaging.md) ──

    private AssistantMessageDraft toDraft(IntentResult result) {
        return switch (result.tool()) {
            case CHAT, CLARIFY -> AssistantMessageDraft.text(result.message());
            case NO_MATCH -> AssistantMessageDraft.system(
                    NO_MATCH_NOTICE, RecipeJsonUtil.toJsonString(Map.of("level", "info")));
            case EXECUTE_RECIPE -> AssistantMessageDraft.card(
                    executionModeCard(result.recipeId(), result.extractedValues()));
            case PROPOSE_PLAN -> AssistantMessageDraft.card(planCard(result.recipeIds(), result.rationale()));
            case SELECT_SERVICE -> AssistantMessageDraft.card(serviceSelectCard(result.suggestedServices()));
            case SHOW_CANDIDATES -> AssistantMessageDraft.card(candidatesCard(result.candidates()));
            // investigate는 process()에서 InvestigateLoop로 위임되어 여기 도달하지 않는다(방어적 처리).
            case INVESTIGATE -> throw new IllegalStateException(
                    "INVESTIGATE must be handled by InvestigateLoop, not toDraft");
        };
    }

    /**
     * execution_mode 카드: 실행 진입점 ([바로 실행]/[값 확인 후 실행]).
     * "뭘 실행하는지, 어떤 값이 필요한지"를 카드에서 바로 보여준다(messaging.md execution_mode 카드 상세).
     * extractedValues는 발화에서 추출된 초기 입력값으로, 실행 시작 시 initialContext로 전달되고
     * 여기서는 각 입력 변수의 현재 값/출처(source) 산출에도 사용된다.
     *
     * <p>레시피가 없으면(삭제 등) 최소 정보(recipeId/buttons/extractedValues)만 담는다.
     */
    private String executionModeCard(Long recipeId, Map<String, Object> extractedValues) {
        Map<String, Object> values = extractedValues == null ? Map.of() : extractedValues;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cardType", "execution_mode");
        meta.put("recipeId", recipeId);

        Recipe recipe = recipeId == null ? null
                : recipeRepository.findByIdAndDeletedAtIsNull(recipeId).orElse(null);
        if (recipe != null) {
            meta.put("recipeName", recipe.getName());
            meta.put("description", recipe.getDescription());
            meta.put("inputVariables", buildInputVariables(recipe.getVariablesJson(), values));
        }

        meta.put("buttons", List.of("auto", "manual"));
        meta.put("extractedValues", values);
        return RecipeJsonUtil.toJsonString(meta);
    }

    /**
     * 레시피의 사용자 입력 변수 정의(variablesJson)를 카드용 항목으로 변환한다
     * (messaging.md inputVariables: {@code {key, label, value, source, required}}).
     * 값/출처(source) 산출 규칙:
     * <ul>
     *   <li>{@code extractedValues}에 값이 있으면 → value=그 값, source={@code "utterance"}(🗣️ 발화)</li>
     *   <li>없고 변수 정의에 {@code default}가 있으면 → value=기본값, source={@code "default"}(📌 기본값)</li>
     *   <li>둘 다 없으면 → value=null, source={@code "none"}(✏️ 미입력)</li>
     * </ul>
     * 변수 매칭 키는 {@code key} 우선, 없으면 {@code name}(ExecutionService.variableKey와 정합).
     */
    private List<Map<String, Object>> buildInputVariables(String variablesJson,
                                                          Map<String, Object> extractedValues) {
        List<Map<String, Object>> variables = RecipeJsonUtil.parseSteps(variablesJson);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> variable : variables) {
            String key = variableKey(variable);
            if (key == null) {
                continue;
            }
            Object label = variable.get("label");

            Object value;
            String source;
            if (extractedValues != null && extractedValues.containsKey(key)
                    && extractedValues.get(key) != null) {
                value = extractedValues.get(key);
                source = "utterance";
            } else if (variable.containsKey("default") && variable.get("default") != null) {
                value = variable.get("default");
                source = "default";
            } else {
                value = null;
                source = "none";
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("label", label == null ? key : label);
            item.put("value", value);
            item.put("source", source);
            item.put("required", isRequired(variable));
            items.add(item);
        }
        return items;
    }

    /** 변수 정의에서 매칭 키를 얻는다. {@code key} 우선, 없으면 {@code name}(ExecutionService와 정합). */
    private String variableKey(Map<String, Object> variable) {
        Object key = variable.get("key");
        if (key == null) {
            key = variable.get("name");
        }
        return key == null ? null : key.toString();
    }

    /** required=true 여부. Boolean/문자열("true") 모두 허용. 값 없으면 false. */
    private boolean isRequired(Map<String, Object> variable) {
        Object required = variable.get("required");
        if (required instanceof Boolean b) {
            return b;
        }
        return required != null && "true".equalsIgnoreCase(required.toString());
    }

    /**
     * plan 카드: 순차 실행 레시피 목록 (플랜 UI 진입). recipeIds 순서대로 각 레시피의 경량 정보
     * ({@code recipeId/recipeName/serviceName})와 값 미리보기(기본값만, source={@code "default"} 📌)를 담는다.
     * {@code rationale}(제안 근거)이 있으면 함께 실어 사용자가 "왜 이 조합인지"를 카드에서 바로 볼 수 있게 한다.
     *
     * <p>값 미리보기는 <b>기본값(📌)만</b> 담는다(발화 추출값 미사용) — 플랜은 여러 레시피를 조합 제안하는
     * 단계라 발화값 매핑을 확정하지 않고, 실제 값 확정/입력은 실행 시작 후 각 레시피의 액션 피커에서 한다.
     * 삭제된 레시피는 최소 정보(recipeId만)로 남긴다.
     */
    private String planCard(List<Long> recipeIds, String rationale) {
        List<Long> ids = recipeIds == null ? List.of() : recipeIds;
        List<Map<String, Object>> recipeItems = new ArrayList<>();
        for (Long recipeId : ids) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("recipeId", recipeId);
            Recipe recipe = recipeId == null ? null
                    : recipeRepository.findByIdAndDeletedAtIsNull(recipeId).orElse(null);
            if (recipe != null) {
                item.put("recipeName", recipe.getName());
                item.put("serviceName", resolveServiceName(recipe.getApiSpecId()));
                // 값 미리보기: 기본값이 있는 변수만 (📌 default). 발화값은 담지 않는다.
                item.put("inputPreview", buildDefaultPreview(recipe.getVariablesJson()));
            }
            recipeItems.add(item);
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cardType", "plan");
        meta.put("recipeIds", ids);
        meta.put("recipes", recipeItems);
        if (rationale != null && !rationale.isBlank()) {
            meta.put("rationale", rationale);
        }
        return RecipeJsonUtil.toJsonString(meta);
    }

    /** apiSpecId → 서비스 표시명(serviceDescription > name). 없으면 null. */
    private String resolveServiceName(Long apiSpecId) {
        if (apiSpecId == null) {
            return null;
        }
        ApiSpec spec = apiSpecRepository.findByIdAndDeletedAtIsNull(apiSpecId).orElse(null);
        if (spec == null) {
            return null;
        }
        String description = spec.getServiceDescription();
        return (description != null && !description.isBlank()) ? description : spec.getName();
    }

    /**
     * plan 카드 값 미리보기: 변수 정의 중 {@code default}가 있는 것만 {@code {key,label,value,source:"default"}}로.
     * 발화 추출값을 쓰지 않으므로(플랜 단계) 기본값(📌)만 노출한다. 기본값 없는 변수는 미리보기에서 제외한다.
     */
    private List<Map<String, Object>> buildDefaultPreview(String variablesJson) {
        List<Map<String, Object>> variables = RecipeJsonUtil.parseSteps(variablesJson);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> variable : variables) {
            String key = variableKey(variable);
            if (key == null || !variable.containsKey("default") || variable.get("default") == null) {
                continue;
            }
            Object label = variable.get("label");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("label", label == null ? key : label);
            item.put("value", variable.get("default"));
            item.put("source", "default");
            items.add(item);
        }
        return items;
    }

    /** service_select 카드: 서비스 선택 버튼 (messaging.md: services:[{name,label}]) */
    private String serviceSelectCard(List<ServiceOption> suggested) {
        List<Map<String, Object>> services = new ArrayList<>();
        for (ServiceOption s : suggested) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("apiSpecId", s.apiSpecId());
            item.put("name", s.name());
            item.put("label", s.label() == null ? s.name() : s.label());
            services.add(item);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cardType", "service_select");
        meta.put("services", services);
        return RecipeJsonUtil.toJsonString(meta);
    }

    /** candidates 카드: 유사 레시피 후보 (messaging.md: recipes:[{id,name,description}]) */
    private String candidatesCard(List<RecipeCandidate> candidates) {
        List<Map<String, Object>> recipes = new ArrayList<>();
        for (RecipeCandidate c : candidates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.id());
            item.put("name", c.name());
            item.put("description", c.description());
            recipes.add(item);
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cardType", "candidates");
        meta.put("recipes", recipes);
        return RecipeJsonUtil.toJsonString(meta);
    }
}
