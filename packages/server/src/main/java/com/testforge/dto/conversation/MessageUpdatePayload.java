package com.testforge.dto.conversation;

/**
 * {@code message_update} 이벤트의 data 페이로드(messaging.md):
 * {@code { sessionId, messageId, message: {...} }}.
 *
 * <p>기존 메시지(진행 블록 PROGRESS 등)를 갱신할 때 발행한다. FE는 {@code messageId}로 대상 메시지를
 * 찾아 {@code message}(전체 스냅샷)로 교체한다. payloadJson이 진실이며 content는 표시용 요약이다.
 *
 * @param sessionId 대화방 ID
 * @param messageId 갱신 대상 메시지 ID
 * @param message   갱신된 메시지 전체 스냅샷
 */
public record MessageUpdatePayload(
        Long sessionId,
        Long messageId,
        MessageResponse message) {

    public static MessageUpdatePayload of(Long sessionId, MessageResponse message) {
        return new MessageUpdatePayload(sessionId, message.id(), message);
    }
}
