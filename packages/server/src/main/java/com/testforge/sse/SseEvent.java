package com.testforge.sse;

import com.testforge.sse.enums.SseCategory;
import com.testforge.sse.enums.SseEventNature;
import com.testforge.sse.enums.SseEventType;

/**
 * SSE 표준 봉투(envelope). 모든 이벤트는 이 하나의 형태로 전달된다(messaging.md).
 *
 * <p>직렬화 예:
 * <pre>
 * {
 *   "eventId": 42,
 *   "category": "CHAT",
 *   "type": "message_new",
 *   "nature": "DATA",
 *   "sessionId": 123,
 *   "data": { ... }
 * }
 * </pre>
 *
 * <p>{@code eventId}는 사용자 스트림 단위로 단조 증가하며 {@link SseEventPublisher}가 발번한다
 * (재연결 시 Last-Event-ID 기준점). {@code category}/{@code nature}는 {@link SseEventType}에서
 * 파생되고, {@code type}은 소문자 와이어 이름({@link SseEventType#wireName()})으로 직렬화된다.
 * {@code sessionId}는 대화방 이벤트면 지정, 전역(알림 등)이면 null.
 */
public record SseEvent(
        // 사용자 스트림 단위 단조 증가 ID (Last-Event-ID 기준점)
        long eventId,
        // 이벤트 관심사 (예: CHAT)
        String category,
        // 이벤트 타입 (소문자 와이어 이름, 예: message_new)
        String type,
        // 이벤트 성격 (SIGNAL/DATA)
        String nature,
        // 대화방 ID (전역 이벤트면 null)
        Long sessionId,
        // 타입별 페이로드
        Object data) {

    /**
     * 아직 eventId가 발번되지 않은 이벤트를 만든다({@code eventId=0}).
     * 실제 ID는 {@link SseEventPublisher}가 발행 직전에 {@link #withEventId(long)}로 채운다.
     */
    public static SseEvent of(SseEventType type, Long sessionId, Object data) {
        return new SseEvent(
                0L,
                type.getCategory().getCode(),
                type.wireName(),
                type.getNature().getCode(),
                sessionId,
                data);
    }

    /** 발번된 eventId를 채운 새 봉투를 반환한다(record 불변성 유지). */
    public SseEvent withEventId(long assignedEventId) {
        return new SseEvent(assignedEventId, category, type, nature, sessionId, data);
    }

    /** category enum 헬퍼 (필요 시 라우팅 판단용). */
    public SseCategory categoryEnum() {
        return SseCategory.valueOf(category);
    }

    /** nature enum 헬퍼. */
    public SseEventNature natureEnum() {
        return SseEventNature.valueOf(nature);
    }
}
