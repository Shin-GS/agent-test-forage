package com.testforge.ai;

import java.util.List;

/**
 * AI 컨텍스트/결과에서 다루는 레시피의 최소 표현.
 *
 * <ul>
 *   <li>컨텍스트: 서비스 지정 시 AI에게 전달하는 "사용 가능한 레시피" 목록의 한 항목
 *       (id + name + description + tags + variables — ai-config.md 토큰 절약: 원시 정의는 제외,
 *       발화값 추출용 변수 스키마는 최소 필드만)</li>
 *   <li>결과: {@code show_candidates}가 반환하는 후보 항목 (id + name + description)</li>
 * </ul>
 */
public record RecipeCandidate(
        // 레시피 ID
        Long id,
        // 레시피 이름
        String name,
        // 한 줄 설명 (없으면 null)
        String description,
        // 태그 (없으면 빈 리스트)
        List<String> tags,
        // 사용자 입력 변수 요약 (발화값 추출용, 없으면 빈 리스트) — ai-config.md "레시피 변수 스키마 전달"
        List<VariableSummary> variables) {

    /**
     * 발화값 추출용 변수 스키마의 최소 표현 (ai-config.md 토큰 절약: 추출에 필요한 필드만).
     *
     * @param key         userInput 매칭 키 (variablesJson의 key 우선, 없으면 name)
     * @param label       사용자 표시용 라벨 (없으면 null)
     * @param type        변수 타입 (예: number/integer/string/boolean, 없으면 null)
     * @param required    필수 여부 (없으면 false)
     * @param description 변수 설명 (없으면 null)
     */
    public record VariableSummary(
            String key,
            String label,
            String type,
            boolean required,
            String description) {
    }

    /** 후보 표현용 간이 생성 (태그/변수 불필요 시) */
    public static RecipeCandidate of(Long id, String name, String description) {
        return new RecipeCandidate(id, name, description, List.of(), List.of());
    }

    /** 태그만 있고 변수 스키마가 없을 때의 호환 생성 (변수는 빈 리스트) */
    public static RecipeCandidate of(Long id, String name, String description, List<String> tags) {
        return new RecipeCandidate(id, name, description,
                tags == null ? List.of() : tags, List.of());
    }
}
