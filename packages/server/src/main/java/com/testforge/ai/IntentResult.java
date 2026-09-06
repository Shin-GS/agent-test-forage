package com.testforge.ai;

import com.testforge.ai.enums.ToolName;

import java.util.List;
import java.util.Map;

/**
 * IntentResolver 출력. 선택된 {@link ToolName}과 그 tool의 파라미터를 담는다
 * (intent-classification.md의 tool 정의). 하나의 result는 하나의 tool만 나타내며,
 * 사용되지 않는 파라미터 필드는 null/빈 값이다.
 *
 * <p>생성은 tool별 팩토리 메서드로만 하여 "tool ↔ 필요한 파라미터"를 강제한다.
 * ChatProcessor는 {@link #tool()}로 분기하여 해당 파라미터만 사용한다.
 */
public record IntentResult(
        // 선택된 tool
        ToolName tool,
        // clarify/chat: AI 생성 메시지 (그 외 null)
        String message,
        // execute_recipe: 실행할 레시피 ID (그 외 null)
        Long recipeId,
        // propose_plan: 순차 실행 레시피 ID 배열 (그 외 null)
        List<Long> recipeIds,
        // propose_plan: 플랜 제안 근거 한 줄 (그 외 null). plan 카드에 표시된다
        String rationale,
        // select_service: 추천 서비스 (그 외 빈 리스트)
        List<ServiceOption> suggestedServices,
        // show_candidates: 후보 레시피 (그 외 빈 리스트)
        List<RecipeCandidate> candidates,
        // execute_recipe: 발화에서 추출한 초기 입력값 (그 외 빈 맵)
        Map<String, Object> extractedValues,
        // investigate: 조회 소스 식별자 (예: "api_spec", "jira"). 그 외 null
        String investigateSource,
        // investigate: 조회 키워드/질문. 그 외 null
        String investigateQuery) {

    /** execute_recipe: 단일 레시피 실행 (발화에서 추출한 초기값 시드 포함) */
    public static IntentResult executeRecipe(Long recipeId, Map<String, Object> extractedValues) {
        Map<String, Object> values = extractedValues == null ? Map.of() : Map.copyOf(extractedValues);
        return new IntentResult(ToolName.EXECUTE_RECIPE, null, recipeId, null, null, List.of(), List.of(), values, null, null);
    }

    /** propose_plan: 여러 레시피 순차 실행 제안 ({@code rationale}은 제안 근거, null 허용) */
    public static IntentResult proposePlan(List<Long> recipeIds, String rationale) {
        return new IntentResult(ToolName.PROPOSE_PLAN, null, null, List.copyOf(recipeIds), rationale, List.of(), List.of(), Map.of(), null, null);
    }

    /** select_service: 서비스 선택 요청 (유추 불가 시 빈 리스트 전달) */
    public static IntentResult selectService(List<ServiceOption> suggestedServices) {
        return new IntentResult(ToolName.SELECT_SERVICE, null, null, null, null, List.copyOf(suggestedServices), List.of(), Map.of(), null, null);
    }

    /** show_candidates: 후보 레시피 목록 제시 */
    public static IntentResult showCandidates(List<RecipeCandidate> candidates) {
        return new IntentResult(ToolName.SHOW_CANDIDATES, null, null, null, null, List.of(), List.copyOf(candidates), Map.of(), null, null);
    }

    /** clarify: 모호하여 재질문 (AI 생성 메시지) */
    public static IntentResult clarify(String message) {
        return new IntentResult(ToolName.CLARIFY, message, null, null, null, List.of(), List.of(), Map.of(), null, null);
    }

    /** no_match: 매칭 레시피 없음 (message는 FE 고정 문구이므로 없음) */
    public static IntentResult noMatch() {
        return new IntentResult(ToolName.NO_MATCH, null, null, null, null, List.of(), List.of(), Map.of(), null, null);
    }

    /** chat: 일반 대화 응답 (AI 생성 메시지) */
    public static IntentResult chat(String message) {
        return new IntentResult(ToolName.CHAT, message, null, null, null, List.of(), List.of(), Map.of(), null, null);
    }

    /**
     * investigate: 정보 조회 루프 진입 (source/query로 첫 조회 지정). ChatProcessor가 이 결과를 만나면
     * 단발 경로 대신 전용 루프({@code InvestigateLoop})로 위임한다(investigation.md).
     */
    public static IntentResult investigate(String source, String query) {
        return new IntentResult(ToolName.INVESTIGATE, null, null, null, null, List.of(), List.of(), Map.of(), source, query);
    }
}
