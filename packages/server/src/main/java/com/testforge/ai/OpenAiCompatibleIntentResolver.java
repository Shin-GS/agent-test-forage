package com.testforge.ai;

import com.testforge.ai.config.AiSettings;
import com.testforge.ai.enums.ToolName;
import com.testforge.ai.openai.OpenAiClient;
import com.testforge.ai.openai.OpenAiDtos;
import com.testforge.ai.openai.ToolSchemas;
import com.testforge.utils.RecipeJsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * IntentResolver의 실제 AI 구현 (OpenAI 호환 API, OpenRouter 등). 발화 + 대화방 컨텍스트로 프롬프트를
 * 구성해 tool calling을 수행하고, AI가 고른 tool(+인자)을 {@link IntentResult}로 변환한다.
 *
 * <p>빈 등록은 {@code AiResolverConfig}가 관리하며, {@code ai-test-forge.ai.api-key}가 설정된 경우에만
 * 이 구현이 {@code IntentResolver} 빈이 된다. 키가 없으면 {@link RuleBasedIntentResolver}(목)가 쓰인다.
 *
 * <p>AI는 프롬프트로 받은 레시피/서비스 목록에서 <b>id</b>로 대상을 지목한다. 여기서는 그 id를
 * context의 목록과 대조해 IntentResult로 매핑한다(없는 id는 방어적으로 무시/재질문 처리).
 * 호출/파싱 실패 시 예외를 던지며, 상위 ChatProcessor가 이를 잡아 대화방을 종결한다(종결 보장).
 */
public class OpenAiCompatibleIntentResolver implements IntentResolver {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleIntentResolver.class);

    private static final String SYSTEM_PROMPT = """
            너는 API 워크플로우 실행 플랫폼의 어시스턴트다. 사용자 발화를 분석해 제공된 tool 중 정확히
            하나를 호출한다. 반드시 아래 규칙을 지켜라:
            - 제공된 레시피/서비스 목록에 없는 작업은 지어내지 말 것. 매칭되는 레시피가 없으면 no_match.
            - 확실하지 않으면 추측하지 말고 clarify로 되물을 것.
            - 레시피/서비스는 반드시 목록에 있는 id로 지목할 것.
            - 대상 서비스가 지정되지 않은 상태(레시피 목록이 비어 있음)면 select_service 또는 chat만 사용.
            - 레시피 실행과 무관한 인사/잡담/일반 질문은 chat.
            사용자에게 보이는 message(clarify/chat)는 한국어로 작성한다.
            """;

    private final OpenAiClient client;
    private final AiSettings settings;

    public OpenAiCompatibleIntentResolver(OpenAiClient client, AiSettings settings) {
        this.client = client;
        this.settings = settings;
        log.info("AI intent resolver enabled (baseUrl={}, model={})",
                settings.baseUrl(), settings.reasoningModel());
    }

    @Override
    public IntentResult resolve(IntentContext context) {
        List<OpenAiDtos.ChatMessage> messages = buildMessages(context);
        OpenAiDtos.ChatResponse response = client.chatWithTools(
                settings.reasoningModel(), messages, ToolSchemas.all());

        OpenAiDtos.ToolCall toolCall = firstToolCall(response);
        if (toolCall == null) {
            // tool을 안 골랐으면(모델이 텍스트만 반환) 안전하게 재질문으로 유도
            log.warn("AI returned no tool_call; falling back to clarify");
            return IntentResult.clarify("요청을 조금 더 구체적으로 말씀해 주시겠어요?");
        }
        return toIntentResult(context, toolCall);
    }

    // ── 프롬프트 구성 ──

    private List<OpenAiDtos.ChatMessage> buildMessages(IntentContext context) {
        List<OpenAiDtos.ChatMessage> messages = new ArrayList<>();
        messages.add(OpenAiDtos.ChatMessage.system(SYSTEM_PROMPT));

        // 컨텍스트: 서비스 지정 여부에 따라 레시피 목록 또는 서비스 목록 (ai-config.md 분기)
        if (context.hasService()) {
            messages.add(OpenAiDtos.ChatMessage.system(
                    "현재 서비스(apiSpecId=" + context.apiSpecId() + ")의 사용 가능한 레시피 목록:\n"
                            + renderRecipes(context.recipes())));
        } else {
            messages.add(OpenAiDtos.ChatMessage.system(
                    "대상 서비스가 지정되지 않았다. 사용 가능한 서비스 목록:\n"
                            + renderServices(context.services())));
        }

        // 최근 대화 이력 (오래된 → 최신)
        if (context.history() != null) {
            for (IntentContext.HistoryTurn turn : context.history()) {
                String role = "user".equals(turn.role()) ? "user" : "assistant";
                messages.add(new OpenAiDtos.ChatMessage(role, turn.content()));
            }
        }

        // 현재 발화
        messages.add(OpenAiDtos.ChatMessage.user(context.utterance() == null ? "" : context.utterance()));
        return messages;
    }

    private String renderRecipes(List<RecipeCandidate> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            return "(레시피 없음)";
        }
        StringBuilder sb = new StringBuilder();
        for (RecipeCandidate r : recipes) {
            sb.append("- id=").append(r.id()).append(", name=").append(r.name());
            if (r.description() != null && !r.description().isBlank()) {
                sb.append(", desc=").append(r.description());
            }
            if (r.tags() != null && !r.tags().isEmpty()) {
                sb.append(", tags=").append(String.join("/", r.tags()));
            }
            appendVariables(sb, r.variables());
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 레시피 줄에 발화값 추출용 입력 변수 스키마를 덧붙인다 (ai-config.md "레시피 변수 스키마 전달").
     * 예: {@code , 입력변수=[productId(상품 ID,number,필수), quantity(수량,number,선택)]}.
     * 변수가 없으면 생략한다(토큰 절약). 각 변수는 key(label,type,필수/선택)로 최소 표기하고,
     * 설명이 있으면 콜론 뒤에 덧붙인다.
     */
    private void appendVariables(StringBuilder sb, List<RecipeCandidate.VariableSummary> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        sb.append(", 입력변수=[");
        for (int i = 0; i < variables.size(); i++) {
            RecipeCandidate.VariableSummary v = variables.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(v.key());
            List<String> attrs = new java.util.ArrayList<>();
            if (v.label() != null) {
                attrs.add(v.label());
            }
            if (v.type() != null) {
                attrs.add(v.type());
            }
            attrs.add(v.required() ? "필수" : "선택");
            sb.append("(").append(String.join(",", attrs)).append(")");
            if (v.description() != null) {
                sb.append(": ").append(v.description());
            }
        }
        sb.append("]");
    }

    private String renderServices(List<ServiceOption> services) {
        if (services == null || services.isEmpty()) {
            return "(서비스 없음)";
        }
        StringBuilder sb = new StringBuilder();
        for (ServiceOption s : services) {
            sb.append("- id=").append(s.apiSpecId()).append(", name=").append(s.name());
            if (s.description() != null && !s.description().isBlank()) {
                sb.append(", desc=").append(s.description());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ── 응답 파싱 ──

    private OpenAiDtos.ToolCall firstToolCall(OpenAiDtos.ChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return null;
        }
        OpenAiDtos.ResponseMessage message = response.choices().get(0).message();
        if (message == null || message.tool_calls() == null || message.tool_calls().isEmpty()) {
            return null;
        }
        return message.tool_calls().get(0);
    }

    /** AI가 고른 tool + 인자를 IntentResult로 변환 */
    private IntentResult toIntentResult(IntentContext context, OpenAiDtos.ToolCall toolCall) {
        String name = toolCall.function() == null ? null : toolCall.function().name();
        Map<String, Object> args = parseArgs(toolCall.function() == null ? null : toolCall.function().arguments());
        ToolName tool = resolveToolName(name);

        return switch (tool) {
            case EXECUTE_RECIPE -> {
                Long recipeId = asLong(args.get("recipeId"));
                // AI가 목록 밖 id를 주면 방어적으로 재질문
                if (recipeId == null || !recipeExists(context, recipeId)) {
                    log.warn("execute_recipe with invalid recipeId={}, falling back to clarify", recipeId);
                    yield IntentResult.clarify("어떤 레시피를 실행할지 다시 알려주시겠어요?");
                }
                yield IntentResult.executeRecipe(recipeId, asMap(args.get("extractedValues")));
            }
            case PROPOSE_PLAN -> {
                List<Long> ids = asLongList(args.get("recipeIds")).stream()
                        .filter(id -> recipeExists(context, id))
                        .toList();
                if (ids.isEmpty()) {
                    yield IntentResult.clarify("어떤 작업들을 순서대로 진행할지 알려주시겠어요?");
                }
                yield IntentResult.proposePlan(ids);
            }
            case SELECT_SERVICE -> {
                List<ServiceOption> suggested = mapServices(context, asLongList(args.get("apiSpecIds")));
                yield IntentResult.selectService(suggested);
            }
            case SHOW_CANDIDATES -> {
                List<RecipeCandidate> candidates = mapRecipes(context, asLongList(args.get("recipeIds")));
                if (candidates.isEmpty()) {
                    yield IntentResult.noMatch();
                }
                yield IntentResult.showCandidates(candidates);
            }
            case CLARIFY -> IntentResult.clarify(asString(args.get("message"), "무엇을 도와드릴까요?"));
            case NO_MATCH -> IntentResult.noMatch();
            case CHAT -> IntentResult.chat(asString(args.get("message"), "네, 말씀하세요."));
        };
    }

    /** wire 이름(execute_recipe) → ToolName. 알 수 없으면 no_match로 방어 */
    private ToolName resolveToolName(String wireName) {
        if (wireName != null) {
            for (ToolName t : ToolName.values()) {
                if (t.wireName().equals(wireName.toLowerCase(Locale.ROOT))) {
                    return t;
                }
            }
        }
        log.warn("Unknown tool name from AI: {}", wireName);
        return ToolName.NO_MATCH;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String argumentsJson) {
        Object parsed = RecipeJsonUtil.toObject(argumentsJson);
        if (parsed instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    // ── context 대조: AI가 준 id를 실제 목록과 매칭 ──

    private boolean recipeExists(IntentContext context, Long recipeId) {
        return recipeId != null && context.recipes() != null
                && context.recipes().stream().anyMatch(r -> recipeId.equals(r.id()));
    }

    private List<RecipeCandidate> mapRecipes(IntentContext context, List<Long> ids) {
        List<RecipeCandidate> result = new ArrayList<>();
        if (context.recipes() != null) {
            for (Long id : ids) {
                context.recipes().stream()
                        .filter(r -> id.equals(r.id()))
                        .findFirst()
                        .ifPresent(result::add);
            }
        }
        return result;
    }

    private List<ServiceOption> mapServices(IntentContext context, List<Long> ids) {
        List<ServiceOption> result = new ArrayList<>();
        if (context.services() != null) {
            for (Long id : ids) {
                context.services().stream()
                        .filter(s -> id.equals(s.apiSpecId()))
                        .findFirst()
                        .ifPresent(result::add);
            }
        }
        return result;
    }

    // ── 값 변환 헬퍼 (JSON 숫자는 Integer/Long/Double 등으로 올 수 있음) ──

    private Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<Long> asLongList(Object value) {
        List<Long> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Long id = asLong(item);
                if (id != null) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    private String asString(Object value, String fallback) {
        if (value instanceof String s && !s.isBlank()) {
            return s;
        }
        return fallback;
    }

    /** JSON object 인자를 Map으로. Map이 아니면 빈 맵(추측/오염 방지) */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
