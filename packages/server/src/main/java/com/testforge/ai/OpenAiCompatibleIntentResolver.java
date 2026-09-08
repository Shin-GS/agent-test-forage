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
            하나를 호출한다.

            [tool 선택 결정 트리] — 위에서부터 순서대로 판단한다:
            1. 대상 서비스가 지정되지 않았고(레시피 목록이 비어 있음) 작업 요청이면 → select_service.
               발화에서 유추되는 서비스가 있으면 apiSpecIds에 담고, 없으면 빈 배열로 둔다.
            2. 요청과 정확히 일치하는 레시피가 1개면 → execute_recipe.
               1개로 확실하면 되묻지 말고 바로 execute_recipe를 호출한다.
            3. 요청에 부합하는 레시피가 2개 이상이라 사용자가 골라야 하면 → show_candidates.
               반드시 실제 목록에 있는 id로만 후보를 담는다. 후보가 없으면 show_candidates를 부르지 말 것.
            4. 결과가 앞→뒤로 흐르는(결과 의존) 레시피를 2개 이상 순서대로 실행해야 하면 → propose_plan.
               단순히 여러 후보 중 택1(show_candidates)과 혼동하지 말 것.
            5. 요청이 모호하거나 정보가 부족하면 → clarify로 되묻는다.
               show_candidates를 빈/억지 후보로 부르지 말고, 이 경우 clarify를 사용한다.
            6. 제공된 목록에 매칭되는 레시피가 정말 없으면 → no_match. 없는 기능을 지어내지 말 것.
            7. 레시피 실행과 무관한 인사/잡담/일반 질문이면 → chat.

            [경계 규칙]
            - clarify는 남발하지 않는다. 1개로 확실하면 바로 execute_recipe, 정말 모호할 때만 clarify.
            - 레시피/서비스는 반드시 목록에 있는 id로 지목한다(목록 밖 id 금지).
            - 대상 서비스 미지정 상태에서는 select_service 또는 chat만 사용한다.
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
        // 참조 태그([▶] 실행 등) 단락: referenceId가 현재 레시피 목록의 유효 id면 AI 호출 없이 바로 실행.
        // RuleBasedIntentResolver.matchByReference/parseRecipeId와 동일 규칙.
        Long referencedRecipeId = matchReferencedRecipeId(context);
        if (referencedRecipeId != null) {
            log.debug("Short-circuit execute_recipe by referenceId={} (recipeId={}), skipping AI call",
                    context.referenceId(), referencedRecipeId);
            return IntentResult.executeRecipe(referencedRecipeId, Map.of());
        }

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
                messages.add(new OpenAiDtos.ChatMessage(role, turn.content(), null, null));
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
                // 레시피가 1개면 플랜이 아니라 단일 실행이다 → execute_recipe로 폴백(카드/실행 일관).
                if (ids.size() == 1) {
                    yield IntentResult.executeRecipe(ids.get(0), Map.of());
                }
                yield IntentResult.proposePlan(ids, asString(args.get("rationale"), null));
            }
            case SELECT_SERVICE -> {
                List<ServiceOption> suggested = mapServices(context, asLongList(args.get("apiSpecIds")));
                yield IntentResult.selectService(suggested);
            }
            case SHOW_CANDIDATES -> {
                List<RecipeCandidate> candidates = mapRecipes(context, asLongList(args.get("recipeIds")));
                // 후보가 비면(빈/무효 recipeIds) 단정(no_match)하지 않고 되물어 확인한다.
                // AI가 억지로 show_candidates를 부른 경우일 수 있어, clarify로 사용자 의도를 재확인한다.
                if (candidates.isEmpty()) {
                    log.warn("show_candidates with empty/invalid recipeIds, falling back to clarify");
                    yield IntentResult.clarify("어떤 작업을 하시려는지 조금 더 구체적으로 알려주시겠어요?");
                }
                yield IntentResult.showCandidates(candidates);
            }
            case CLARIFY -> IntentResult.clarify(asString(args.get("message"), "무엇을 도와드릴까요?"));
            case NO_MATCH -> IntentResult.noMatch();
            case CHAT -> IntentResult.chat(asString(args.get("message"), "네, 말씀하세요."));
            case INVESTIGATE -> {
                // 정보 조회 루프 진입 지정(첫 조회 source/query). 실제 반복/조회는 InvestigateLoop가 수행한다.
                // source가 비면 1단계 기본 소스(api_spec)로 둔다(스키마 required이나 방어).
                String source = asString(args.get("source"), "api_spec");
                String query = asString(args.get("query"), context.utterance());
                yield IntentResult.investigate(source, query);
            }
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

    // ── 참조 태그 단락: referenceId를 실제 레시피 목록과 대조 ──

    /**
     * referenceId("recipe_123" 또는 "123")를 파싱해 현재 recipes 목록에 존재하면 그 id를 반환한다.
     * referenceId가 없거나 형식이 아니거나 목록에 없으면 null(→ 기존 AI 호출 경로).
     */
    private Long matchReferencedRecipeId(IntentContext context) {
        String referenceId = context.referenceId();
        if (referenceId == null || referenceId.isBlank()) {
            return null;
        }
        Long refRecipeId = parseRecipeId(referenceId);
        if (refRecipeId == null || !recipeExists(context, refRecipeId)) {
            return null;
        }
        return refRecipeId;
    }

    /** "recipe_123" / "123" → 123L. 숫자가 없으면 null */
    private Long parseRecipeId(String referenceId) {
        String digits = referenceId.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
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
