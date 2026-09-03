package com.testforge.sse;

import com.testforge.sse.enums.SseCategory;
import com.testforge.sse.enums.SseEventNature;
import com.testforge.sse.enums.SseEventType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SseEventType의 category/nature/wireName 매핑이 messaging.md 정의와 일치하는지 검증한다.
 */
class SseEventTypeTest {

    @Test
    void categoryAndNature_matchMessagingSpec() {
        assertThat(SseEventType.MESSAGE_NEW.getCategory()).isEqualTo(SseCategory.CHAT);
        assertThat(SseEventType.MESSAGE_NEW.getNature()).isEqualTo(SseEventNature.DATA);

        assertThat(SseEventType.MESSAGE_UPDATE.getCategory()).isEqualTo(SseCategory.CHAT);
        assertThat(SseEventType.MESSAGE_UPDATE.getNature()).isEqualTo(SseEventNature.DATA);

        assertThat(SseEventType.SESSION_STATUS.getCategory()).isEqualTo(SseCategory.SESSION);
        assertThat(SseEventType.SESSION_STATUS.getNature()).isEqualTo(SseEventNature.SIGNAL);

        assertThat(SseEventType.SESSION_LIST_UPDATE.getCategory()).isEqualTo(SseCategory.SESSION);
        assertThat(SseEventType.SESSION_LIST_UPDATE.getNature()).isEqualTo(SseEventNature.SIGNAL);

        // 실행 진행/완료는 CHAT 메시지(PROGRESS/RESULT)로 흐른다. execution_progress/complete는 폐지됨.

        assertThat(SseEventType.HEARTBEAT.getCategory()).isEqualTo(SseCategory.SYSTEM);
        assertThat(SseEventType.HEARTBEAT.getNature()).isEqualTo(SseEventNature.SIGNAL);
    }

    @Test
    void wireName_isLowerSnakeCase() {
        assertThat(SseEventType.MESSAGE_NEW.wireName()).isEqualTo("message_new");
        assertThat(SseEventType.MESSAGE_UPDATE.wireName()).isEqualTo("message_update");
        assertThat(SseEventType.SESSION_LIST_UPDATE.wireName()).isEqualTo("session_list_update");
        assertThat(SseEventType.HEARTBEAT.wireName()).isEqualTo("heartbeat");
    }

    @Test
    void getCode_equalsEnumName() {
        assertThat(SseEventType.MESSAGE_NEW.getCode()).isEqualTo("MESSAGE_NEW");
        assertThat(SseCategory.CHAT.getCode()).isEqualTo("CHAT");
        assertThat(SseEventNature.DATA.getCode()).isEqualTo("DATA");
    }

    @Test
    void sseEvent_of_derivesEnvelopeFromType() {
        SseEvent event = SseEvent.of(SseEventType.MESSAGE_NEW, 123L, "payload");

        assertThat(event.eventId()).isZero(); // 아직 미발번
        assertThat(event.category()).isEqualTo("CHAT");
        assertThat(event.type()).isEqualTo("message_new");
        assertThat(event.nature()).isEqualTo("DATA");
        assertThat(event.sessionId()).isEqualTo(123L);
        assertThat(event.data()).isEqualTo("payload");
    }

    @Test
    void withEventId_replacesOnlyEventId() {
        SseEvent event = SseEvent.of(SseEventType.HEARTBEAT, null, "d").withEventId(42L);

        assertThat(event.eventId()).isEqualTo(42L);
        assertThat(event.type()).isEqualTo("heartbeat");
        assertThat(event.sessionId()).isNull();
    }
}
