package com.testforge.dto.recipe;

import com.testforge.entity.recipe.enums.Visibility;

/**
 * 레시피 생성 요청. (레시피 편집 화면 소비용)
 * 스텝/변수/결과정의/태그는 구조가 제각각이라 범용 객체(Map/List)로 받아 서버에서 문자열로 직렬화해 저장한다.
 */
public record RecipeCreateRequest(
        // 작성자 ID (auth 도입 전까지 요청 바디로 전달)
        Long ownerUserId,
        // 대상 서비스(스펙) ID
        Long apiSpecId,
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
