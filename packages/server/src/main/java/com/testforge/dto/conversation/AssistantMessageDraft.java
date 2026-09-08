package com.testforge.dto.conversation;

import com.testforge.entity.conversation.enums.MessageRole;

import java.util.List;

/**
 * AI/시스템 처리 결과를 대화방에 확정 턴으로 남기기 위한 초안(draft). 한 턴은 순서 있는 파트 배열
 * ({@link PartDraft})로 구성된다. ChatProcessor/InvestigateLoop가 tool 결과를 이 형태로 만들어
 * ConversationService로 넘기면, ConversationService가 턴(MESSAGE) + 파트(MESSAGE_PART) 저장 +
 * SSE(message_new) 발행 + 상태 종결(idle)을 처리한다.
 *
 * <ul>
 *   <li>chat/clarify: role=ASSISTANT, parts=[TEXT]</li>
 *   <li>investigate 답변: role=ASSISTANT, parts=[TEXT] 또는 [TEXT, REFERENCES]</li>
 *   <li>no_match: role=SYSTEM, parts=[TEXT]</li>
 *   <li>카드류(execute_recipe/propose_plan/select_service/show_candidates): role=ASSISTANT, parts=[CARD]</li>
 * </ul>
 *
 * @param role  턴 작성 주체
 * @param parts 순서 있는 파트 초안 배열
 */
public record AssistantMessageDraft(
        MessageRole role,
        List<PartDraft> parts) {

    /** chat/clarify: ASSISTANT 텍스트 턴 (TEXT 파트 1개) */
    public static AssistantMessageDraft text(String content) {
        return new AssistantMessageDraft(MessageRole.ASSISTANT, List.of(PartDraft.text(content)));
    }

    /**
     * investigate 최종 답변: ASSISTANT 턴 = TEXT 파트 (+ 출처 있으면 REFERENCES 파트).
     * 조회한 출처가 없으면 {@code referencesPayloadJson}을 null로 두어 순수 TEXT로 발행한다.
     */
    public static AssistantMessageDraft textWithReferences(String content, String referencesPayloadJson) {
        if (referencesPayloadJson == null || referencesPayloadJson.isBlank()) {
            return text(content);
        }
        return new AssistantMessageDraft(MessageRole.ASSISTANT,
                List.of(PartDraft.text(content), PartDraft.references(referencesPayloadJson)));
    }

    /** no_match 등: SYSTEM 안내 턴 (TEXT 파트 1개). metadataJson은 현 파트 모델에서 사용하지 않는다 */
    public static AssistantMessageDraft system(String content, String metadataJson) {
        return new AssistantMessageDraft(MessageRole.SYSTEM, List.of(PartDraft.text(content)));
    }

    /** 카드류: ASSISTANT 턴 = CARD 파트 1개. cardType은 payloadJson에서 파싱해 채운다(ConversationService) */
    public static AssistantMessageDraft card(String payloadJson) {
        return new AssistantMessageDraft(MessageRole.ASSISTANT,
                List.of(PartDraft.card(null, payloadJson)));
    }
}
