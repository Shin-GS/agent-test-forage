package com.testforge.entity.conversation.enums;

import com.testforge.common.EnumColumn;

/**
 * 턴 전체 상태 (MESSAGE.STATUS). AI 응답을 시작할 때 빈 ASSISTANT 턴을 {@code STREAMING}으로 INSERT하고
 * 파트를 append하며 진행하다가, 완료 시 {@code COMPLETE}, 오류 시 {@code FAILED}로 확정한다.
 * 사용자 턴은 저장 시 {@code COMPLETE}로 기록한다(messaging.md 종결 보장/낙관적 UI).
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum MessageStatus implements EnumColumn {

    /** 스트리밍 중 (AI 응답 진행 — 파트 append 중) */
    STREAMING("스트리밍"),
    /** 완료 (턴 확정) */
    COMPLETE("완료"),
    /** 실패 */
    FAILED("실패");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    MessageStatus(String description) {
        this.description = description;
    }

    /** DB 저장 코드값. 현재는 enum name()과 동일 */
    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
