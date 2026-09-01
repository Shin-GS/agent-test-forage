package com.testforge.dto.conversation;

/**
 * 첫 메시지로 대화방을 생성한 응답. 생성된 대화방 상세와 저장된 첫 메시지를 함께 반환한다.
 * AI 처리/SSE 발행은 이번 스코프가 아니다(다음 조각).
 */
public record ConversationStartResponse(
        // 접수 여부 (항상 true — 저장 성공 시 반환)
        boolean accepted,
        // 생성된 대화방 상세
        ConversationDetailResponse conversation,
        // 저장된 첫 메시지 (seq=1)
        MessageResponse message) {
}
