// 메시지 리스트 (디자인 명세 chat.html .chat-messages > .messages-container).
// store.messages(seq 오름차순)를 렌더하고 최신 메시지로 스크롤.
// 진행/결과 블록은 messages 안의 PROGRESS/RESULT 메시지로 MessageItem 이 렌더한다
// (별도 store 상태 없음 — 새로고침 시 메시지 로드만으로 복원됨).
//
// 자동 스크롤 정책 (표준 채팅 UX):
//  - 새 메시지/업데이트 도착 시: 사용자가 "바닥 근처"에 있을 때만 자동으로 바닥으로 따라간다.
//    위로 스크롤해 과거 메시지를 읽는 중이면 튕기지 않는다.
//  - 대화방 전환 시: 애니메이션 없이 즉시 바닥으로 이동한다.

import { useEffect, useLayoutEffect, useRef } from "react";
import type { MessageResponse } from "../../api/types";
import { useChatStore } from "../../store/chatStore";
import { ActionPicker } from "./ActionPicker";
import { AuthRequiredCard } from "./AuthRequiredCard";
import { MessageItem } from "./MessageItem";

interface Props {
  messages: MessageResponse[];
}

// 바닥으로 간주할 임계값(px). 이 거리 이내면 "바닥에 붙어 있음"으로 본다.
const NEAR_BOTTOM_THRESHOLD = 80;

export function MessageList({ messages }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const authPause = useChatStore((state) => state.authPause);
  const actionPicker = useChatStore((state) => state.actionPicker);
  const currentConversationId = useChatStore((state) => state.currentConversationId);

  // 직전에 렌더한 대화방 id. 값이 바뀌면 "대화방 전환"으로 판단한다.
  const prevConversationIdRef = useRef<number | null>(null);
  // 대화방 전환 후 "아직 바닥으로 못 보냈다"는 대기 플래그.
  // 전환 시점엔 messages 가 비어 있고(store 가 초기화 후 비동기 로드) 높이가 확정되지 않으므로,
  // 실제 메시지가 렌더되어 높이가 잡힐 때까지 유지했다가 바닥으로 보낸다.
  const pendingScrollToBottomRef = useRef(false);
  // "사용자가 바닥에 붙어 있는가" 상태. 사용자의 실제 스크롤 조작으로만 갱신한다.
  // 메시지 추가 시엔 이 값을 신뢰해 바닥으로 보낸다(스크롤 진행 중 위치 재측정으로 인한 경합 방지).
  const stickToBottomRef = useRef(true);

  const scrollToBottom = (smooth: boolean) => {
    const el = containerRef.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? "smooth" : "auto" });
  };

  // 사용자의 스크롤 조작을 추적해 stick-to-bottom 상태를 갱신한다.
  const handleScroll = () => {
    const el = containerRef.current;
    if (!el) return;
    // 전환으로 인한 프로그램적 스크롤이 진행 중이면 사용자 의도로 오인하지 않는다.
    if (pendingScrollToBottomRef.current) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    stickToBottomRef.current = distanceFromBottom <= NEAR_BOTTOM_THRESHOLD;
  };

  // 대화방 전환 감지: 전환이면 대기 플래그를 세우고 바닥 고정 상태로 초기화한다.
  if (prevConversationIdRef.current !== currentConversationId) {
    prevConversationIdRef.current = currentConversationId;
    pendingScrollToBottomRef.current = true;
    stickToBottomRef.current = true;
  }

  // 대화방 전환 직후: 레이아웃 커밋 후 애니메이션 없이 즉시 바닥으로.
  // messages 가 채워져 높이가 잡히면 대기 플래그를 소비한다.
  useLayoutEffect(() => {
    if (!pendingScrollToBottomRef.current) return;
    scrollToBottom(false);
    // 메시지가 실제로 렌더된 뒤에만 완료 처리(빈 상태에서 미리 소비하지 않도록).
    if (messages.length > 0) {
      pendingScrollToBottomRef.current = false;
    }
  }, [currentConversationId, messages.length]);

  // 새 메시지/업데이트/피커 변화: 바닥에 붙어 있던 경우에만 따라간다.
  // (대화방 전환으로 인한 첫 이동은 위 layout effect 가 담당하므로 여기선 스킵.)
  useEffect(() => {
    if (pendingScrollToBottomRef.current) return;
    if (stickToBottomRef.current) {
      scrollToBottom(true);
    }
  }, [messages.length, authPause, actionPicker]);

  return (
    <div className="chat-messages" ref={containerRef} onScroll={handleScroll}>
      <div className="messages-container">
        {messages.map((message) => (
          <MessageItem key={`${message.id}-${message.seq}`} message={message} />
        ))}

        {/* 인증 필요(401/403) 안내 — 있으면 표시(현재 대화방 것만) */}
        <AuthRequiredCard />

        {/* 액션 피커 — 필수 입력 미충족 시 표시(현재 대화방 것만). 실행마다 새 인스턴스 */}
        {actionPicker && <ActionPicker key={actionPicker.executionId} />}
      </div>
    </div>
  );
}
