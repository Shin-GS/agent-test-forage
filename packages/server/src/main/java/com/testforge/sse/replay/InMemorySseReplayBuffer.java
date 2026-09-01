package com.testforge.sse.replay;

import com.testforge.sse.SseEvent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인메모리 replay 버퍼. 사용자별로 최근 이벤트를 개수 상한 + 시간 만료(TTL)로 유지한다.
 * 프로토타입/단일 인스턴스 기본 구현이며, 서버 재시작 시 사라진다(messaging.md 5분 replay 정책).
 *
 * <p>스레드 안전: 사용자별 Deque 접근을 해당 Deque 모니터로 동기화한다. 발행 경로는 짧아
 * 락 경합이 크지 않다. 다중 인스턴스가 필요하면 {@link SseReplayBuffer} 구현을 Redis로 교체한다.
 */
@Component
public class InMemorySseReplayBuffer implements SseReplayBuffer {

    /** 사용자별 보관 이벤트 최대 개수 */
    private static final int MAX_EVENTS_PER_USER = 100;

    /** 보관 유지 시간 (초과분은 만료) */
    private static final Duration RETENTION = Duration.ofMinutes(5);

    /** eventId + 도착 시각을 함께 보관 (시간 만료 판정용) */
    private record Entry(SseEvent event, Instant storedAt) {
    }

    private final Map<Long, Deque<Entry>> buffers = new ConcurrentHashMap<>();

    @Override
    public void append(Long userId, SseEvent event) {
        if (userId == null || event == null) {
            return;
        }
        Deque<Entry> deque = buffers.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(new Entry(event, Instant.now()));
            evict(deque);
        }
    }

    @Override
    public List<SseEvent> after(Long userId, Long lastEventId) {
        if (userId == null) {
            return List.of();
        }
        Deque<Entry> deque = buffers.get(userId);
        if (deque == null) {
            return List.of();
        }
        List<SseEvent> result = new ArrayList<>();
        synchronized (deque) {
            evict(deque);
            for (Entry entry : deque) {
                if (lastEventId == null || entry.event().eventId() > lastEventId) {
                    result.add(entry.event());
                }
            }
        }
        return result;
    }

    /** 개수 상한 + TTL 만료 정리. 반드시 deque 모니터를 쥔 상태에서 호출한다. */
    private void evict(Deque<Entry> deque) {
        Instant cutoff = Instant.now().minus(RETENTION);
        // 시간 만료: 오래된 앞쪽부터 제거
        Iterator<Entry> it = deque.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.storedAt().isBefore(cutoff)) {
                it.remove();
            } else {
                break; // 삽입 순서(시간 오름차순) 보장 → 첫 미만료에서 중단
            }
        }
        // 개수 상한: 초과분을 앞쪽부터 제거
        while (deque.size() > MAX_EVENTS_PER_USER) {
            deque.pollFirst();
        }
    }
}
