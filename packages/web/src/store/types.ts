// 스토어 도메인 보조 타입.

/** 대화방 처리 상태 (FE 로컬 상태 머신) */
export type ConversationRuntimeStatus =
  | "idle"
  | "ai_responding"
  | "executing"
  | "input_waiting";

// 실행 진행/결과는 이제 PROGRESS/RESULT 메시지 payload(api/types.ts)로 표현하며,
// FE 스토어에 별도 실행 상태를 두지 않는다(messages 로 흡수). 진행 스텝 타입은
// api/types.ts 의 ProgressPayload/ProgressStepPayload 를 사용한다.
