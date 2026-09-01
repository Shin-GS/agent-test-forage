package com.testforge.service.conversation;

import com.testforge.ai.IntentContext;
import com.testforge.ai.IntentResolver;
import com.testforge.ai.IntentResult;
import com.testforge.ai.RecipeCandidate;
import com.testforge.ai.ServiceOption;
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
 *   <li>{@link IntentResolver}로 tool 선택 (지금은 규칙 기반 목, 추후 Spring AI)</li>
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

    public ChatProcessor(IntentResolver intentResolver,
                         ConversationService conversationService,
                         ConversationRepository conversationRepository,
                         MessageRepository messageRepository,
                         RecipeRepository recipeRepository,
                         ApiSpecRepository apiSpecRepository) {
        this.intentResolver = intentResolver;
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.recipeRepository = recipeRepository;
        this.apiSpecRepository = apiSpecRepository;
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
            AssistantMessageDraft draft = toDraft(result);
            conversationService.completeAssistantTurn(conversationId, draft);
        } catch (Exception e) {
            // 어떤 실패에도 대화방을 idle로 종결(무한 로딩 방지). 종결 자체가 또 실패하면 로그만 남긴다.
            log.error("Chat processing failed, releasing conversation to idle: conversationId={}",
                    conversationId, e);
            try {
                conversationService.completeAssistantTurn(conversationId,
                        AssistantMessageDraft.system(
                                "처리 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요.",
                                RecipeJsonUtil.toJsonString(Map.of("level", "error"))));
            } catch (Exception releaseError) {
                log.error("Failed to release conversation to idle after processing error: conversationId={}",
                        conversationId, releaseError);
            }
        }
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
            recipes = loadRecipes(apiSpecId);
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

    /** 서비스(스펙)의 미삭제 레시피를 후보 표현으로 로드 */
    private List<RecipeCandidate> loadRecipes(Long apiSpecId) {
        List<Recipe> recipes = recipeRepository.search(apiSpecId, null, null, null);
        List<RecipeCandidate> candidates = new ArrayList<>();
        for (Recipe r : recipes) {
            candidates.add(new RecipeCandidate(
                    r.getId(), r.getName(), r.getDescription(),
                    RecipeJsonUtil.parseTags(r.getTags())));
        }
        return candidates;
    }

    /** ACTIVE 서비스 목록을 서비스 옵션으로 로드 (미지정 대화방에서 서비스 선택용) */
    private List<ServiceOption> loadServices() {
        List<ApiSpec> specs = apiSpecRepository.findByDeletedAtIsNullOrderByNameAsc();
        List<ServiceOption> options = new ArrayList<>();
        for (ApiSpec spec : specs) {
            if (spec.getStatus() == SpecStatus.INACTIVE) {
                // 비활성 서비스는 매칭/선택 대상에서 제외
                continue;
            }
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
            case EXECUTE_RECIPE -> AssistantMessageDraft.card(executionModeCard(result.recipeId()));
            case PROPOSE_PLAN -> AssistantMessageDraft.card(planCard(result.recipeIds()));
            case SELECT_SERVICE -> AssistantMessageDraft.card(serviceSelectCard(result.suggestedServices()));
            case SHOW_CANDIDATES -> AssistantMessageDraft.card(candidatesCard(result.candidates()));
        };
    }

    /** execution_mode 카드: 실행 진입점 ([자동 실행]/[직접 입력]). 실제 실행은 다음 조각 */
    private String executionModeCard(Long recipeId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cardType", "execution_mode");
        meta.put("recipeId", recipeId);
        meta.put("buttons", List.of("auto", "manual"));
        return RecipeJsonUtil.toJsonString(meta);
    }

    /** plan 카드: 순차 실행 레시피 목록 (플랜 UI 진입) */
    private String planCard(List<Long> recipeIds) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("cardType", "plan");
        meta.put("recipeIds", recipeIds == null ? List.of() : recipeIds);
        return RecipeJsonUtil.toJsonString(meta);
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
