package com.testforge.dto.conversation;

/**
 * 메시지 전송 접수 응답. 사용자 메시지 저장 결과를 담는다.
 * AI 처리/SSE 발행은 다음 조각에서 추가되며, 현재는 저장된 사용자 메시지만 반환한다.
 */
public record MessageSendResponse(
        // 접수 여부 (항상 true — 저장 성공 시 반환)
        boolean accepted,
        // 대화방(세션) ID
        Long sessionId,
        // 저장된 사용자 메시지
        MessageResponse message) {
}
