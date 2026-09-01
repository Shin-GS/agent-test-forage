package com.testforge.entity.conversation.enums;

import com.testforge.common.EnumColumn;

/**
 * 메시지 상태 (MESSAGE.STATUS). AI 응답 자리를 PENDING으로 미리 두고 완료/실패로 전이한다.
 * 사용자 메시지는 저장 시점에 COMPLETED로 기록한다.
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum MessageStatus implements EnumColumn {

    /** 생성 대기 (AI 응답 자리 예약) */
    PENDING("대기"),
    /** 완료 (내용 채워짐) */
    COMPLETED("완료"),
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
