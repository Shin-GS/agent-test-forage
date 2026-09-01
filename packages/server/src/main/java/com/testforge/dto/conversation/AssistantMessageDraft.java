package com.testforge.dto.conversation;

import com.testforge.entity.conversation.enums.MessageType;

/**
 * AI 처리 결과를 대화방에 확정 메시지로 남기기 위한 초안(draft).
 * ChatProcessor(오케스트레이션)가 tool 결과를 이 형태로 만들어 ConversationService로 넘기면,
 * ConversationService가 seq 발번 + 저장 + SSE(message_new) 발행 + 상태 종결(idle)을 처리한다.
 *
 * <ul>
 *   <li>chat/clarify: {@code type=TEXT}, {@code content=AI 메시지}, {@code metadataJson=null}</li>
 *   <li>no_match: {@code type=SYSTEM}, {@code content=고정 안내}, {@code metadataJson={level:"info"}}</li>
 *   <li>카드류(execute_recipe/propose_plan/select_service/show_candidates):
 *       {@code type=CARD}, {@code content=null 또는 짧은 안내}, {@code metadataJson={cardType,...}}</li>
 * </ul>
 *
 * @param type        메시지 표현 타입
 * @param content     본문 (카드류는 null 가능)
 * @param metadataJson 타입별 상세 JSON 문자열 (없으면 null)
 */
public record AssistantMessageDraft(
        MessageType type,
        String content,
        String metadataJson) {

    /** chat/clarify: 텍스트 메시지 */
    public static AssistantMessageDraft text(String content) {
        return new AssistantMessageDraft(MessageType.TEXT, content, null);
    }

    /** no_match 등: 시스템 안내 메시지 */
    public static AssistantMessageDraft system(String content, String metadataJson) {
        return new AssistantMessageDraft(MessageType.SYSTEM, content, metadataJson);
    }

    /** 카드류: content 없이 metadata로 렌더 */
    public static AssistantMessageDraft card(String metadataJson) {
        return new AssistantMessageDraft(MessageType.CARD, null, metadataJson);
    }
}
