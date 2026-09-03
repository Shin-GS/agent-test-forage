// 메시지 리스트 (디자인 명세 chat.html .chat-messages > .messages-container).
// store.messages(seq 오름차순)를 렌더하고 최신 메시지로 스크롤.
// 실행 진행 상태(executionProgress)가 있으면 하단에 ProgressSteps 를 함께 노출.

import { useEffect, useRef } from "react";
import type { MessageResponse } from "../../api/types";
import { useChatStore } from "../../store/chatStore";
import { AuthRequiredCard } from "./AuthRequiredCard";
import { ProgressSteps } from "./ProgressSteps";
import { MessageItem } from "./MessageItem";

interface Props {
  messages: MessageResponse[];
}

export function MessageList({ messages }: Props) {
  const endRef = useRef<HTMLDivElement>(null);
  const executionProgress = useChatStore((state) => state.executionProgress);
  const authPause = useChatStore((state) => state.authPause);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages.length, executionProgress, authPause]);

  return (
    <div className="chat-messages">
      <div className="messages-container">
        {messages.map((message) => (
          <MessageItem key={`${message.id}-${message.seq}`} message={message} />
        ))}

        {executionProgress && (
          <div className="message message--ai">
            <div className="message__avatar">🤖</div>
            <div className="message__content" style={{ maxWidth: "100%", flex: 1 }}>
              <ProgressSteps progress={executionProgress} />
            </div>
          </div>
        )}

        {/* 인증 필요(401/403) 안내 — 있으면 표시(현재 대화방 것만) */}
        <AuthRequiredCard />

        <div ref={endRef} />
      </div>
    </div>
  );
}
