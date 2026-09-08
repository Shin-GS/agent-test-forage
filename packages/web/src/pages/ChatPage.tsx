// 채팅 메인 화면 (라우트 "/").
// 개편: 좌측 통합 사이드바는 AppLayout(전역)이 렌더한다. ChatPage 는
//   중앙 채팅 영역 + 우측 사이드 패널(+리사이즈/오버레이)만 담당한다.
//   구조: .chat-layout(가로) > .chat-area + [.resize-handle] + SidePanel
//
// 회귀 방지: 헤더/전역 SSE 구독/ToastContainer/좌측 사이드바는 상위 레이아웃이 담당한다.
// 대화 목록/선택/이름변경/삭제/새채팅은 AppSidebar 로 이동했고, ChatPage 는 전송/중지/
// 레시피 실행과 현재 대화방 메시지 표시에 집중한다. 상태는 전역 chatStore 로 공유한다.

import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
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

// 우측 패널 폭 clamp 범위 (tokens: --panel-width-min/max)
const PANEL_WIDTH_MIN = 240;
const PANEL_WIDTH_MAX = 640;
const PANEL_WIDTH_DEFAULT = 320;

// localStorage 키 (우측 패널만 — 좌측 접기는 AppLayout 이 관리)
const LS_RIGHT_COLLAPSED = "testforge.ui.rightPanel.collapsed";
const LS_RIGHT_WIDTH = "testforge.ui.rightPanel.width";

/**
 * 낙관적 임시 사용자 턴 생성 (음수 id 로 서버 확정 턴과 구분).
 * 턴 1개 = TEXT 파트 1개. 확정 message_new(양수 id) 도착 시 store 가 임시 턴을 전부 제거·교체한다.
 */
function optimisticUserMessage(conversationId: number, content: string): MessageResponse {
  const now = Date.now();
  return {
    id: -now,
    conversationId,
    role: { code: "USER", description: "사용자" },
    status: { code: "COMPLETE", description: "완료" },
    referenceId: null,
    clientMessageId: null,
    createdAt: new Date().toISOString(),
    parts: [
      {
        id: -now,
        type: { code: "TEXT", description: "텍스트" },
        status: { code: "COMPLETE", description: "완료" },
        content,
        executionId: null,
        investigationId: null,
        cardType: null,
        payload: null,
        schemaVersion: null,
      },
    ],
  };
}

export function ChatPage() {
  const currentConversationId = useChatStore((state) => state.currentConversationId);
  const messages = useChatStore((state) => state.messages);
  const conversationStatus = useChatStore((state) => state.conversationStatus);
  const conversations = useChatStore((state) => state.conversations);
  const pendingApiSpecId = useChatStore((state) => state.pendingApiSpecId);

  const setCurrentConversation = useChatStore((state) => state.setCurrentConversation);
  const setMessages = useChatStore((state) => state.setMessages);
  const addMessage = useChatStore((state) => state.addMessage);
  const setPendingApiSpecId = useChatStore((state) => state.setPendingApiSpecId);
  const loadConversations = useChatStore((state) => state.loadConversations);
  const clearConversation = useChatStore((state) => state.clearConversation);

  const [error, setError] = useState<string | null>(null);

  const navigate = useNavigate();
  // URL 파라미터가 대화방의 source of truth. 아래 effect 가 URL→store 로 단방향 동기화한다.
  const { conversationId: conversationIdParam } = useParams<{ conversationId?: string }>();

  const queryClient = useQueryClient();
  const showToast = useToastStore((state) => state.show);

  // URL→store 동기화의 "최신 요청 대상" 추적용 ref.
  // StrictMode(dev) 이중 실행이나 빠른 연속 전환에서, 가장 마지막에 시작된 로드만
  // 결과를 반영하도록 하는 sequence guard. cleanup 클로저의 cancelled 플래그와 달리
  // effect 재실행 사이에 값이 유지되어, 1차/2차 실행이 서로의 결과를 버리는 경합을 없앤다.
  const loadRequestRef = useRef(0);

  // ─── URL → store 단방향 동기화 ───
  // URL 의 :conversationId 를 유일한 진입점으로 삼아 현재 대화방을 결정한다.
  // (store→URL 역방향 effect 는 두지 않는다 — 무한 루프 방지.)
  //   - id 있음(유효 숫자): 이미 그 대화가 열려 있고 메시지도 로드돼 있으면 재로드 스킵(깜빡임 방지),
  //     아니면 setCurrentConversation + 메시지 로드 + 읽음 처리. 로드 실패(404/403 등)면 "/" 로 replace + 토스트.
  //   - id 없음("/"): clearConversation → 온보딩 상태.
  useEffect(() => {
    // "/" (딥링크 아님): 온보딩. 단, 방금 새 대화 생성 직후 "/c/:id" 로 전환되기 전
    // 잔여 렌더에서 store 를 지우지 않도록, param 이 없을 때만 초기화한다.
    if (conversationIdParam == null) {
      // 이미 대화가 열려 있고 URL 이 "/" 면 새 채팅으로 초기화
      if (useChatStore.getState().currentConversationId != null) {
        clearConversation();
      }
      return;
    }

    const id = Number(conversationIdParam);
    if (!Number.isInteger(id) || id <= 0) {
      navigate("/", { replace: true });
      showToast("대화를 찾을 수 없습니다", "error");
      return;
    }

    // 재로드 스킵 가드(강화): "이미 그 대화가 열려 있음"만으로는 부족하다.
    // StrictMode 2차 실행 시점엔 currentConversationId===id 이지만 messages 가 아직
    // 비어 있을 수 있는데(1차가 setCurrentConversation 으로 []로 리셋 후 로드 진행 중),
    // 여기서 스킵하면 1차 결과가 cancelled 로 버려져 영영 0개로 남는다.
    // → 열려 있고 && (메시지가 이미 있거나 || 이 요청이 이미 진행 중)일 때만 스킵.
    const state = useChatStore.getState();
    const alreadyOpen = state.currentConversationId === id;
    const alreadyHasMessages = state.messages.length > 0;
    const loadInFlight = loadRequestRef.current !== 0;
    if (alreadyOpen && (alreadyHasMessages || loadInFlight)) {
      return;
    }

    // 이 로드의 시퀀스 번호를 발급. await 완료 시 최신(=마지막 발급)일 때만 반영한다.
    const requestId = ++loadRequestRef.current;
    setCurrentConversation(id);
    void (async () => {
      try {
        const page = await conversationsApi.listMessages(id);
        // 최신 요청이 아니면(그 사이 다른 대화로 전환됨) 무시. StrictMode 이중 실행에서도
        // 최종적으로 마지막 요청의 결과가 반영되므로 messages 가 비지 않는다.
        if (loadRequestRef.current !== requestId) return;
        loadRequestRef.current = 0;
        setMessages(page.items);
        // 읽음 처리는 부수효과. 5xx/네트워크로 실패해도 이미 정상 로드된 대화를
        // 버리면 안 되므로 try 밖(.catch)으로 분리한다(하단 자동 읽음 effect 와 동일 패턴).
        void conversationsApi.markRead(id).catch(() => {
          // 무시 — SSE(session_list_update)/다음 진입/재동기화로 복구
        });
      } catch {
        if (loadRequestRef.current !== requestId) return;
        loadRequestRef.current = 0;
        // listMessages 실패(존재하지 않거나 권한 없는 대화) → 홈으로 돌려보내고 안내
        navigate("/", { replace: true });
        showToast("대화를 찾을 수 없습니다", "error");
      }
    })();
    // conversationIdParam 만 트리거. store 액션/navigate/showToast 는 안정적이라 재실행 유발 안 함.
    // cleanup 은 두지 않는다 — 취소는 loadRequestRef 시퀀스로 대체(경합 제거).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversationIdParam]);

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

  // 대화 목록 재조회(loadConversations)는 store 액션으로 통합됨.
  // 첫 메시지로 새 대화 생성 시 목록 갱신 + SSE 재연결/탭 복귀 재동기화(useSse)가 같은 액션을 공유한다.

  // ─── 현재 보는 방 자동 읽음 처리 ───
  // 지금 열어 보고 있는 대화방(currentConversationId)에 SSE 로 새 메시지가 도착하면
  // 서버가 session_list_update(unread=true)를 내려 목록에 🔵 뱃지가 붙는다. 그러나 사용자가
  // 그 방을 실제로 보고 있으면(탭이 visible) 안 읽음일 이유가 없으므로 자동으로 읽음 처리한다.
  //
  // 조건(사용자 정책):
  //   - conversationId === currentConversationId (지금 보는 방)
  //   - document.visibilityState === "visible" (백그라운드 탭·다른 방 탭 제외)
  //   - 해당 방 unread === true (이미 false 면 스킵 — 불필요한 API 호출/이벤트 폭주 방지)
  // 멱등: 서버 markRead 는 이미 읽음이면 no-op 이라 멀티 탭 동시 호출도 안전.
  // 부수효과(네트워크 + 뷰 가시성 의존)라 store 가 아니라 컴포넌트 effect 에서 처리한다.
  //
  // 목록의 unread 는 store 셀렉터로 직접 구독해, SSE 로 unread 가 true 로 바뀌면 이 effect 가
  // 재실행되도록 한다(메시지 배열 대신 unread 플래그를 트리거로 삼아 과다 실행을 막는다).
  const currentUnread = useChatStore((state) =>
    state.currentConversationId == null
      ? false
      : state.conversations.find((c) => c.id === state.currentConversationId)?.unread ?? false
  );
  useEffect(() => {
    if (currentConversationId == null || !currentUnread) {
      return;
    }

    // 읽음 처리 실행: visible 일 때만. 실패는 삼킨다(다음 진입/재동기화로 복구).
    const markCurrentRead = () => {
      if (document.visibilityState !== "visible") return;
      // 실행 직전 최신 상태 재확인(effect 스케줄 이후 다른 탭이 먼저 읽었을 수 있음)
      const state = useChatStore.getState();
      if (state.currentConversationId !== currentConversationId) return;
      const conv = state.conversations.find((c) => c.id === currentConversationId);
      if (!conv || !conv.unread) return;
      void conversationsApi.markRead(currentConversationId).catch(() => {
        // 무시 — SSE(session_list_update)/다음 진입으로 복구
      });
    };

    // 이미 visible 이면 즉시, 백그라운드였다가 복귀(hidden→visible)하면 그때 읽음 처리.
    markCurrentRead();
    const onVisibility = () => markCurrentRead();
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, [currentConversationId, currentUnread]);

  // 전송. referenceId 는 사이드 패널 레시피 실행 시 recipeId(문자열)로 전달된다.
  const handleSend = useCallback(
    async (content: string, referenceId?: string) => {
      setError(null);
      try {
        if (currentConversationId == null) {
          const started = await conversationsApi.startMessage({
            content,
            // 새 대화 pending 대상 서비스(없으면 null=미지정)
            apiSpecId: pendingApiSpecId,
            referenceId,
          });
          const newId = started.conversation.id;
          setCurrentConversation(newId);
          setMessages([started.message]);
          // 새 대화 URL 로 전환(replace: 온보딩 "/" 를 히스토리에 남기지 않음).
          // store 는 이미 위에서 newId 로 반영됐으므로, URL 동기화 effect 는 id 일치로 재로드를 스킵한다.
          navigate(`/c/${newId}`, { replace: true });
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
    [
      currentConversationId,
      pendingApiSpecId,
      setCurrentConversation,
      setMessages,
      addMessage,
      loadConversations,
      navigate,
    ]
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

  // 현재 대화방 요약(대상 서비스 블록 표시 소스). 목록에서 찾는다.
  const currentConversation =
    currentConversationId != null
      ? conversations.find((c) => c.id === currentConversationId) ?? null
      : null;

  // 기존 대화 서비스 변경 성공 시 목록 배지 반영은 BE 의 session_list_update → 목록 재조회가
  // 담당한다(대화방 목록은 낙관적 UI 대상이 아님 — messaging.md. 목록의 진실은 서버).

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
      {/* 더블클릭 시 기본폭(PANEL_WIDTH_DEFAULT)으로 리셋 */}
      {!rightIsOverlay && !rightHidden && (
        <div
          className={`resize-handle${resizing ? " active" : ""}`}
          role="separator"
          aria-orientation="vertical"
          aria-label="사이드 패널 크기 조절 (더블클릭 시 기본 폭으로 리셋)"
          onPointerDown={handleResizeStart}
          onDoubleClick={() => setPanelWidth(PANEL_WIDTH_DEFAULT)}
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
        conversationId={currentConversationId}
        conversationApiSpecId={currentConversation?.apiSpecId ?? null}
        conversationServiceName={currentConversation?.serviceName ?? null}
        pendingApiSpecId={pendingApiSpecId}
        onChangePendingService={setPendingApiSpecId}
      />
    </div>
  );
}
