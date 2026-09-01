package com.testforge.sse.enums;

import com.testforge.common.EnumColumn;

/**
 * SSE 이벤트가 속한 관심사(category). FE 렌더링 라우팅 기준이다(messaging.md).
 *
 * <p>봉투(envelope)의 {@code category} 필드로 직렬화되며, 문자열 코드는 name()을 그대로 쓴다.
 */
public enum SseCategory implements EnumColumn {

    /** 대화 메시지/카드 (액션 피커·인증 카드 포함) */
    CHAT("대화 메시지"),
    /** 대화방 상태/목록 */
    SESSION("대화방 상태/목록"),
    /** 레시피 실행 진행/완료 */
    EXECUTION("레시피 실행"),
    /** 시스템/연결 수준 신호 (heartbeat 등) */
    SYSTEM("시스템 신호"),
    /** 알림센터 (예약 — 추후) */
    NOTIFICATION("알림");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    SseCategory(String description) {
        this.description = description;
    }

    /** 봉투에 실리는 코드값. 현재는 enum name()과 동일 */
    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
