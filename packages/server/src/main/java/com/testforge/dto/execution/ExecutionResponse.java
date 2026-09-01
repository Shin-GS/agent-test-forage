package com.testforge.dto.execution;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 실행 상세 응답 (EXECUTION + 하위 레시피/스텝). 실행 시작 응답과 히스토리 상세에서 공용.
 * FE는 이 응답으로 진행 상태 블록을 렌더링하고, 각 레시피의 스냅샷/스텝으로 실제 실행을 수행한다.
 */
public record ExecutionResponse(
        Long id,
        Long userId,
        Long conversationId,
        Long apiSpecId,
        StatusView type,
        String title,
        StatusView mode,
        StatusView status,
        Object context,
        String resultSummary,
        List<ExecutionRecipeView> recipes,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs) {
}
