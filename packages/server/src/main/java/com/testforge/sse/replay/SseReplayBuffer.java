package com.testforge.sse.replay;

import com.testforge.sse.SseEvent;

import java.util.List;

/**
 * 사용자 스트림별 최근 SSE 이벤트 버퍼. 재연결 시 Last-Event-ID 이후 놓친 이벤트를
 * replay 하는 데 쓴다(messaging.md 재연결 정책).
 *
 * <p>인메모리 구현이 기본이지만, 다중 인스턴스/무손실이 필요하면 Redis 등으로 교체할 수 있도록
 * 인터페이스로 추상화한다. 구현체는 반드시 스레드 안전해야 한다(SSE 발행은 여러 스레드에서 호출).
 */
public interface SseReplayBuffer {

    /** 발행된(eventId가 채워진) 이벤트를 사용자 버퍼에 추가한다. */
    void append(Long userId, SseEvent event);

    /**
     * {@code lastEventId} 이후(초과)의 이벤트를 eventId 오름차순으로 반환한다.
     * lastEventId가 null이면 버퍼에 남은 전체를 반환한다. 버퍼가 없으면 빈 리스트.
     */
    List<SseEvent> after(Long userId, Long lastEventId);
}
