// 메시지 리스트 (디자인 명세 chat.html .chat-messages > .messages-container).
// store.messages(seq 오름차순)를 렌더하고 최신 메시지로 스크롤.
// 진행/결과 블록은 messages 안의 PROGRESS/RESULT 메시지로 MessageItem 이 렌더한다
// (별도 store 상태 없음 — 새로고침 시 메시지 로드만으로 복원됨).

import { useEffect, useRef } from "react";
import type { MessageResponse } from "../../api/types";
import { useChatStore } from "../../store/chatStore";
import { ActionPicker } from "./ActionPicker";
import { AuthRequiredCard } from "./AuthRequiredCard";
import { MessageItem } from "./MessageItem";

interface Props {
  messages: MessageResponse[];
}

export function MessageList({ messages }: Props) {
  const endRef = useRef<HTMLDivElement>(null);
  const authPause = useChatStore((state) => state.authPause);
  const actionPicker = useChatStore((state) => state.actionPicker);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages.length, authPause, actionPicker]);

  return (
    <div className="chat-messages">
      <div className="messages-container">
        {messages.map((message) => (
          <MessageItem key={`${message.id}-${message.seq}`} message={message} />
        ))}

        {/* 인증 필요(401/403) 안내 — 있으면 표시(현재 대화방 것만) */}
        <AuthRequiredCard />

        {/* 액션 피커 — 필수 입력 미충족 시 표시(현재 대화방 것만). 실행마다 새 인스턴스 */}
        {actionPicker && <ActionPicker key={actionPicker.executionId} />}

        <div ref={endRef} />
      </div>
    </div>
  );
}
