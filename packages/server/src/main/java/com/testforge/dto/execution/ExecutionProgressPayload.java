package com.testforge.dto.execution;

/**
 * execution_progress 이벤트의 data 페이로드(messaging.md):
 * {@code { sessionId, executionId, stepIndex, status, summary }}.
 *
 * <p>실행 진행 신호(SIGNAL)다. FE는 이 신호를 받고 진행 상태 블록을 갱신하되, 상세는 실행 상세
 * 조회로 그린다. 실행 시작 시에는 stepIndex=null(전체 시작 알림)로 발행할 수 있다.
 *
 * @param sessionId   대화방 ID
 * @param executionId 실행 ID
 * @param stepIndex   진행된 스텝 위치 (시작 알림이면 null)
 * @param status      진행 상태 코드 (예: "STARTED", 스텝 상태 등)
 * @param summary     한 줄 요약 (선택)
 */
public record ExecutionProgressPayload(
        Long sessionId,
        Long executionId,
        Integer stepIndex,
        String status,
        String summary) {

    /** 실행 시작 알림 (스텝 없이 전체 시작) */
    public static ExecutionProgressPayload started(Long sessionId, Long executionId) {
        return new ExecutionProgressPayload(sessionId, executionId, null, "STARTED", null);
    }
}
