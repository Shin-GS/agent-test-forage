package com.testforge.sse.enums;

import com.testforge.common.EnumColumn;

import java.util.Locale;

/**
 * SSE 이벤트 타입. 각 타입이 소속 {@link SseCategory}와 {@link SseEventNature}를 함께 보유한다
 * (messaging.md의 "type이 category와 nature를 모두 보유"). 발행 시 type만 지정하면
 * 봉투에 category/nature가 함께 직렬화된다.
 *
 * <p>SSE 와이어 이벤트 이름(EventSource의 event 필드)은 messaging.md 규약대로 소문자
 * snake_case({@code message_new} 등)로 나가야 하므로 {@link #wireName()}으로 별도 제공한다.
 * enum 상수 이름은 Java 관례(UPPER_SNAKE_CASE)를 따른다.
 */
public enum SseEventType implements EnumColumn {

    /** 새 메시지 도착 (data: 메시지 JSON 전체) */
    MESSAGE_NEW(SseCategory.CHAT, SseEventNature.DATA, "새 메시지"),
    /** 기존 메시지 업데이트 (진행 상태, 추후 토큰 스트리밍) */
    MESSAGE_UPDATE(SseCategory.CHAT, SseEventNature.DATA, "메시지 업데이트"),
    /** 대화방 처리 상태 변경 (입력 영역 구동, 고빈도) */
    SESSION_STATUS(SseCategory.SESSION, SseEventNature.SIGNAL, "대화방 상태 변경"),
    /** 대화방 목록 한 줄 갱신 (추가/삭제/이름·서비스·읽음·상태 흡수) */
    SESSION_LIST_UPDATE(SseCategory.SESSION, SseEventNature.SIGNAL, "대화방 목록 갱신"),
    /** 레시피 실행 스텝 진행 */
    EXECUTION_PROGRESS(SseCategory.EXECUTION, SseEventNature.SIGNAL, "실행 진행"),
    /** 레시피 실행 종료 (결과/사유 포함) */
    EXECUTION_COMPLETE(SseCategory.EXECUTION, SseEventNature.DATA, "실행 종료"),
    /** 연결 유지용 heartbeat */
    HEARTBEAT(SseCategory.SYSTEM, SseEventNature.SIGNAL, "연결 유지");

    /** 이벤트가 속한 관심사 */
    private final SseCategory category;

    /** 이벤트 성격 (SIGNAL/DATA) */
    private final SseEventNature nature;

    /** 사람이 읽는 한글 설명 */
    private final String description;

    SseEventType(SseCategory category, SseEventNature nature, String description) {
        this.category = category;
        this.nature = nature;
        this.description = description;
    }

    public SseCategory getCategory() {
        return category;
    }

    public SseEventNature getNature() {
        return nature;
    }

    /** SSE 와이어 이벤트 이름 (소문자 snake_case). 예: MESSAGE_NEW → "message_new" */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
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
