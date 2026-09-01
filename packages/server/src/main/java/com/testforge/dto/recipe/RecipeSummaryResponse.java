package com.testforge.dto.recipe;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 레시피 목록의 한 행. (레시피 목록/사이드 패널 소비용)
 * 스텝 상세는 제외하고 목록 표시/필터에 필요한 메타만 내린다.
 */
public record RecipeSummaryResponse(
        // 레시피 ID
        Long id,
        // 레시피명
        String name,
        // 설명
        String description,
        // 대상 서비스(스펙) ID
        Long apiSpecId,
        // 공개 범위 (code + description)
        StatusView visibility,
        // 태그 배열
        List<String> tags,
        // 유효성 검증 상태 (code + description)
        StatusView validationStatus,
        // 현재 버전 번호
        int currentVersion,
        // 사용 횟수
        int usageCount,
        // 마지막 사용 시각
        LocalDateTime lastUsedAt) {
}
