// Global SSE 구독 훅.
// - GET {API_BASE}/sse/connect 를 EventSource 로 구독 (사용자는 세션 쿠키로 식별)
// - withCredentials:true 로 세션 쿠키를 실어 서버가 사용자를 도출한다
// - 백엔드는 named event(SseEmitter.event().name("message_new") 등)로 전송하므로
//   각 이벤트명마다 addEventListener 로 개별 리스너를 등록해야 한다.
//   (기본 "message" 리스너로는 named event 를 받지 못한다 — 이전 버그)
// - heartbeat 는 SSE comment(":")로 오므로 이벤트로 도착하지 않아 별도 처리 불필요
// - 재연결/Last-Event-ID 는 브라우저 EventSource 기본 동작 사용

import { useEffect } from "react";
import { useNavigate, type NavigateFunction } from "react-router-dom";
import { API_BASE } from "../api/client";
import type { SseEnvelope } from "../api/types";
import { useChatStore } from "../store/chatStore";
import { useAuthStore } from "../store/authStore";
import { useToastStore } from "../store/toastStore";

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
  "session_deleted",
] as const;

/**
 * 전역 SSE 연결을 열고, 수신 이벤트를 chatStore 액션으로 라우팅한다.
 * 사용자는 세션 쿠키(withCredentials)로 서버가 식별한다.
 * 인증(authenticated) 상태에서만 연결하며, 로그인/로그아웃 시 재연결한다.
 */
export function useSse(options: UseSseOptions = {}): void {
  const { enabled = true } = options;
  const authStatus = useAuthStore((state) => state.status);
  const navigate = useNavigate();

  useEffect(() => {
    if (!enabled || authStatus !== "authenticated") {
      return;
    }

    const url = `${API_BASE}/sse/connect`;
    const source = new EventSource(url, { withCredentials: true });

    const handleEvent = (event: MessageEvent<string>) => {
      let envelope: SseEnvelope;
      try {
        envelope = JSON.parse(event.data) as SseEnvelope;
      } catch {
        return;
      }
      routeEnvelope(envelope, navigate);
    };

    // SSE 연결/재연결 시 목록 재동기화.
    // 재연결 도중 놓친 session_list_update(SIGNAL, 유실 시 재조회로 복구)를 보정한다.
    // Last-Event-ID replay 로도 복구되지만, replay 버퍼(5분) 초과·최초 연결 실패 후 복구 등을
    // 안전하게 커버하기 위해 연결이 열릴 때마다 목록을 한 번 재조회한다.
    const handleOpen = () => {
      void useChatStore.getState().loadConversations();
    };
    source.addEventListener("open", handleOpen);

    // 탭 복귀(백그라운드 → visible) 시 목록 재동기화.
    // 백그라운드에서 브라우저가 EventSource 를 스로틀/중단했을 수 있어, 복귀 시 최신 목록을 1회 당겨온다.
    const handleVisibility = () => {
      if (document.visibilityState === "visible") {
        void useChatStore.getState().loadConversations();
      }
    };
    document.addEventListener("visibilitychange", handleVisibility);

    // named event 별 리스너 등록.
    // envelope.type 필드로도 라우팅하지만, 리스너는 이벤트명 기준으로 걸어야 수신된다.
    for (const eventName of SSE_EVENT_TYPES) {
      source.addEventListener(eventName, handleEvent as EventListener);
    }

    return () => {
      source.removeEventListener("open", handleOpen);
      document.removeEventListener("visibilitychange", handleVisibility);
      for (const eventName of SSE_EVENT_TYPES) {
        source.removeEventListener(eventName, handleEvent as EventListener);
      }
      source.close();
    };
    // enabled/인증 상태 변경 시 재연결
  }, [enabled, authStatus, navigate]);
}

/** envelope.type 에 따라 스토어 액션 호출 */
function routeEnvelope(envelope: SseEnvelope, navigate: NavigateFunction): void {
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

    case "session_deleted": {
      // BE 계약(SessionDeletedPayload): { conversationId }.
      // 보고 있던 방이 삭제됐으면 홈으로 이탈 + 안내. 그 뒤 목록을 재조회해 삭제된 방을 제거한다.
      // 본인이 이 탭에서 직접 삭제한 경우엔 AppSidebar.handleDelete 가 삭제 API 호출 "전에"
      // clearConversation 을 실행하므로 currentConversationId 가 이미 null 이라 아래 조건에
      // 안 걸린다(다른 탭에서 삭제된 경우에만 이탈/안내 — 자연 구분).
      const deletedId: number | undefined = data?.conversationId;
      if (deletedId != null && deletedId === store.currentConversationId) {
        navigate("/");
        useToastStore.getState().show("보고 있던 대화가 삭제되었어요.", "info");
      }
      void store.loadConversations();
      return;
    }

    default:
      // 알 수 없는 타입은 무시 (전방 호환)
      return;
  }
}
