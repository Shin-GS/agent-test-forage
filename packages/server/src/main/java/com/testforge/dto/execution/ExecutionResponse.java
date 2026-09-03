package com.testforge.dto.execution;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 실행 상세 응답 (EXECUTION + 하위 레시피/스텝). 실행 시작 응답과 히스토리 상세에서 공용.
 * FE는 이 응답으로 진행 상태 블록을 렌더링하고, 각 레시피의 스냅샷/스텝으로 실제 실행을 수행한다.
 *
 * <p>{@code pendingInputs}는 액션 피커 pre-run 수집용이다. 실행 시작 시 미충족된(또는 직접 입력
 * 모드에서 노출 대상인) 사용자 입력 변수 스키마 목록으로, 값이 없으면 빈 리스트다. 각 항목은
 * 레시피 {@code variablesJson}의 변수 정의 객체 그대로(key/label/type/required/default/... 자유 필드)를 담는다.
 * 이 목록이 비어있지 않으면 대화방은 {@code WAITING_INPUT} 상태이며 FE가 액션 피커를 렌더링한다.
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
        List<Map<String, Object>> pendingInputs,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs) {
}
