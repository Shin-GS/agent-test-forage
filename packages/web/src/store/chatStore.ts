// 채팅/대화방 전역 스토어 (Zustand).
// - 현재 사용자, 현재 대화방, 대화방 목록, 메시지 목록, 대화방 런타임 상태, 실행 진행 상태 관리
// - SSE 이벤트를 받아 상태를 갱신하는 액션 제공 (useSse 훅에서 라우팅)

import { create } from "zustand";
import type { ActionPickerVariable, ConversationSummary, MessageResponse, StatusView } from "../api/types";
import type { ConversationRuntimeStatus } from "./types";

// auth 미구현 — 임시 하드코딩 사용자
const DEFAULT_USER_ID = 1;

// ---------------------------------------------------------------------------
// SSE 이벤트 payload (envelope.data) 형태
// 백엔드가 확정되면 좁힐 수 있으나, 현재는 스토어가 필요로 하는 필드만 명시한다.
// ---------------------------------------------------------------------------

/**
 * session_status 이벤트 data (messaging.md): { sessionId, status }.
 * status 는 StatusView({code, description}) 로 내려온다(문자열 아님).
 */
export interface SessionStatusPayload {
  sessionId: number;
  status: StatusView;
}

/**
 * session_list_update 이벤트 data (messaging.md): 목록 "한 줄" 갱신.
 * BE 계약: { op: "upsert" | "removed", conversation: ConversationListSnapshot }
 * - upsert: 스냅샷 필드로 해당 대화방 한 줄을 추가/갱신
 * - removed: conversation.id 만 채워짐 → 목록에서 제거
 */
export interface ConversationListSnapshot {
  id: number;
  title: string | null;
  apiSpecId: number | null;
  status: StatusView | null;
  lastMessageAt: string | null;
  unread: boolean;
  updatedAt: string | null;
}

export interface SessionListUpdatePayload {
  op: "upsert" | "removed";
  conversation: ConversationListSnapshot;
}

/**
 * 액션 피커 대기 상태 (실행 시작 시 필수 입력 미충족). 액션 피커 컴포넌트가 이 상태를 읽어
 * 입력 폼을 렌더하고, 제출 시 respond API 로 값을 보낸 뒤 실행을 재개한다.
 * execution 은 재개(runExecution)에 필요한 런타임 객체라 직렬화하지 않고 메모리에만 둔다.
 */
export interface ActionPickerState {
  conversationId: number;
  executionId: number;
  /** pre-run 수집이면 -1 */
  stepIndex: number;
  variables: ActionPickerVariable[];
  mode: string;
}

// ---------------------------------------------------------------------------
// State + Actions
// ---------------------------------------------------------------------------

/**
 * 인증 대기 상태 (레시피 실행 중 401/403). 인증 안내 카드가 이 상태를 읽어
 * 로그인 링크와 "계속 진행"(재개) 버튼을 렌더한다. execution/resumeState 는 재개에 필요한
 * 런타임 객체라 직렬화하지 않고 메모리에만 둔다.
 */
export interface AuthPauseState {
  conversationId: number;
  httpStatus: number;
  /** 인증이 필요한 서비스 이름 (스펙 name) */
  serviceName: string | null;
  /** 로그인 안내 링크 [{name, loginPageUrl}] */
  loginProfiles: { name: string; loginPageUrl: string }[];
  /** 재개에 필요한 실행/상태 (executionRunner 로 전달) */
  execution: unknown;
  resumeState: unknown;
  mode: string;
}

interface ChatState {
  userId: number;
  currentConversationId: number | null;
  conversations: ConversationSummary[];
  messages: MessageResponse[];
  conversationStatus: ConversationRuntimeStatus;
  /** 인증 대기 상태 (있으면 인증 안내 카드 표시) */
  authPause: AuthPauseState | null;
  /** 액션 피커 대기 상태 (있으면 입력 폼 표시) */
  actionPicker: ActionPickerState | null;

  // --- 일반 액션 ---
  setCurrentConversation: (conversationId: number | null) => void;
  setConversations: (conversations: ConversationSummary[]) => void;
  setMessages: (messages: MessageResponse[]) => void;
  /** 낙관적 임시 메시지 추가 (음수 seq 등으로 구분) */
  addMessage: (message: MessageResponse) => void;
  clearConversation: () => void;
  /** 인증 대기 상태 설정/해제 */
  setAuthPause: (pause: AuthPauseState | null) => void;
  /** 액션 피커 대기 상태 설정/해제 */
  setActionPicker: (picker: ActionPickerState | null) => void;

  // --- SSE 라우팅 액션 ---
  onMessageNew: (message: MessageResponse) => void;
  onMessageUpdate: (message: MessageResponse) => void;
  onSessionStatus: (payload: SessionStatusPayload) => void;
  onSessionListUpdate: (payload: SessionListUpdatePayload) => void;
}

/** 런타임 상태(StatusView 또는 문자열) → FE 상태 머신 매핑 */
function mapRuntimeStatus(status: StatusView | string): ConversationRuntimeStatus {
  const code = (typeof status === "string" ? status : status?.code ?? "").toUpperCase();
  switch (code) {
    case "AI_RESPONDING":
      return "ai_responding";
    case "EXECUTING":
      return "executing";
    case "INPUT_WAITING":
    case "WAITING_INPUT":
      return "input_waiting";
    default:
      return "idle";
  }
}

/**
 * seq 오름차순 정렬 + seq 중복 제거.
 * 같은 seq 가 여러 개면 뒤에 들어온(=서버 확정) 메시지를 우선한다.
 * 임시 메시지는 seq 를 음수로 두어, 서버 메시지가 오면 자연스럽게 대체되도록 한다.
 */
function normalizeMessages(messages: MessageResponse[]): MessageResponse[] {
  const bySeq = new Map<number, MessageResponse>();
  for (const message of messages) {
    bySeq.set(message.seq, message);
  }
  return Array.from(bySeq.values()).sort((a, b) => a.seq - b.seq);
}

export const useChatStore = create<ChatState>((set) => ({
  userId: DEFAULT_USER_ID,
  currentConversationId: null,
  conversations: [],
  messages: [],
  conversationStatus: "idle",
  authPause: null,
  actionPicker: null,

  setCurrentConversation: (conversationId) =>
    set({
      currentConversationId: conversationId,
      messages: [],
      conversationStatus: "idle",
      authPause: null,
      actionPicker: null,
    }),

  setConversations: (conversations) => set({ conversations }),

  setMessages: (messages) => set({ messages: normalizeMessages(messages) }),

  setAuthPause: (pause) => set({ authPause: pause }),

  setActionPicker: (picker) => set({ actionPicker: picker }),

  addMessage: (message) =>
    set((state) => ({ messages: normalizeMessages([...state.messages, message]) })),

  clearConversation: () =>
    set({
      currentConversationId: null,
      messages: [],
      conversationStatus: "idle",
      authPause: null,
      actionPicker: null,
    }),

  // --- SSE 라우팅 ---

  onMessageNew: (message) =>
    set((state) => {
      // 다른 대화방 이벤트는 목록의 unread 등에만 영향 (여기선 현재 방만 반영)
      if (message.conversationId !== state.currentConversationId) {
        return {};
      }
      return { messages: normalizeMessages([...state.messages, message]) };
    }),

  onMessageUpdate: (message) =>
    set((state) => {
      if (message.conversationId !== state.currentConversationId) {
        return {};
      }
      const next = state.messages.filter((m) => m.id !== message.id);
      return { messages: normalizeMessages([...next, message]) };
    }),

  onSessionStatus: (payload) =>
    set((state) => {
      // BE 계약: { sessionId, status: StatusView }
      if (payload.sessionId !== state.currentConversationId) {
        return {};
      }
      return { conversationStatus: mapRuntimeStatus(payload.status) };
    }),

  onSessionListUpdate: (payload) =>
    set((state) => {
      // BE 계약: { op: "upsert" | "removed", conversation: ConversationListSnapshot }
      // 방어: payload/conversation 이 없으면 목록을 건드리지 않는다(undefined 로 덮어 크래시 방지).
      const snap = payload?.conversation;
      if (!snap || snap.id == null) {
        return {};
      }

      if (payload.op === "removed") {
        return { conversations: state.conversations.filter((c) => c.id !== snap.id) };
      }

      // upsert: 기존 항목이면 스냅샷 필드로 병합, 없으면 새 요약으로 추가.
      const existing = state.conversations.find((c) => c.id === snap.id);
      const merged: ConversationSummary = existing
        ? {
            ...existing,
            title: snap.title ?? existing.title,
            apiSpecId: snap.apiSpecId ?? existing.apiSpecId,
            status: snap.status ?? existing.status,
            lastMessageAt: snap.lastMessageAt ?? existing.lastMessageAt,
            unread: snap.unread,
            updatedAt: snap.updatedAt ?? existing.updatedAt,
          }
        : {
            id: snap.id,
            userId: state.userId,
            title: snap.title,
            apiSpecId: snap.apiSpecId,
            status: snap.status ?? { code: "IDLE", description: "" },
            lastMessageAt: snap.lastMessageAt,
            lastReadAt: null,
            unread: snap.unread,
            createdAt: snap.updatedAt ?? new Date().toISOString(),
            updatedAt: snap.updatedAt ?? new Date().toISOString(),
          };

      const others = state.conversations.filter((c) => c.id !== snap.id);
      const next = [merged, ...others];
      // 최근 갱신 순 정렬(내림차순). updatedAt 없으면 뒤로.
      next.sort((a, b) => {
        const ta = a.updatedAt ? Date.parse(a.updatedAt) : 0;
        const tb = b.updatedAt ? Date.parse(b.updatedAt) : 0;
        return tb - ta;
      });
      return { conversations: next };
    }),

}));
