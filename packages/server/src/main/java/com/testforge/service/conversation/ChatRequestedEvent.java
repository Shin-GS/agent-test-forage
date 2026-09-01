package com.testforge.service.conversation;

/**
 * 사용자 메시지가 접수되어 AI 처리가 필요함을 알리는 도메인 이벤트.
 * ConversationService(메시지 저장 트랜잭션)가 발행하고, {@link ChatRequestedListener}가
 * <b>커밋 이후</b> 비동기로 수신하여 ChatProcessor를 구동한다.
 *
 * <p>이벤트로 디커플링하는 이유:
 * <ul>
 *   <li>ConversationService ↔ ChatProcessor 생성자 순환 의존 제거</li>
 *   <li>{@code @TransactionalEventListener(AFTER_COMMIT)}로 "저장 커밋 후 처리 시작"을 보장
 *       (커밋 전에 AI가 발화를 못 읽는 경합 방지)</li>
 * </ul>
 *
 * @param conversationId 처리 대상 대화방
 * @param userId         발화 소유자 (SSE 대상)
 */
public record ChatRequestedEvent(Long conversationId, Long userId) {
}
