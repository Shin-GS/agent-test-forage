package com.testforge.dto.execution;

import com.testforge.dto.common.StatusView;
import com.testforge.entity.execution.enums.ExecutionStatus;

/**
 * execution_complete 이벤트의 data 페이로드(messaging.md):
 * {@code { sessionId, executionId, outcome, retriable, failedStepIndex }}.
 *
 * <p>{@code outcome}은 실행 최종 상태({@link ExecutionStatus})를 코드/설명으로 내려, FE가 후속 액션
 * (결과 보기/다시 실행/이어서 실행)을 결정하게 한다. {@code retriable}/{@code failedStepIndex}는
 * 스텝 단위 결과 보고가 있어야 채워지므로 이번 조각에서는 null로 둔다(다음 조각에서 세팅).
 *
 * @param sessionId       대화방 ID
 * @param executionId     실행 ID
 * @param outcome         최종 상태 (code + description)
 * @param retriable       재시도 가능 여부 (FAILED에만 의미, 미정이면 null)
 * @param failedStepIndex 실패/중단 스텝 위치 (FAILED/STOPPED, 미정이면 null)
 */
public record ExecutionCompletePayload(
        Long sessionId,
        Long executionId,
        StatusView outcome,
        Boolean retriable,
        Integer failedStepIndex) {

    public static ExecutionCompletePayload of(Long sessionId, Long executionId, ExecutionStatus status) {
        return new ExecutionCompletePayload(sessionId, executionId, StatusView.of(status), null, null);
    }
}
