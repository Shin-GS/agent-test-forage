package com.testforge.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자별 Global SSE 연결(emitter)을 관리한다. 방침상 사용자당 1개 연결만 유지하며,
 * 같은 사용자가 새로 연결하면 기존 emitter를 정리하고 교체한다(messaging.md — 사용자당 1 연결).
 *
 * <p>Spring MVC의 {@link SseEmitter} 함정을 피하기 위해:
 * <ul>
 *   <li>onTimeout/onCompletion/onError 콜백에서 <b>멱등</b>하게 맵에서 제거한다.</li>
 *   <li>onTimeout에서는 컨테이너가 자동으로 complete 하지 않으므로 직접 {@code complete()}를 호출한다.</li>
 *   <li>전송 실패(IOException 등) 시 해당 emitter를 즉시 제거해 좀비 커넥션을 막는다.</li>
 * </ul>
 *
 * <p>JSON 직렬화는 로컬 ObjectMapper를 쓴다(RecipeService/SpecQueryService와 동일하게
 * 공용 매퍼 빈에 의존하지 않는 패턴). LocalDateTime 등 java.time 직렬화를 위해
 * {@code findAndRegisterModules()}로 JSR-310 모듈을 등록한다.
 */
@Component
public class GlobalSseRegistry {

    private static final Logger log = LoggerFactory.getLogger(GlobalSseRegistry.class);

    /** SSE emitter 타임아웃 (ms). 0/음수는 무제한이나 좀비 방지를 위해 30분으로 둔다. */
    private static final long EMITTER_TIMEOUT_MS = 30L * 60L * 1000L;

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /**
     * 사용자용 새 emitter를 등록한다. 기존 연결이 있으면 정리 후 교체한다(사용자당 1 연결).
     * 콜백에서 맵을 멱등 정리하도록 등록한 뒤 emitter를 반환한다.
     */
    public SseEmitter register(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);

        // 기존 연결 교체: 새 emitter로 먼저 바꾸고, 이전 것을 안전하게 complete
        SseEmitter previous = emitters.put(userId, emitter);
        if (previous != null) {
            log.info("Replacing existing SSE connection: userId={}", userId);
            try {
                previous.complete();
            } catch (Exception ignore) {
                // 이미 종료된 연결일 수 있음 — 무시
            }
        }

        // 콜백은 항상 "현재 등록된 것이 이 emitter일 때만" 제거(멱등, 교체 레이스 방지)
        emitter.onCompletion(() -> removeIfCurrent(userId, emitter));
        emitter.onError(e -> removeIfCurrent(userId, emitter));
        emitter.onTimeout(() -> {
            // MVC는 타임아웃 시 자동 complete 하지 않음 → 직접 호출
            removeIfCurrent(userId, emitter);
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // 무시
            }
        });

        log.info("SSE connection registered: userId={}, activeConnections={}", userId, emitters.size());
        return emitter;
    }

    /**
     * 특정 사용자에게 이벤트를 전송한다. 연결이 없으면 false, 전송 성공 시 true.
     * 전송 실패 시 해당 emitter를 제거하고 false를 반환한다.
     */
    public boolean send(Long userId, SseEvent event) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return false;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(event.eventId()))
                    .name(event.type())
                    .data(json, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException e) {
            // 끊긴 연결 등 → 정리 (좀비 커넥션/메모리 누수 방지)
            log.debug("SSE send failed, removing emitter: userId={}, reason={}", userId, e.toString());
            removeIfCurrent(userId, emitter);
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // 무시
            }
            return false;
        } catch (Exception e) {
            // 직렬화 실패 등 예상 못한 오류 — 발행은 best-effort이므로 로그만
            log.warn("SSE send unexpected error: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 연결 유지용 heartbeat를 SSE comment로 전송한다. comment(":..." 라인)는 이벤트가 아니라
     * EventSource가 무시하며, id/event 필드가 없어 Last-Event-ID를 오염시키지 않는다(데이터
     * 이벤트의 replay 기준점 훼손 방지). 전송 실패 시 emitter를 정리하고 false 반환.
     */
    public boolean sendHeartbeat(Long userId) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE heartbeat failed, removing emitter: userId={}, reason={}", userId, e.toString());
            removeIfCurrent(userId, emitter);
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // 무시
            }
            return false;
        }
    }

    /** 현재 연결된 사용자 수 (모니터링/테스트용). */
    public int activeConnectionCount() {
        return emitters.size();
    }

    /** 현재 연결된 사용자 ID 스냅샷 (heartbeat 순회용). */
    public java.util.Set<Long> connectedUserIds() {
        return java.util.Set.copyOf(emitters.keySet());
    }

    /** 맵에 등록된 것이 정확히 이 emitter일 때만 제거(교체 레이스 시 새 연결 보존). */
    private void removeIfCurrent(Long userId, SseEmitter emitter) {
        emitters.remove(userId, emitter);
    }

    /** 애플리케이션 종료 시 모든 연결을 정리한다. */
    @PreDestroy
    public void shutdown() {
        log.info("Completing {} SSE connection(s) on shutdown", emitters.size());
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // 무시
            }
        });
        emitters.clear();
    }
}
