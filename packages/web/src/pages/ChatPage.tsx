// 채팅 메인 화면 (라우트 "/").
// 개편: ChatGPT식 3열 레이아웃.
//   .main-content > .app-sidebar (통합 사이드바) + .chat-area + .resize-handle + SidePanel
// 좌측 사이드바(로고+접기 / +새 채팅 / 메뉴 nav / 대화 목록)와 우측 패널(접기/펼치기/리사이즈)의
// UI 상태는 useLocalStorageState 로 저장/복원한다. 반응형 자동 접기는 useMediaQuery 로 감지한다.
//
// 회귀 방지: 헤더/전역 SSE 구독/ToastContainer 는 상위 레이아웃(AppLayout)이 담당한다.
// 3열 배치와 채팅/실행/SSE 흐름은 기존 로직을 그대로 유지한다.

import { useCallback, useEffect, useRef, useState } from "react";
import { NavLink } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { conversationsApi } from "../api";
import type { MessageResponse } from "../api/types";
import { ChatInput } from "../components/chat/ChatInput";
import { ConversationSidebar } from "../components/chat/ConversationSidebar";
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

// localStorage 키
const LS_LEFT_COLLAPSED = "testforge.ui.leftSidebar.collapsed";
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
  const conversations = useChatStore((state) => state.conversations);
  const currentConversationId = useChatStore((state) => state.currentConversationId);
  const messages = useChatStore((state) => state.messages);
  const conversationStatus = useChatStore((state) => state.conversationStatus);

  const setConversations = useChatStore((state) => state.setConversations);
  const setCurrentConversation = useChatStore((state) => state.setCurrentConversation);
  const setMessages = useChatStore((state) => state.setMessages);
  const addMessage = useChatStore((state) => state.addMessage);
  const clearConversation = useChatStore((state) => state.clearConversation);

  const [error, setError] = useState<string | null>(null);

  const queryClient = useQueryClient();
  const showToast = useToastStore((state) => state.show);

  // ─── UI 상태 (localStorage 저장/복원) ───
  const [leftCollapsed, setLeftCollapsed] = useLocalStorageState<boolean>(LS_LEFT_COLLAPSED, false);
  const [rightCollapsed, setRightCollapsed] = useLocalStorageState<boolean>(LS_RIGHT_COLLAPSED, false);
  const [panelWidth, setPanelWidth] = useLocalStorageState<number>(LS_RIGHT_WIDTH, PANEL_WIDTH_DEFAULT, {
    sanitize: (v) => clampNumber(v, PANEL_WIDTH_MIN, PANEL_WIDTH_MAX, PANEL_WIDTH_DEFAULT),
  });

  // ─── 반응형 자동 접기 (우측 먼저, 그다음 좌측) ───
  const isTablet = useMediaQuery("(max-width: 1199px)");
  const isMobile = useMediaQuery("(max-width: 767px)");

  // 오버레이 모드에서 패널 열림 여부 (panelStore.open)
  const panelOpen = usePanelStore((s) => s.open);
  const setPanelOpen = usePanelStore((s) => s.setOpen);

  // 리사이즈 드래그 상태
  const [resizing, setResizing] = useState(false);
  const mainRef = useRef<HTMLDivElement>(null);

  // 채팅 ↔ 패널 연동: executing → idle 전이 시 실행 히스토리 무효화(패널은 구독만)
  const prevStatusRef = useRef(conversationStatus);
  useEffect(() => {
    const prev = prevStatusRef.current;
    prevStatusRef.current = conversationStatus;
    if (prev === "executing" && conversationStatus === "idle") {
      void queryClient.invalidateQueries({ queryKey: ["executions"] });
    }
  }, [conversationStatus, queryClient]);

  // 대화 목록 로드
  const loadConversations = useCallback(async () => {
    try {
      const list = await conversationsApi.listConversations();
      setConversations(list);
    } catch (err) {
      setError(err instanceof Error ? err.message : "대화 목록을 불러오지 못했습니다");
    }
  }, [setConversations]);

  useEffect(() => {
    void loadConversations();
  }, [loadConversations]);

  // 대화 선택 → 메시지 로드
  const handleSelect = useCallback(
    async (conversationId: number) => {
      setCurrentConversation(conversationId);
      try {
        const page = await conversationsApi.listMessages(conversationId);
        setMessages(page.items);
        await conversationsApi.markRead(conversationId);
      } catch (err) {
        setError(err instanceof Error ? err.message : "메시지를 불러오지 못했습니다");
      }
    },
    [setCurrentConversation, setMessages]
  );

  const handleNew = useCallback(() => {
    clearConversation();
    setError(null);
  }, [clearConversation]);

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

  // 이름 변경 저장 (검증은 사이드바에서 완료). 실패 시 롤백 안내 + 목록 재조회.
  const handleRename = useCallback(
    async (conversationId: number, title: string) => {
      try {
        await conversationsApi.updateTitle(conversationId, title);
        // 낙관적 반영: 목록의 해당 항목 title 갱신
        const updated = conversations.map((c) =>
          c.id === conversationId ? { ...c, title } : c
        );
        setConversations(updated);
      } catch (err) {
        showToast(
          err instanceof Error ? `이름 변경 실패: ${err.message}` : "이름 변경에 실패했습니다",
          "error"
        );
        // 실패 시 서버 상태로 롤백
        void loadConversations();
      }
    },
    [conversations, setConversations, showToast, loadConversations]
  );

  // 삭제 확정
  const handleDelete = useCallback(
    async (conversationId: number) => {
      try {
        await conversationsApi.remove(conversationId);
        // 낙관적 제거
        setConversations(conversations.filter((c) => c.id !== conversationId));
        if (currentConversationId === conversationId) {
          clearConversation();
        }
      } catch (err) {
        showToast(
          err instanceof Error ? `삭제 실패: ${err.message}` : "삭제에 실패했습니다",
          "error"
        );
        void loadConversations();
      }
    },
    [conversations, setConversations, currentConversationId, clearConversation, showToast, loadConversations]
  );

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

  // ─── 리사이즈 드래그 (우측 패널 좌경계) ───
  useEffect(() => {
    if (!resizing) return;
    const onMove = (e: MouseEvent) => {
      const main = mainRef.current;
      if (!main) return;
      const rect = main.getBoundingClientRect();
      // 우측 경계에서 커서까지 거리 = 패널 폭
      const next = rect.right - e.clientX;
      setPanelWidth(clampNumber(next, PANEL_WIDTH_MIN, PANEL_WIDTH_MAX, PANEL_WIDTH_DEFAULT));
    };
    const onUp = () => setResizing(false);
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseup", onUp);
    return () => {
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseup", onUp);
    };
  }, [resizing, setPanelWidth]);

  // 반응형 자동 접기 판정:
  //   - Desktop(>=1200): 저장된 collapsed 상태를 그대로 사용, 고정 열
  //   - Tablet(<1200): 우측 패널은 오버레이(자동 접힘 취급). 저장값 무관하게 고정 열 아님
  //   - Mobile(<768): 좌측 사이드바도 레일
  const rightIsOverlay = isTablet;              // 오버레이 모드 여부
  const leftIsRail = isMobile ? true : leftCollapsed;
  // Desktop 에서만 완전 접기(hidden) 적용. 오버레이 모드에선 hidden 대신 오버레이로 처리.
  const rightHidden = !rightIsOverlay && rightCollapsed;

  // 우측 패널 인라인 폭: Desktop 펼침 상태에서만 저장 폭 적용
  const panelStyle =
    !rightIsOverlay && !rightHidden
      ? { width: panelWidth, minWidth: panelWidth }
      : undefined;

  return (
    <div className="main-content" ref={mainRef}>
      {/* ─── 좌측 통합 사이드바 ─── */}
      <aside className={`app-sidebar${leftIsRail ? " collapsed" : ""}`} aria-label="네비게이션">
        {/* 로고 + 접기 버튼 */}
        <div className="sidebar-brand">
          <span className="sidebar-brand__logo">💬 테스트 채팅</span>
          <button
            type="button"
            className="sidebar-brand__collapse"
            aria-label={leftCollapsed ? "사이드바 펼치기" : "사이드바 접기"}
            aria-expanded={!leftCollapsed}
            onClick={() => setLeftCollapsed((v) => !v)}
          >
            {leftIsRail ? "»" : "«"}
          </button>
        </div>

        {/* + 새 채팅 */}
        <button type="button" className="sidebar-new-chat" onClick={handleNew}>
          <span aria-hidden>➕</span>
          <span className="sidebar-new-chat__label">새 채팅</span>
        </button>

        {/* 메뉴 nav (레시피 / 설정) — 레일에서는 CSS 로 숨김 */}
        <nav className="sidebar-nav" aria-label="페이지 메뉴">
          <NavLink
            to="/recipes"
            className={({ isActive }) => `sidebar-nav__item${isActive ? " active" : ""}`}
          >
            <span className="sidebar-nav__icon" aria-hidden>📋</span>
            <span>레시피 관리</span>
          </NavLink>
          <NavLink
            to="/settings"
            className={({ isActive }) => `sidebar-nav__item${isActive ? " active" : ""}`}
          >
            <span className="sidebar-nav__icon" aria-hidden>⚙️</span>
            <span>설정</span>
          </NavLink>
        </nav>

        {/* 대화 목록 (이 영역만 스크롤) */}
        <ConversationSidebar
          conversations={conversations}
          currentId={currentConversationId}
          onSelect={handleSelect}
          onRename={handleRename}
          onDelete={handleDelete}
        />
      </aside>

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
          onMouseDown={(e) => {
            e.preventDefault();
            setResizing(true);
          }}
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

      {/* ─── 우측 사이드 패널 (main-content 의 직접 자식이어야 오버레이 CSS 적용됨) ─── */}
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
