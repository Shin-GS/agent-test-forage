// 채팅 메인 화면.
// 레이아웃(디자인 명세 docs/design/web/chat.html 준수):
//   .app-container > .app-header + .tab-nav + .main-content(.sidebar + .chat-area)
// - 마운트 시 useSse() 로 전역 SSE 구독 시작 + 대화 목록 로드
// - 대화 선택 시 메시지 로드
// - 전송: 현재 대화방 없으면 startMessage 로 새 대화 생성, 있으면 sendMessage
//   AI 응답은 SSE(message_new)로 도착하므로 POST 응답에서 기대하지 않는다
// - 우측 사이드 패널(features/panel)은 홈/레시피/히스토리 3탭의 독립 모듈 (Desktop 항상 열림)
//   실행 완료(executing→idle) 시 executions 쿼리를 무효화해 히스토리를 갱신한다

import { useCallback, useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { conversationsApi } from "./api";
import type { MessageResponse } from "./api/types";
import { ChatInput } from "./components/chat/ChatInput";
import { ConversationSidebar } from "./components/chat/ConversationSidebar";
import { MessageList } from "./components/chat/MessageList";
import { Onboarding } from "./components/chat/Onboarding";
import { ToastContainer } from "./components/common/ToastContainer";
import { SidePanel } from "./features/panel/SidePanel";
import { useSse } from "./hooks/useSse";
import { useChatStore } from "./store/chatStore";
import { useToastStore } from "./store/toastStore";

// 새 대화 시작 시 사용할 기본 서비스(apiSpecId).
// 프로토타입: 하드코딩. 실제로는 상단 서비스 선택 UI 로 대체 예정.
const DEFAULT_API_SPEC_ID = 1;

// 상단 탭 정의 (디자인 명세 tab-nav). 채팅 외 탭은 후속 화면 — 자리표시.
const TABS = [
  { key: "chat", label: "💬 채팅", active: true },
  { key: "subdomain", label: "📡 서브도메인", active: false },
  { key: "recipe", label: "📋 레시피", active: false },
  { key: "settings", label: "⚙️ 설정", active: false },
] as const;

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

function App() {
  const userId = useChatStore((state) => state.userId);
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

  // 전역 SSE 구독
  useSse();

  // 채팅 ↔ 패널 연동: 대화방 상태가 executing → idle 로 전이하면 실행 완료로 보고
  // 패널의 실행 히스토리 쿼리를 무효화한다(패널은 구독만).
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
      const list = await conversationsApi.listConversations(userId);
      setConversations(list);
    } catch (err) {
      setError(err instanceof Error ? err.message : "대화 목록을 불러오지 못했습니다");
    }
  }, [userId, setConversations]);

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
          // 새 대화 생성 + 첫 메시지
          const started = await conversationsApi.startMessage({
            userId,
            content,
            apiSpecId: DEFAULT_API_SPEC_ID,
            referenceId,
          });
          // 응답 형태: { accepted, conversation, message }. 대화방 id 는 conversation.id.
          const newId = started.conversation.id;
          // currentConversationId 를 먼저 확정한다. 이 시점 이후 도착하는 SSE message_new 가
          // 스토어 필터를 통과하도록 한다(경쟁 방지). setCurrentConversation 이 messages 를 비우므로,
          // 곧바로 서버에서 현재까지의 메시지를 재조회해 채운다(POST 응답보다 먼저 온 AI 응답 유실 방지).
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
          await conversationsApi.sendMessage(currentConversationId, { userId, content, referenceId });
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : "메시지 전송에 실패했습니다");
      }
    },
    [currentConversationId, userId, setCurrentConversation, addMessage, loadConversations]
  );

  // 실행 중지: 현재 대화방의 진행 실행을 서버에 중지 요청한다(상태/진행은 SSE 로 반영).
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

  // 레시피 [▶] 실행: "{name} 실행하기" 발화 + referenceId(=recipeId) 로 채팅 flow 를 탄다.
  const handleRunRecipe = useCallback(
    (recipeId: number, recipeName: string) => {
      // 대화방 처리 중이면 실행 진입을 막고 안내(대화방 단위 락). 새 대화(null)면 통과.
      if (currentConversationId != null && conversationStatus !== "idle") {
        showToast("현재 대화방에 진행 중인 작업이 있어요. 완료 후 다시 시도해주세요.", "warning");
        return;
      }
      void handleSend(`${recipeName} 실행하기`, String(recipeId));
    },
    [handleSend, currentConversationId, conversationStatus, showToast]
  );

  return (
    <div className="app-container">
      {/* 상단 헤더: 로고 + 워크스페이스 + 아바타 */}
      <header className="app-header">
        <div className="app-header__logo">🔨 AI Test Forge</div>
        <div className="app-header__workspace">🌐 demo-shop ▾</div>
        <div className="app-header__avatar">KS</div>
      </header>

      {/* 탭 내비게이션 */}
      <nav className="tab-nav">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            className={`tab-nav__item${tab.active ? " active" : ""}`}
            aria-current={tab.active ? "page" : undefined}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      {/* 본문: 사이드바 + 대화 영역 */}
      <div className="main-content">
        <ConversationSidebar
          conversations={conversations}
          currentId={currentConversationId}
          onSelect={handleSelect}
          onNew={handleNew}
        />

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

        <SidePanel
          userId={userId}
          conversationStatus={conversationStatus}
          onRunRecipe={handleRunRecipe}
        />
      </div>

      {/* 인앱 토스트 (대화방 락 안내 등) */}
      <ToastContainer />
    </div>
  );
}

export default App;
