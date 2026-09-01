package com.testforge.dto.recipe;

import com.testforge.entity.recipe.enums.Visibility;

/**
 * 레시피 수정 요청. (레시피 편집 화면 소비용)
 * 수정 저장 시 이전 상태가 RecipeVersion에 스냅샷되고 CURRENT_VERSION이 증가한다.
 */
public record RecipeUpdateRequest(
        // 레시피명 (AI 매칭용)
        String name,
        // 설명 (AI 매칭용)
        String description,
        // 공개 범위 (COMMON/PRIVATE). null이면 PRIVATE
        Visibility visibility,
        // 태그 배열 (JSON 배열)
        Object tags,
        // 사용자 입력 변수 정의 (JSON)
        Object variables,
        // 스텝 목록 (JSON 배열) — 검증 대상
        Object steps,
        // 결과 정의 (JSON)
        Object resultDefinition,
        // 결과 메시지 템플릿 (없으면 AI 요약)
        String resultTemplate) {
}
