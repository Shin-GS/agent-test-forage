package com.testforge.dto.recipe;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 특정 버전 스냅샷 상세 (versioning.md 특정 버전 조회).
 * <b>{@link RecipeDetailResponse} 호환</b> 필드 구조에 버전 식별 정보({@code versionNo}/{@code createdAt})를 얹는다.
 * FE 편집 페이지의 미리보기가 현재 상세 렌더링 컴포넌트를 그대로 재사용할 수 있게 한다.
 *
 * <p>스냅샷은 저장 시점 통짜 JSON이므로 usageCount/lastUsedAt 같은 런타임 카운터는 담기지 않는다
 * (해당 필드는 미리보기에서 의미가 없어 생략). canEdit은 요청자 기준 편집 가능 여부(복원 버튼 게이팅용)다.
 */
public record RecipeVersionDetailResponse(
        // 대상 레시피 ID
        Long recipeId,
        // 이 스냅샷의 버전 번호
        int versionNo,
        // 버전(스냅샷) 생성 시각
        LocalDateTime createdAt,
        // 대상 서비스(스펙) ID
        Long apiSpecId,
        // 레시피명
        String name,
        // 설명
        String description,
        // 공개 범위 (code + description)
        StatusView visibility,
        // 태그 배열
        List<String> tags,
        // 사용자 입력 변수 정의 (JSON)
        Object variables,
        // 스텝 목록 (JSON 배열)
        Object steps,
        // 결과 정의 (JSON)
        Object resultDefinition,
        // 결과 메시지 템플릿
        String resultTemplate,
        // 요청자 기준 편집 가능 여부 (복원 버튼 게이팅용, auth.md 권한 힌트)
        boolean canEdit) {
}
