package com.testforge.ai.openai;

import com.testforge.ai.enums.ToolName;

import java.util.List;
import java.util.Map;

/**
 * 7종 tool({@link ToolName})의 OpenAI function 스키마 정의 (intent-classification.md).
 * AI는 프롬프트로 받은 레시피/서비스 목록에서 <b>id</b>로 대상을 지목한다(이름 대신 id를 쓰면 파싱이
 * 정확하고 동명 레시피 혼동이 없다). Resolver가 이 id로 IntentResult를 만든다.
 *
 * <p>wire 이름은 소문자 snake_case({@link ToolName#wireName()})를 쓴다.
 */
public final class ToolSchemas {

    private ToolSchemas() {
    }

    /** 전체 tool 스키마 목록 (요청의 tools 필드에 실림) */
    public static List<OpenAiDtos.Tool> all() {
        return List.of(
                executeRecipe(),
                proposePlan(),
                selectService(),
                showCandidates(),
                clarify(),
                noMatch(),
                chat());
    }

    private static OpenAiDtos.Tool fn(ToolName tool, String description, Map<String, Object> parameters) {
        return OpenAiDtos.Tool.function(
                new OpenAiDtos.FunctionDef(tool.wireName(), description, parameters));
    }

    /** object 타입 파라미터 스키마 헬퍼 */
    private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", required,
                "additionalProperties", false);
    }

    private static Map<String, Object> integerProp(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private static Map<String, Object> integerArrayProp(String description) {
        return Map.of(
                "type", "array",
                "description", description,
                "items", Map.of("type", "integer"));
    }

    private static Map<String, Object> stringProp(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> objectProp(String description) {
        return Map.of("type", "object", "description", description);
    }

    private static OpenAiDtos.Tool executeRecipe() {
        return fn(ToolName.EXECUTE_RECIPE,
                "사용자 요청과 정확히 일치하는 레시피 1개를 실행한다. 후보가 여러 개면 show_candidates를 쓴다.",
                object(Map.of(
                        "recipeId", integerProp("실행할 레시피 ID"),
                        "extractedValues", objectProp(
                                "발화에서 명시적으로 언급된 값(키-값). 추측 금지, 언급된 것만 담는다.")),
                        List.of("recipeId")));
    }

    private static OpenAiDtos.Tool proposePlan() {
        return fn(ToolName.PROPOSE_PLAN,
                "여러 레시피를 순서대로 실행하는 복합 작업(플랜)을 제안한다.",
                object(Map.of("recipeIds", integerArrayProp("순차 실행할 레시피 ID 배열(실행 순서대로)")),
                        List.of("recipeIds")));
    }

    private static OpenAiDtos.Tool selectService() {
        return fn(ToolName.SELECT_SERVICE,
                "대상 서비스가 지정되지 않아 사용자가 서비스를 먼저 선택해야 할 때 사용한다. "
                        + "발화에서 유추되는 서비스가 있으면 apiSpecIds에 추천으로 담고, 없으면 빈 배열로 둔다.",
                object(Map.of("apiSpecIds", integerArrayProp("추천 서비스(스펙) ID 배열. 유추 불가 시 빈 배열")),
                        List.of("apiSpecIds")));
    }

    private static OpenAiDtos.Tool showCandidates() {
        return fn(ToolName.SHOW_CANDIDATES,
                "요청에 부합하는 레시피 후보가 2개 이상이라 사용자가 선택해야 할 때 사용한다.",
                object(Map.of("recipeIds", integerArrayProp("후보 레시피 ID 배열")),
                        List.of("recipeIds")));
    }

    private static OpenAiDtos.Tool clarify() {
        return fn(ToolName.CLARIFY,
                "요청이 모호해 추가 정보가 필요할 때 사용한다. 사용자에게 물을 질문을 message에 담는다.",
                object(Map.of("message", stringProp("사용자에게 되물을 한국어 질문")), List.of("message")));
    }

    private static OpenAiDtos.Tool noMatch() {
        return fn(ToolName.NO_MATCH,
                "요청과 매칭되는 레시피가 없을 때 사용한다. 없는 기능을 지어내지 말 것.",
                object(Map.of(), List.of()));
    }

    private static OpenAiDtos.Tool chat() {
        return fn(ToolName.CHAT,
                "레시피 실행과 무관한 일반 대화/인사/질문에 응답할 때 사용한다.",
                object(Map.of("message", stringProp("사용자에게 보낼 한국어 응답")), List.of("message")));
    }
}
