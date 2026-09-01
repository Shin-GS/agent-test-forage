package com.testforge.scheduler;

import com.testforge.sse.GlobalSseRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 30초마다 모든 SSE 연결에 heartbeat를 보낸다(messaging.md — 30초 주기, 60초 미수신 시 재연결).
 *
 * <p>heartbeat는 **SSE comment**로 전송한다({@link GlobalSseRegistry#sendHeartbeat}).
 * comment는 이벤트가 아니라 id/event 필드가 없어 Last-Event-ID를 오염시키지 않으며,
 * replay 버퍼에도 쌓지 않아 데이터 이벤트의 재연결 기준점을 훼손하지 않는다.
 * 전송 실패한 emitter는 registry 내부에서 즉시 정리되어 좀비 커넥션이 남지 않는다.
 */
@Component
public class SseHeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(SseHeartbeatScheduler.class);

    private final GlobalSseRegistry registry;

    public SseHeartbeatScheduler(GlobalSseRegistry registry) {
        this.registry = registry;
    }

    /** 30초마다 연결된 모든 사용자에게 heartbeat comment 전송. */
    @Scheduled(fixedRate = 30_000L)
    public void beat() {
        Set<Long> userIds = registry.connectedUserIds();
        if (userIds.isEmpty()) {
            return;
        }
        int sent = 0;
        for (Long userId : userIds) {
            if (registry.sendHeartbeat(userId)) {
                sent++;
            }
        }
        log.debug("Heartbeat sent to {}/{} SSE connection(s)", sent, userIds.size());
    }
}
