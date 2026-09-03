// 채팅 입력창 (디자인 명세 chat.html .chat-input-area).
// - conversationStatus 로 잠금/안내 처리
//   idle: 입력 가능
//   ai_responding: 잠금 + "AI가 응답 중입니다"
//   executing: 잠금 + "레시피 실행 중입니다" + [중지] 버튼
//   input_waiting: 잠금(자유채팅 불가, 액션 피커로만 입력) + 안내 문구
// - Enter 전송 / Shift+Enter 줄바꿈
// - 구조: .chat-input-wrapper > textarea.chat-input + button.btn.btn--primary

import { useState } from "react";
import type { ConversationRuntimeStatus } from "../../store/types";

interface Props {
  status: ConversationRuntimeStatus;
  onSend: (content: string) => void;
  /** executing 상태에서 실행 중지 요청 */
  onStop?: () => void;
}

const STATUS_TEXT: Record<ConversationRuntimeStatus, { locked: boolean; hint: string; placeholder: string }> = {
  idle: { locked: false, hint: "", placeholder: "메시지를 입력하세요..." },
  ai_responding: { locked: true, hint: "AI가 응답 중입니다", placeholder: "AI 응답 대기 중..." },
  executing: { locked: true, hint: "레시피 실행 중입니다", placeholder: "실행 중에는 입력할 수 없습니다" },
  // 기획(execution.md): 입력 대기 중에는 자유 채팅을 잠그고 액션 피커로만 값을 받는다.
  input_waiting: { locked: true, hint: "입력을 기다리고 있습니다 (아래에서 값을 입력하세요)", placeholder: "액션 피커로 입력하세요" },
};

export function ChatInput({ status, onSend, onStop }: Props) {
  const [value, setValue] = useState("");
  const meta = STATUS_TEXT[status];
  const canSend = !meta.locked && value.trim().length > 0;
  const executing = status === "executing";

  const submit = () => {
    if (!canSend) return;
    onSend(value.trim());
    setValue("");
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  };

  return (
    <div className="chat-input-area">
      {meta.hint && <div className="chat-input-hint">{meta.hint}</div>}
      <div className="chat-input-wrapper">
        <textarea
          className="chat-input"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={meta.locked}
          rows={1}
          placeholder={meta.placeholder}
          aria-label="메시지 입력"
        />
        {executing ? (
          <button
            type="button"
            className="btn btn--danger"
            onClick={() => onStop?.()}
            aria-label="실행 중지"
          >
            중지
          </button>
        ) : (
          <button type="button" className="btn btn--primary" onClick={submit} disabled={!canSend}>
            전송
          </button>
        )}
      </div>
    </div>
  );
}
