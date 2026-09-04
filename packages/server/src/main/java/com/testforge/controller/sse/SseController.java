package com.testforge.controller.sse;

import com.testforge.security.CurrentUser;
import com.testforge.sse.GlobalSseRegistry;
import com.testforge.sse.SseEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Global SSE 연결 엔드포인트. 사용자당 1개 연결로 모든 대화방의 이벤트를 하나의 스트림으로 받는다
 * (messaging.md). 연결 시 Last-Event-ID 헤더가 있으면 그 이후 놓친 이벤트를 먼저 replay 한 뒤
 * 라이브 스트림을 이어간다.
 *
 * <p>인증: 세션 쿠키로 자동 인증되며(EventSource withCredentials), userId는 세션에서 도출한다
 * (auth.md SSE 인증). 쿼리 토큰/유저ID를 쓰지 않는다.
 */
@RestController
@RequestMapping("/api/v1/sse")
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    private final GlobalSseRegistry registry;
    private final SseEventPublisher publisher;

    public SseController(GlobalSseRegistry registry, SseEventPublisher publisher) {
        this.registry = registry;
        this.publisher = publisher;
    }

    /**
     * SSE 연결 수립. text/event-stream을 반환한다. userId는 세션에서 도출한다.
     *
     * @param lastEventId 재연결 시 마지막 수신 이벤트 ID (없으면 신규 연결)
     */
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId) {
        Long userId = CurrentUser.id();
        SseEmitter emitter = registry.register(userId);

        // 재연결이면 놓친 이벤트를 라이브에 앞서 replay
        if (lastEventId != null) {
            publisher.replayTo(userId, lastEventId);
        }

        log.info("SSE connect: userId={}, lastEventId={}", userId, lastEventId);
        return emitter;
    }
}
