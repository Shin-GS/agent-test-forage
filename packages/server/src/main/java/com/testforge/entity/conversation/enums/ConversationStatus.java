package com.testforge.entity.conversation.enums;

import com.testforge.common.EnumColumn;

/**
 * 대화방 처리 상태 (CONVERSATION.STATUS). session_status(messaging.md)와 1:1 매핑.
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 *
 * <p>이번 스코프에서는 컬럼/enum만 준비하고 실제 상태 전이(AI 처리/락)는 설정하지 않는다.
 */
public enum ConversationStatus implements EnumColumn {

    /** 유휴: 진행 중인 작업 없음 (기본값) */
    IDLE("유휴"),
    /** AI 응답 생성 중 */
    AI_RESPONDING("AI 응답 중"),
    /** 레시피/플랜 실행 중 */
    EXECUTING("실행 중"),
    /** 액션 피커 입력 대기 */
    WAITING_INPUT("입력 대기");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    ConversationStatus(String description) {
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
