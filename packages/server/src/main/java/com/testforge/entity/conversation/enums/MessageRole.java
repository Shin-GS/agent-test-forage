package com.testforge.entity.conversation.enums;

import com.testforge.common.EnumColumn;

/**
 * 메시지(턴) 작성 주체 (MESSAGE.ROLE). 대화 = 턴(MESSAGE)의 나열이고, 한 턴은 사용자 발화 1개 또는
 * AI 응답 1턴이다. 시스템 안내(취소/중지 등)는 {@code SYSTEM} 턴 + TEXT 파트 1개로 표현한다
 * (messaging.md). DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum MessageRole implements EnumColumn {

    /** 사용자 발화 */
    USER("사용자"),
    /** AI 응답 */
    ASSISTANT("AI"),
    /** 시스템 안내 (취소/중지 등) */
    SYSTEM("시스템");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    MessageRole(String description) {
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
