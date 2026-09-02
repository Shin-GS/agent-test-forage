package com.testforge.dto.execution;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;

/**
 * 실행 히스토리 목록 항목(경량). 패널 최근 목록/전체 히스토리 페이지의 한 줄을 그리는 데 필요한
 * 정보만 담는다 — 하위 레시피/스텝 상세는 제외(상세는 {@code GET /executions/{id}}로 별도 조회).
 *
 * <p>UI: 목록에 실행 시각/레시피명/상태 뱃지/결과 요약을 표시한다(history.md 표시 정보).
 * 상태는 코드+설명을 함께 내리는 {@link StatusView}로 표현해 FE가 라벨을 하드코딩하지 않게 한다.
 */
public record ExecutionSummaryView(
        Long id,
        Long conversationId,
        Long apiSpecId,
        StatusView type,
        String title,
        StatusView status,
        String resultSummary,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs) {
}
