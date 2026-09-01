package com.testforge.sse;

import com.testforge.sse.enums.SseEventType;
import com.testforge.sse.replay.SseReplayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE 발행 단일 통로. 모든 이벤트는 여기를 거쳐 나간다:
 * <ol>
 *   <li>사용자 스트림 단위로 eventId를 단조 증가 발번</li>
 *   <li>replay 버퍼에 append (재연결 replay 대비)</li>
 *   <li>{@link GlobalSseRegistry}로 라이브 전송</li>
 * </ol>
 *
 * <p>발행은 <b>best-effort</b>다. 전송 실패가 호출측(예: 메시지 저장 트랜잭션)을 깨뜨리면 안 되므로
 * 내부에서 예외를 삼키고 로그만 남긴다. eventId는 append와 send 순서에 무관하게 replay의 기준점이
 * 되도록, 발번 후 버퍼에 먼저 쌓고 전송한다.
 */
@Component
public class SseEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SseEventPublisher.class);

    private final GlobalSseRegistry registry;
    private final SseReplayBuffer replayBuffer;

    /** 사용자별 eventId 시퀀스 (스트림 단위 단조 증가) */
    private final Map<Long, AtomicLong> sequences = new ConcurrentHashMap<>();

    public SseEventPublisher(GlobalSseRegistry registry, SseReplayBuffer replayBuffer) {
        this.registry = registry;
        this.replayBuffer = replayBuffer;
    }

    /**
     * 타입 + sessionId + data로 봉투를 만들어 사용자에게 발행한다.
     * userId가 null이면 발행 대상이 없으므로 no-op.
     */
    public void toUser(Long userId, SseEventType type, Long sessionId, Object data) {
        if (userId == null) {
            return;
        }
        toUser(userId, SseEvent.of(type, sessionId, data));
    }

    /**
     * 이미 만들어진 봉투를 발행한다. eventId를 발번해 채운 뒤 버퍼 append + 라이브 전송.
     * 예외는 삼키고 로그만 남긴다(best-effort).
     */
    public void toUser(Long userId, SseEvent event) {
        if (userId == null || event == null) {
            return;
        }
        try {
            long eventId = nextEventId(userId);
            SseEvent finalized = event.withEventId(eventId);
            replayBuffer.append(userId, finalized);
            registry.send(userId, finalized);
        } catch (Exception e) {
            // 발행 실패가 호출측 트랜잭션을 깨지 않도록 삼킨다
            log.warn("SSE publish failed (ignored): userId={}, type={}", userId, event.type(), e);
        }
    }

    /**
     * 재연결 시 Last-Event-ID 이후 놓친 이벤트를 라이브 스트림에 앞서 replay 한다.
     * 반환값은 replay 한 이벤트 수(모니터링/테스트용).
     */
    public int replayTo(Long userId, Long lastEventId) {
        if (userId == null) {
            return 0;
        }
        List<SseEvent> missed = replayBuffer.after(userId, lastEventId);
        for (SseEvent event : missed) {
            registry.send(userId, event);
        }
        if (!missed.isEmpty()) {
            log.info("Replayed {} SSE event(s): userId={}, afterEventId={}", missed.size(), userId, lastEventId);
        }
        return missed.size();
    }

    /** 사용자 스트림의 다음 eventId (1부터 단조 증가). */
    private long nextEventId(Long userId) {
        return sequences.computeIfAbsent(userId, k -> new AtomicLong(0L)).incrementAndGet();
    }
}
