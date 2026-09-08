package com.testforge.dto.conversation;

import com.testforge.entity.conversation.enums.PartStatus;
import com.testforge.entity.conversation.enums.PartType;

/**
 * 턴에 append할 파트 1개의 초안. {@link AssistantMessageDraft}가 파트 초안 배열을 담아 한 턴을 표현한다.
 * ConversationService가 이 초안을 실제 {@code MESSAGE_PART}로 저장한다.
 *
 * @param type        파트 타입
 * @param status      파트 상태
 * @param content     TEXT 파트 본문 (없으면 null)
 * @param cardType    CARD 파트 세부 유형 (없으면 null)
 * @param payloadJson 타입별 구조화 데이터 JSON 문자열 (없으면 null)
 * @param executionId 실행류 파트가 가리키는 실행 ID (없으면 null)
 * @param investigationId 조회류 파트가 가리키는 조회 ID (없으면 null)
 * @param schemaVersion payload 스키마 버전 (기본 1)
 */
public record PartDraft(
        PartType type,
        PartStatus status,
        String content,
        String cardType,
        String payloadJson,
        Long executionId,
        Long investigationId,
        int schemaVersion) {

    /** TEXT 파트 (COMPLETE) */
    public static PartDraft text(String content) {
        return new PartDraft(PartType.TEXT, PartStatus.COMPLETE, content, null, null, null, null, 1);
    }

    /** REFERENCES 파트 (COMPLETE) — 조회 참고 자료 payload */
    public static PartDraft references(String payloadJson) {
        return new PartDraft(PartType.REFERENCES, PartStatus.COMPLETE, null, null, payloadJson, null, null, 1);
    }

    /** CARD 파트 (PENDING — 인터랙티브 대기) */
    public static PartDraft card(String cardType, String payloadJson) {
        return new PartDraft(PartType.CARD, PartStatus.PENDING, null, cardType, payloadJson, null, null, 1);
    }
}
