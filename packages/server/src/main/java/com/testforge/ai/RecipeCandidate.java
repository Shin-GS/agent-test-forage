package com.testforge.ai;

import java.util.List;

/**
 * AI 컨텍스트/결과에서 다루는 레시피의 최소 표현.
 *
 * <ul>
 *   <li>컨텍스트: 서비스 지정 시 AI에게 전달하는 "사용 가능한 레시피" 목록의 한 항목
 *       (id + name + description + tags — ai-config.md 토큰 절약: 원시 정의는 제외)</li>
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
        List<String> tags) {

    /** 후보 표현용 간이 생성 (태그 불필요 시) */
    public static RecipeCandidate of(Long id, String name, String description) {
        return new RecipeCandidate(id, name, description, List.of());
    }
}
