package com.testforge.entity.conversation.enums;

import com.testforge.common.EnumColumn;

/**
 * 메시지 작성 주체 (MESSAGE.ROLE). AI Tool Use 결과는 TOOL로 표현한다.
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum MessageRole implements EnumColumn {

    /** 사용자 발화 */
    USER("사용자"),
    /** AI 응답 */
    ASSISTANT("AI"),
    /** AI Tool Use 결과 */
    TOOL("툴");

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
