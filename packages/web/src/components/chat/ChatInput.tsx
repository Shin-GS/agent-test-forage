// 채팅 입력창 (디자인 명세 chat.html .chat-input-area).
// - conversationStatus 로 잠금/안내 처리
//   idle: 입력 가능
//   ai_responding: 잠금 + "AI가 응답 중입니다"
//   executing: 잠금 + "레시피 실행 중입니다" + [중지] 버튼
//   input_waiting: 잠금(자유채팅 불가, 액션 피커로만 입력) + 안내 문구
// - Enter 전송 / Shift+Enter 줄바꿈
// - 구조: .chat-input-wrapper > textarea.chat-input + button.btn.btn--primary

import { useState } from "react";
import { ConfirmModal } from "../common/ConfirmModal";
import type { ConversationRuntimeStatus } from "../../store/types";

interface Props {
  status: ConversationRuntimeStatus;
  onSend: (content: string) => void;
  /** executing 상태에서 실행 중지 요청 (확인 모달 확정 시 호출) */
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
  // 중지 확인 모달 (실행 중 [중지] 클릭 시 노출 — 완료분 되돌릴 수 없음 경고, chat.cases.md Case 20)
  const [confirmStop, setConfirmStop] = useState(false);

  const meta = STATUS_TEXT[status];
  const canSend = !meta.locked && value.trim().length > 0;
  const executing = status === "executing";

  const submit = () => {
    if (!canSend) return;
    onSend(value.trim());
    setValue("");
  };

  const confirmStopAndClose = () => {
    setConfirmStop(false);
    onStop?.();
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
            onClick={() => setConfirmStop(true)}
            aria-label="실행 중지 (확인 모달 노출)"
          >
            중지
          </button>
        ) : (
          <button type="button" className="btn btn--primary" onClick={submit} disabled={!canSend}>
            전송
          </button>
        )}
      </div>

      {/* 중지 확인 모달: 완료분 되돌릴 수 없음 경고 (chat.cases.md Case 20 / plan.md 사용자 중단) */}
      <ConfirmModal
        open={confirmStop}
        title="실행 중지"
        description={"이미 완료된 레시피는 되돌릴 수 없습니다. 중단하시겠습니까?\n이미 생성된 데이터는 유지됩니다."}
        confirmLabel="중단"
        cancelLabel="계속 실행"
        danger
        initialFocus="cancel"
        onConfirm={confirmStopAndClose}
        onCancel={() => setConfirmStop(false)}
      />
    </div>
  );
}
