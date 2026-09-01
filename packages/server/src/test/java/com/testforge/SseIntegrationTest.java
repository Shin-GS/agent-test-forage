package com.testforge;

import com.testforge.sse.GlobalSseRegistry;
import com.testforge.sse.SseEventPublisher;
import com.testforge.sse.enums.SseEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSE 연결→이벤트 수신 E2E 통합 테스트 (H2 test 프로파일, 실제 HTTP SSE 엔드포인트).
 *
 * <p>기존 SSE 단위 테스트(replay 버퍼/publisher/타입)와 달리, 실제 {@code /api/v1/sse/connect}
 * HTTP 연결을 MockMvc로 열고 {@link SseEventPublisher}로 이벤트를 발행하여 스트림에 흘러나오는지
 * 검증한다. SSE는 런타임 함정(async 미종료, 와이어 포맷, 연결 교체)이 많아 이 계층 검증이 중요하다.
 *
 * <p><b>테스트 격리</b>: {@code @SpringBootTest}는 컨텍스트(따라서 {@link GlobalSseRegistry},
 * {@link SseEventPublisher}의 eventId 시퀀스/replay 버퍼)를 테스트 간 공유한다. eventId 절대값이나
 * 버퍼 상태에 의존하면 실행 순서에 따라 깨진다. 그래서 각 테스트는 {@link #uniqueUserId()}로
 * 고유 userId를 써서 서로의 스트림/버퍼/시퀀스와 격리한다.
 *
 * <p><b>MockMvc SSE 본문 읽기 전략</b>: SseEmitter는 응답을 열어둔 채 유지되므로 async 요청이
 * 끝나기 전에는 {@code getContentAsString()}이 비어 있고 Content-Type도 확정되지 않는다. 이벤트를
 * 발행한 뒤 registry에서 해당 emitter를 꺼내 {@code complete()}하고 {@code asyncDispatch}로 async를
 * 종료시키면, 그 시점까지 써진 바이트를 응답 본문에서 읽고 헤더도 확정된다. (실제 EventSource
 * 클라이언트가 없는 MockMvc 환경의 한계를 우회하는 표준 방식)
 */
@SpringBootTest
@ActiveProfiles("test")
class SseIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private GlobalSseRegistry registry;

    @Autowired
    private SseEventPublisher publisher;

    private MockMvc mockMvc;

    /** 테스트 간 격리를 위한 userId 발급기 (공유 컨텍스트 오염 방지). */
    private static final AtomicLong USER_SEQ = new AtomicLong(1000L);

    private long uniqueUserId() {
        return USER_SEQ.incrementAndGet();
    }

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        }
        return mockMvc;
    }

    // ── (1) 연결 성공: 200 + async 시작 + activeConnectionCount 증가, dispatch 후 text/event-stream ──
    @Test
    void connect_returnsEventStreamAndRegistersConnection() throws Exception {
        long userId = uniqueUserId();
        int before = registry.activeConnectionCount();

        MvcResult result = mvc().perform(get("/api/v1/sse/connect").param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        // 연결이 registry에 등록됨 (이 userId에 대한 연결이 새로 생김)
        assertThat(registry.activeConnectionCount()).isEqualTo(before + 1);

        // emitter를 complete 후 async를 종료시키면 Content-Type이 확정된다
        completeEmitter(userId);
        mvc().perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    // ── (2)(3) 연결 후 이벤트 수신 + 와이어 이벤트 이름 소문자 검증 (핵심) ──
    @Test
    void connect_thenPublish_streamsEventWithLowercaseWireName() throws Exception {
        long userId = uniqueUserId();

        MvcResult result = mvc().perform(get("/api/v1/sse/connect").param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        // 연결된 스트림으로 이벤트 발행 (eventId 발번, 버퍼 append, 라이브 전송)
        publisher.toUser(userId, SseEventType.MESSAGE_NEW, 42L, Map.of("text", "hi"));

        // emitter를 complete하고 async를 종료시켜 지금까지 써진 본문을 확정
        completeEmitter(userId);
        String body = mvc().perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // (2) 발행한 이벤트가 스트림에 나타남: event / data 라인 (id 라인 포함)
        assertThat(body).contains("event:message_new");
        // (3) 와이어 이벤트 이름은 소문자 snake_case (messaging.md 규약) — enum name은 안 나가야 함
        assertThat(body).doesNotContain("event:MESSAGE_NEW");
        // data 페이로드(봉투 JSON)에 소문자 type과 sessionId/payload 포함
        assertThat(body).contains("\"type\":\"message_new\"");
        assertThat(body).contains("\"sessionId\":42");
        assertThat(body).contains("\"text\":\"hi\"");
        // 스트림에 SSE id 라인이 존재 (Last-Event-ID 기준점)
        assertThat(body).contains("id:");
    }

    // ── (4) replay: 이벤트 2개 발행 후 Last-Event-ID로 재연결 → 두번째만 replay ──
    @Test
    void reconnect_withLastEventId_replaysOnlyMissedEvents() throws Exception {
        long userId = uniqueUserId();

        // 연결이 없어도 publisher는 eventId 발번 + 버퍼 append를 수행한다(send만 no-op).
        // 이 userId 스트림의 첫 두 이벤트 → eventId는 1, 2 (스트림 단위 시퀀스, userId 격리로 확정적)
        publisher.toUser(userId, SseEventType.MESSAGE_NEW, 1L, Map.of("tag", "first"));   // eventId=1
        publisher.toUser(userId, SseEventType.MESSAGE_NEW, 2L, Map.of("tag", "second"));  // eventId=2

        // Last-Event-ID=1 로 재연결하면 컨트롤러(SseController.connect)가 eventId>1 인 것만 replay.
        // 실제 HTTP 재연결 경로로 검증한다.
        MvcResult result = mvc().perform(get("/api/v1/sse/connect")
                        .param("userId", String.valueOf(userId))
                        .header("Last-Event-ID", "1"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        completeEmitter(userId);
        String body = mvc().perform(asyncDispatch(result))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 재연결 스트림에는 두번째 이벤트(eventId=2, tag=second)만 replay 되어야 한다
        assertThat(body).contains("\"eventId\":2");
        assertThat(body).contains("\"tag\":\"second\"");
        // 첫번째(eventId=1, tag=first)는 이미 받았으므로 replay 대상이 아님
        assertThat(body).doesNotContain("\"eventId\":1");
        assertThat(body).doesNotContain("\"tag\":\"first\"");
    }

    // ── (5) 사용자당 1연결 교체: 같은 userId 두 번 connect → 총 count 불변, 이전 연결 complete ──
    @Test
    void connect_sameUserTwice_replacesConnectionKeepingCountStable() throws Exception {
        long userId = uniqueUserId();
        int before = registry.activeConnectionCount();

        MvcResult first = mvc().perform(get("/api/v1/sse/connect").param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(registry.activeConnectionCount()).isEqualTo(before + 1);

        // 같은 사용자로 재연결 → 이전 emitter는 complete되고 새 emitter로 교체 (총 count 불변)
        MvcResult second = mvc().perform(get("/api/v1/sse/connect").param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(registry.activeConnectionCount()).isEqualTo(before + 1);

        // 첫 연결은 교체로 이미 complete됨 → async dispatch가 정상 종료
        mvc().perform(asyncDispatch(first)).andExpect(status().isOk());

        // 두번째(현재) 연결 정리
        completeEmitter(userId);
        mvc().perform(asyncDispatch(second)).andExpect(status().isOk());
    }

    // ── (6) heartbeat는 SSE comment로 전송되어 id/event 라인이 없다 (Last-Event-ID 미오염) ──
    @Test
    void heartbeat_isSentAsCommentWithoutIdOrEvent() throws Exception {
        long userId = uniqueUserId();

        MvcResult result = mvc().perform(get("/api/v1/sse/connect").param("userId", String.valueOf(userId)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        // heartbeat 전송 (comment 방식)
        boolean sent = registry.sendHeartbeat(userId);
        assertThat(sent).isTrue();

        completeEmitter(userId);
        String body = mvc().perform(asyncDispatch(result))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // comment 라인(":heartbeat")만 있고, id/event 라인은 없어야 함 (Last-Event-ID 미오염)
        assertThat(body).contains(":heartbeat");
        assertThat(body).doesNotContain("event:heartbeat");
        assertThat(body).doesNotContain("id:");
    }

    /**
     * registry에 등록된 현재 사용자 emitter를 꺼내 complete 한다. registry는 emitter를 외부에
     * 노출하지 않으므로, 열린 SSE async 응답을 종료시키기 위해 리플렉션으로 접근한다.
     * (테스트 전용 — 프로덕션 코드 변경 없이 MockMvc의 열린 async 요청을 끝내기 위함)
     */
    @SuppressWarnings("unchecked")
    private void completeEmitter(long userId) throws Exception {
        java.lang.reflect.Field f = GlobalSseRegistry.class.getDeclaredField("emitters");
        f.setAccessible(true);
        Map<Long, SseEmitter> emitters = (Map<Long, SseEmitter>) f.get(registry);
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            emitter.complete();
        }
    }
}
