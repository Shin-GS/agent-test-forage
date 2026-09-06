package com.testforge.dto.recipe;

import java.time.LocalDateTime;

/**
 * 버전 목록의 한 행 (versioning.md 버전 목록). 스냅샷 본문은 제외하고 식별 메타만 내린다(경량).
 * FE는 이 목록으로 버전 기록 drawer를 채우고, 항목 클릭 시 특정 버전 상세를 별도 조회한다.
 */
public record RecipeVersionSummary(
        // 버전 번호
        int versionNo,
        // 버전 생성 시각 (= 그 수정이 발생한 시각)
        LocalDateTime createdAt) {
}
