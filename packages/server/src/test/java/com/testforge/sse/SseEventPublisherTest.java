package com.testforge.sse;

import com.testforge.sse.enums.SseEventType;
import com.testforge.sse.replay.SseReplayBuffer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SseEventPublisher가 eventId 발번 → replay 버퍼 append → registry 전송 순서를 지키는지,
 * eventId가 사용자 스트림 단위로 단조 증가하는지, replay가 버퍼를 registry로 흘려보내는지 검증한다.
 */
class SseEventPublisherTest {

    @Test
    void toUser_assignsEventId_appendsToBuffer_andSends() {
        GlobalSseRegistry registry = mock(GlobalSseRegistry.class);
        SseReplayBuffer buffer = mock(SseReplayBuffer.class);
        SseEventPublisher publisher = new SseEventPublisher(registry, buffer);

        publisher.toUser(1L, SseEventType.MESSAGE_NEW, 100L, "payload");

        ArgumentCaptor<SseEvent> bufferCaptor = ArgumentCaptor.forClass(SseEvent.class);
        verify(buffer).append(eq(1L), bufferCaptor.capture());
        SseEvent buffered = bufferCaptor.getValue();
        assertThat(buffered.eventId()).isEqualTo(1L);
        assertThat(buffered.type()).isEqualTo("message_new");

        ArgumentCaptor<SseEvent> sendCaptor = ArgumentCaptor.forClass(SseEvent.class);
        verify(registry).send(eq(1L), sendCaptor.capture());
        assertThat(sendCaptor.getValue().eventId()).isEqualTo(1L);
    }

    @Test
    void toUser_eventIdIncrementsPerUserStream() {
        GlobalSseRegistry registry = mock(GlobalSseRegistry.class);
        SseReplayBuffer buffer = mock(SseReplayBuffer.class);
        SseEventPublisher publisher = new SseEventPublisher(registry, buffer);

        publisher.toUser(1L, SseEventType.MESSAGE_NEW, null, "a");
        publisher.toUser(1L, SseEventType.MESSAGE_NEW, null, "b");
        publisher.toUser(2L, SseEventType.MESSAGE_NEW, null, "c");

        ArgumentCaptor<SseEvent> captor = ArgumentCaptor.forClass(SseEvent.class);
        verify(registry, times(2)).send(eq(1L), captor.capture());
        assertThat(captor.getAllValues()).extracting(SseEvent::eventId).containsExactly(1L, 2L);

        ArgumentCaptor<SseEvent> user2 = ArgumentCaptor.forClass(SseEvent.class);
        verify(registry).send(eq(2L), user2.capture());
        assertThat(user2.getValue().eventId()).isEqualTo(1L); // 사용자별 독립 시퀀스
    }

    @Test
    void toUser_nullUser_isNoOp() {
        GlobalSseRegistry registry = mock(GlobalSseRegistry.class);
        SseReplayBuffer buffer = mock(SseReplayBuffer.class);
        SseEventPublisher publisher = new SseEventPublisher(registry, buffer);

        publisher.toUser(null, SseEventType.MESSAGE_NEW, 1L, "x");

        verify(buffer, times(0)).append(any(), any());
        verify(registry, times(0)).send(any(), any());
    }

    @Test
    void toUser_swallowsRegistryException() {
        GlobalSseRegistry registry = mock(GlobalSseRegistry.class);
        SseReplayBuffer buffer = mock(SseReplayBuffer.class);
        when(registry.send(any(), any())).thenThrow(new RuntimeException("boom"));
        SseEventPublisher publisher = new SseEventPublisher(registry, buffer);

        // 예외가 밖으로 전파되면 안 됨 (best-effort)
        publisher.toUser(1L, SseEventType.MESSAGE_NEW, 1L, "x");
    }

    @Test
    void replayTo_forwardsBufferedEventsToRegistry() {
        GlobalSseRegistry registry = mock(GlobalSseRegistry.class);
        SseReplayBuffer buffer = mock(SseReplayBuffer.class);
        SseEvent e1 = SseEvent.of(SseEventType.MESSAGE_NEW, 1L, "a").withEventId(2L);
        SseEvent e2 = SseEvent.of(SseEventType.MESSAGE_NEW, 1L, "b").withEventId(3L);
        when(buffer.after(1L, 1L)).thenReturn(List.of(e1, e2));
        SseEventPublisher publisher = new SseEventPublisher(registry, buffer);

        int replayed = publisher.replayTo(1L, 1L);

        assertThat(replayed).isEqualTo(2);
        verify(registry).send(1L, e1);
        verify(registry).send(1L, e2);
    }
}
