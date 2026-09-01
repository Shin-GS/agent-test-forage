package com.testforge.dto.execution;

import com.testforge.entity.execution.enums.ExecutionStatus;

/**
 * 실행 종료 보고. FE가 모든 스텝 실행을 마치거나(성공/부분/실패) 중지했을 때 서버에 종료를 알린다.
 * 서버는 EXECUTION 상태를 확정하고 {@code execution_complete} + 대화방 idle 전이 + 락 해제를 수행한다.
 *
 * <p>스텝별 상세 결과 보고(스텝 단위 저장)는 다음 조각에서 추가한다. 이번 조각은 실행 전체의
 * 시작/종료 뼈대만 다루므로, 종료 시 최종 상태와 요약만 받는다.
 *
 * @param status        최종 상태 (SUCCESS/PARTIAL/FAILED/STOPPED — RUNNING 불가)
 * @param resultSummary 결과 요약 (히스토리 표시용, 선택)
 */
public record ExecutionCompleteRequest(
        ExecutionStatus status,
        String resultSummary) {
}
