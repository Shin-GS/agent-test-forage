// 스토어 도메인 보조 타입.

/** 대화방 처리 상태 (FE 로컬 상태 머신) */
export type ConversationRuntimeStatus =
  | "idle"
  | "ai_responding"
  | "executing"
  | "input_waiting";

/**
 * 실행 진행 상태 (SSE execution_progress 로 갱신).
 * BE 계약(ExecutionProgressPayload): { sessionId, executionId, stepIndex, status, summary }
 * - status 는 문자열("STARTED" | 스텝 상태 코드)
 * - stepIndex 는 방금 보고된 스텝 인덱스(0-based, 시작 알림이면 null)
 */
export interface ExecutionProgress {
  sessionId: number;
  executionId: number;
  /** 방금 진행된 스텝 인덱스 (0-based). 시작 알림(STARTED)이면 null */
  stepIndex: number | null;
  status: string;
  summary: string | null;
}
