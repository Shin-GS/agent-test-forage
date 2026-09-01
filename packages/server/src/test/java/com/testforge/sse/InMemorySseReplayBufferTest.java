package com.testforge.sse;

import com.testforge.sse.enums.SseEventType;
import com.testforge.sse.replay.InMemorySseReplayBuffer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InMemorySseReplayBuffer의 append/after/개수제한 동작을 검증한다.
 * (TTL 만료는 5분 상수라 단위 테스트에서 시간 조작 없이 직접 검증하지 않는다 — 개수 상한으로 대체 검증)
 */
class InMemorySseReplayBufferTest {

    private static final long USER = 1L;

    private SseEvent event(long eventId) {
        return SseEvent.of(SseEventType.MESSAGE_NEW, 100L, "d").withEventId(eventId);
    }

    @Test
    void after_withLastEventId_returnsOnlyNewerEvents() {
        InMemorySseReplayBuffer buffer = new InMemorySseReplayBuffer();
        buffer.append(USER, event(1));
        buffer.append(USER, event(2));
        buffer.append(USER, event(3));

        List<SseEvent> after = buffer.after(USER, 1L);

        assertThat(after).extracting(SseEvent::eventId).containsExactly(2L, 3L);
    }

    @Test
    void after_withNullLastEventId_returnsAll() {
        InMemorySseReplayBuffer buffer = new InMemorySseReplayBuffer();
        buffer.append(USER, event(1));
        buffer.append(USER, event(2));

        assertThat(buffer.after(USER, null)).extracting(SseEvent::eventId).containsExactly(1L, 2L);
    }

    @Test
    void after_unknownUser_returnsEmpty() {
        InMemorySseReplayBuffer buffer = new InMemorySseReplayBuffer();
        assertThat(buffer.after(999L, null)).isEmpty();
    }

    @Test
    void after_lastEventIdAtOrBeyondLatest_returnsEmpty() {
        InMemorySseReplayBuffer buffer = new InMemorySseReplayBuffer();
        buffer.append(USER, event(1));
        buffer.append(USER, event(2));

        assertThat(buffer.after(USER, 2L)).isEmpty();
        assertThat(buffer.after(USER, 5L)).isEmpty();
    }

    @Test
    void append_exceedingCap_dropsOldestKeepsMostRecent100() {
        InMemorySseReplayBuffer buffer = new InMemorySseReplayBuffer();
        // 150건 추가 → 최근 100건(51..150)만 유지
        for (long i = 1; i <= 150; i++) {
            buffer.append(USER, event(i));
        }

        List<SseEvent> all = buffer.after(USER, null);

        assertThat(all).hasSize(100);
        assertThat(all.get(0).eventId()).isEqualTo(51L);
        assertThat(all.get(all.size() - 1).eventId()).isEqualTo(150L);
    }

    @Test
    void append_isolatesPerUser() {
        InMemorySseReplayBuffer buffer = new InMemorySseReplayBuffer();
        buffer.append(1L, event(1));
        buffer.append(2L, event(1));
        buffer.append(2L, event(2));

        assertThat(buffer.after(1L, null)).hasSize(1);
        assertThat(buffer.after(2L, null)).hasSize(2);
    }

    @Test
    void append_nullArgs_areNoOp() {
        InMemorySseReplayBuffer buffer = new InMemorySseReplayBuffer();
        buffer.append(null, event(1));
        buffer.append(USER, null);

        assertThat(buffer.after(USER, null)).isEmpty();
    }
}
