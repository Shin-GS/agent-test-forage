// 채팅 메인 화면 (라우트 "/").
// 개편: 좌측 통합 사이드바는 AppLayout(전역)이 렌더한다. ChatPage 는
//   중앙 채팅 영역 + 우측 사이드 패널(+리사이즈/오버레이)만 담당한다.
//   구조: .chat-layout(가로) > .chat-area + [.resize-handle] + SidePanel
//
// 회귀 방지: 헤더/전역 SSE 구독/ToastContainer/좌측 사이드바는 상위 레이아웃이 담당한다.
// 대화 목록/선택/이름변경/삭제/새채팅은 AppSidebar 로 이동했고, ChatPage 는 전송/중지/
// 레시피 실행과 현재 대화방 메시지 표시에 집중한다. 상태는 전역 chatStore 로 공유한다.

import { useCallback, useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { conversationsApi } from "../api";
import type { MessageResponse } from "../api/types";
import { ChatInput } from "../components/chat/ChatInput";
import { MessageList } from "../components/chat/MessageList";
import { Onboarding } from "../components/chat/Onboarding";
import { SidePanel } from "../features/panel/SidePanel";
import { useChatStore } from "../store/chatStore";
import { useToastStore } from "../store/toastStore";
import { usePanelStore } from "../features/panel/panelStore";
import { clampNumber, useLocalStorageState } from "../hooks/useLocalStorageState";
import { useMediaQuery } from "../hooks/useMediaQuery";

// 새 대화 시작 시 사용할 기본 서비스(apiSpecId). 프로토타입: 하드코딩.
const DEFAULT_API_SPEC_ID = 1;

// 우측 패널 폭 clamp 범위 (tokens: --panel-width-min/max)
const PANEL_WIDTH_MIN = 240;
const PANEL_WIDTH_MAX = 640;
const PANEL_WIDTH_DEFAULT = 320;

// localStorage 키 (우측 패널만 — 좌측 접기는 AppLayout 이 관리)
const LS_RIGHT_COLLAPSED = "testforge.ui.rightPanel.collapsed";
const LS_RIGHT_WIDTH = "testforge.ui.rightPanel.width";

/** 낙관적 임시 사용자 메시지 생성 (음수 seq/id 로 서버 확정 메시지와 구분) */
function optimisticUserMessage(conversationId: number, content: string): MessageResponse {
  const now = Date.now();
  return {
    id: -now,
    conversationId,
    seq: -now,
    role: { code: "USER", description: "사용자" },
    type: { code: "TEXT", description: "텍스트" },
    status: { code: "SENT", description: "전송됨" },
    content,
    metadata: null,
    referenceId: null,
    createdAt: new Date().toISOString(),
  };
}

export function ChatPage() {
  const currentConversationId = useChatStore((state) => state.currentConversationId);
  const messages = useChatStore((state) => state.messages);
  const conversationStatus = useChatStore((state) => state.conversationStatus);

  const setConversations = useChatStore((state) => state.setConversations);
  const setCurrentConversation = useChatStore((state) => state.setCurrentConversation);
  const setMessages = useChatStore((state) => state.setMessages);
  const addMessage = useChatStore((state) => state.addMessage);

  const [error, setError] = useState<string | null>(null);

  const queryClient = useQueryClient();
  const showToast = useToastStore((state) => state.show);

  // ─── UI 상태 (우측 패널만, localStorage 저장/복원) ───
  const [rightCollapsed, setRightCollapsed] = useLocalStorageState<boolean>(LS_RIGHT_COLLAPSED, false);
  const [panelWidth, setPanelWidth] = useLocalStorageState<number>(LS_RIGHT_WIDTH, PANEL_WIDTH_DEFAULT, {
    sanitize: (v) => clampNumber(v, PANEL_WIDTH_MIN, PANEL_WIDTH_MAX, PANEL_WIDTH_DEFAULT),
  });

  // ─── 반응형: <1200px 는 우측 패널 오버레이 ───
  const isTablet = useMediaQuery("(max-width: 1199px)");

  // 오버레이 모드에서 패널 열림 여부 (panelStore.open)
  const panelOpen = usePanelStore((s) => s.open);
  const setPanelOpen = usePanelStore((s) => s.setOpen);

  // 데스크톱(고정 열)에서 열려있던 패널이 <1200 오버레이로 전환될 때, panelStore.open 이
  // false 면 패널이 갑자기 사라진다. 오버레이 진입 시 접힘 상태가 아니면 open 을 승격해
  // 폭 축소만으로 패널이 닫히지 않게 한다.
  useEffect(() => {
    if (isTablet && !rightCollapsed && !panelOpen) {
      setPanelOpen(true);
    }
    // rightCollapsed/panelOpen 은 의도적으로 의존성에서 제외 — 오버레이 진입 시점에만 1회 승격.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isTablet, setPanelOpen]);

  // 리사이즈 드래그 상태
  const [resizing, setResizing] = useState(false);
  const layoutRef = useRef<HTMLDivElement>(null);

  // 채팅 ↔ 패널 연동: executing → idle 전이 시 실행 히스토리 무효화(패널은 구독만)
  const prevStatusRef = useRef(conversationStatus);
  useEffect(() => {
    const prev = prevStatusRef.current;
    prevStatusRef.current = conversationStatus;
    if (prev === "executing" && conversationStatus === "idle") {
      void queryClient.invalidateQueries({ queryKey: ["executions"] });
    }
  }, [conversationStatus, queryClient]);

  // 대화 목록 재조회 (첫 메시지로 새 대화가 생성됐을 때 목록 갱신용).
  const loadConversations = useCallback(async () => {
    try {
      const list = await conversationsApi.listConversations();
      setConversations(list);
    } catch {
      // 목록 갱신 실패는 SSE(session_list_update)로 복구된다
    }
  }, [setConversations]);

  // 전송. referenceId 는 사이드 패널 레시피 실행 시 recipeId(문자열)로 전달된다.
  const handleSend = useCallback(
    async (content: string, referenceId?: string) => {
      setError(null);
      try {
        if (currentConversationId == null) {
          const started = await conversationsApi.startMessage({
            content,
            apiSpecId: DEFAULT_API_SPEC_ID,
            referenceId,
          });
          const newId = started.conversation.id;
          setCurrentConversation(newId);
          setMessages([started.message]);
          try {
            const page = await conversationsApi.listMessages(newId);
            if (page.items.length > 0) {
              setMessages(page.items);
            }
          } catch {
            // 재조회 실패는 무시 — 저장된 첫 메시지 + 후속 SSE 로 복구된다
          }
          await loadConversations();
        } else {
          addMessage(optimisticUserMessage(currentConversationId, content));
          await conversationsApi.sendMessage(currentConversationId, { content, referenceId });
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "메시지 전송에 실패했습니다");
      }
    },
    [currentConversationId, setCurrentConversation, setMessages, addMessage, loadConversations]
  );

  // 실행 중지
  const handleStop = useCallback(async () => {
    if (currentConversationId == null) return;
    setError(null);
    try {
      await conversationsApi.stop(currentConversationId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "실행 중지에 실패했습니다");
    }
  }, [currentConversationId]);

  const isOnboarding = currentConversationId == null && messages.length === 0;

  // 레시피 [▶] 실행: "{name} 실행하기" 발화 + referenceId(=recipeId)
  const handleRunRecipe = useCallback(
    (recipeId: number, recipeName: string) => {
      if (currentConversationId != null && conversationStatus !== "idle") {
        showToast("현재 대화방에 진행 중인 작업이 있어요. 완료 후 다시 시도해주세요.", "warning");
        return;
      }
      void handleSend(`${recipeName} 실행하기`, String(recipeId));
    },
    [handleSend, currentConversationId, conversationStatus, showToast]
  );

  // ─── 리사이즈 드래그 (우측 패널 좌경계) — Pointer 이벤트 + setPointerCapture ───
  const handleResizeStart = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      e.preventDefault();
      const handle = e.currentTarget;
      handle.setPointerCapture(e.pointerId);
      setResizing(true);
      document.body.style.cursor = "col-resize";
      document.body.style.userSelect = "none";

      const onMove = (ev: PointerEvent) => {
        const layout = layoutRef.current;
        if (!layout) return;
        const rect = layout.getBoundingClientRect();
        // 우측 경계에서 포인터까지 거리 = 패널 폭
        const next = rect.right - ev.clientX;
        setPanelWidth(clampNumber(next, PANEL_WIDTH_MIN, PANEL_WIDTH_MAX, PANEL_WIDTH_DEFAULT));
      };
      const onUp = (ev: PointerEvent) => {
        setResizing(false);
        document.body.style.cursor = "";
        document.body.style.userSelect = "";
        try {
          handle.releasePointerCapture(ev.pointerId);
        } catch {
          // capture 가 이미 해제됐으면 무시
        }
        handle.removeEventListener("pointermove", onMove);
        handle.removeEventListener("pointerup", onUp);
        handle.removeEventListener("pointercancel", onUp);
      };

      handle.addEventListener("pointermove", onMove);
      handle.addEventListener("pointerup", onUp);
      handle.addEventListener("pointercancel", onUp);
    },
    [setPanelWidth]
  );

  // 반응형 판정:
  //   - Desktop(>=1200): 저장된 collapsed 상태를 그대로 사용, 고정 열
  //   - Tablet/Mobile(<1200): 우측 패널은 오버레이(자동 접힘 취급)
  const rightIsOverlay = isTablet;
  // Desktop 에서만 완전 접기(hidden) 적용. 오버레이 모드에선 hidden 대신 오버레이로 처리.
  const rightHidden = !rightIsOverlay && rightCollapsed;

  // 우측 패널 인라인 폭: Desktop 펼침 상태에서만 저장 폭 적용
  const panelStyle =
    !rightIsOverlay && !rightHidden ? { width: panelWidth, minWidth: panelWidth } : undefined;

  return (
    <div className="chat-layout" ref={layoutRef}>
      {/* ─── 중앙 채팅 영역 ─── */}
      <div className="chat-area">
        {error && (
          <div role="alert" className="alert alert--error" style={{ margin: "var(--space-3) var(--space-4) 0" }}>
            {error}
          </div>
        )}

        {isOnboarding ? (
          <div className="chat-messages">
            <Onboarding onQuickAction={handleSend} />
          </div>
        ) : (
          <MessageList messages={messages} />
        )}

        <ChatInput status={conversationStatus} onSend={handleSend} onStop={handleStop} />
      </div>

      {/* ─── 리사이즈 핸들 (Desktop 펼침 상태에서만) ─── */}
      {!rightIsOverlay && !rightHidden && (
        <div
          className={`resize-handle${resizing ? " active" : ""}`}
          role="separator"
          aria-orientation="vertical"
          aria-label="사이드 패널 크기 조절"
          onPointerDown={handleResizeStart}
        />
      )}

      {/* 오버레이 모드에서 패널이 닫혀 있으면 열기 버튼 노출 (우측 가장자리 고정) */}
      {rightIsOverlay && !panelOpen && (
        <button
          type="button"
          className="side-panel__reveal"
          style={{
            display: "flex",
            position: "absolute",
            right: 0,
            top: "50%",
            transform: "translateY(-50%)",
            zIndex: 90,
          }}
          aria-label="패널 펼치기"
          onClick={() => setPanelOpen(true)}
        >
          ◀
        </button>
      )}

      {/* 오버레이 모드 열림 시 배경 클릭으로 닫기 (패널 z-dropdown=100 보다 아래) */}
      {rightIsOverlay && panelOpen && (
        <div
          className="modal-backdrop"
          style={{ zIndex: 90 }}
          aria-hidden
          onClick={() => setPanelOpen(false)}
        />
      )}

      {/* ─── 우측 사이드 패널 (.chat-layout 의 직접 자식이어야 오버레이 CSS 적용됨) ─── */}
      <SidePanel
        conversationStatus={conversationStatus}
        onRunRecipe={handleRunRecipe}
        collapsed={rightHidden}
        overlayMode={rightIsOverlay}
        onExpand={() => setRightCollapsed(false)}
        onCollapse={() => setRightCollapsed(true)}
        style={panelStyle}
      />
    </div>
  );
}
