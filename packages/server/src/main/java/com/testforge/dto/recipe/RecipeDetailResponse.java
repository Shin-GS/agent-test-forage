package com.testforge.dto.recipe;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 레시피 상세. (레시피 편집/미리보기 화면 소비용)
 * 저장 시 문자열로 보관된 스텝/변수/결과 JSON을 다시 구조화된 객체(Map/List)로 파싱하여 내린다.
 */
public record RecipeDetailResponse(
        // 레시피 ID
        Long id,
        // 작성자 ID
        Long ownerUserId,
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
        // 현재 버전 번호
        int currentVersion,
        // 유효성 검증 상태 (code + description)
        StatusView validationStatus,
        // 검증 실패 상세 메시지 (VALID면 null)
        String validationMessage,
        // 사용 횟수
        int usageCount,
        // 마지막 사용 시각
        LocalDateTime lastUsedAt,
        // 생성 시각
        LocalDateTime createdAt,
        // 갱신 시각
        LocalDateTime updatedAt) {
}
