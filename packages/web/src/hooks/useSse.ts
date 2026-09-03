// Global SSE 구독 훅.
// - GET {API_BASE}/sse/connect?userId={userId} 를 EventSource 로 구독
// - EventSource 는 커스텀 헤더를 실을 수 없으므로 userId 를 쿼리로 전달
// - 백엔드는 named event(SseEmitter.event().name("message_new") 등)로 전송하므로
//   각 이벤트명마다 addEventListener 로 개별 리스너를 등록해야 한다.
//   (기본 "message" 리스너로는 named event 를 받지 못한다 — 이전 버그)
// - heartbeat 는 SSE comment(":")로 오므로 이벤트로 도착하지 않아 별도 처리 불필요
// - 재연결/Last-Event-ID 는 브라우저 EventSource 기본 동작 사용

import { useEffect } from "react";
import { API_BASE } from "../api/client";
import type { SseEnvelope } from "../api/types";
import { useChatStore } from "../store/chatStore";

interface UseSseOptions {
  /** false 면 구독하지 않음 (예: 로그인 전) */
  enabled?: boolean;
}

/** 백엔드가 name() 으로 보내는 이벤트 타입 목록 */
const SSE_EVENT_TYPES = [
  "message_new",
  "message_update",
  "session_status",
  "session_list_update",
] as const;

/**
 * 전역 SSE 연결을 열고, 수신 이벤트를 chatStore 액션으로 라우팅한다.
 * userId 는 스토어에서 가져온다.
 */
export function useSse(options: UseSseOptions = {}): void {
  const { enabled = true } = options;
  const userId = useChatStore((state) => state.userId);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    const url = `${API_BASE}/sse/connect?userId=${encodeURIComponent(String(userId))}`;
    const source = new EventSource(url, { withCredentials: true });

    const handleEvent = (event: MessageEvent<string>) => {
      let envelope: SseEnvelope;
      try {
        envelope = JSON.parse(event.data) as SseEnvelope;
      } catch {
        return;
      }
      routeEnvelope(envelope);
    };

    // named event 별 리스너 등록.
    // envelope.type 필드로도 라우팅하지만, 리스너는 이벤트명 기준으로 걸어야 수신된다.
    for (const eventName of SSE_EVENT_TYPES) {
      source.addEventListener(eventName, handleEvent as EventListener);
    }

    return () => {
      for (const eventName of SSE_EVENT_TYPES) {
        source.removeEventListener(eventName, handleEvent as EventListener);
      }
      source.close();
    };
    // userId/enabled 변경 시 재연결
  }, [enabled, userId]);
}

/** envelope.type 에 따라 스토어 액션 호출 */
function routeEnvelope(envelope: SseEnvelope): void {
  const store = useChatStore.getState();
  const data = envelope.data;

  switch (envelope.type) {
    case "heartbeat":
      // 무시 (일반적으로 SSE comment 로 오므로 여기 도달하지 않음)
      return;

    case "message_new":
      store.onMessageNew(data);
      return;

    case "message_update":
      // BE 계약(MessageUpdatePayload): { sessionId, messageId, message }.
      // 실제 갱신 대상은 래퍼 안의 message(전체 스냅샷)이므로 언랩해서 넘긴다.
      store.onMessageUpdate(data?.message ?? data);
      return;

    case "session_status":
      store.onSessionStatus(data);
      return;

    case "session_list_update":
      store.onSessionListUpdate(data);
      return;

    default:
      // 알 수 없는 타입은 무시 (전방 호환)
      return;
  }
}
