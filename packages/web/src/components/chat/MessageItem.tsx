// 단일 메시지 렌더 (디자인 명세 chat.html .message).
// - role.code (USER / AI / SYSTEM) 로 정렬/스타일 결정
//   USER  → .message--user (아바타 👤, 우측 정렬)
//   AI    → .message--ai   (아바타 🤖, 좌측 정렬)
//   SYSTEM→ .chat-system-notice (중앙 안내)
// - type.code === "CARD" → MessageCard 디스패처를 말풍선 내부에 삽입
// - 그 외(TEXT) → 평문 렌더

import type { MessageResponse } from "../../api/types";
import { MessageCard } from "../cards/MessageCard";

interface Props {
  message: MessageResponse;
}

function roleOf(message: MessageResponse): "USER" | "AI" | "SYSTEM" {
  const code = (message.role.code ?? "").toUpperCase();
  if (code === "USER") return "USER";
  if (code === "SYSTEM") return "SYSTEM";
  return "AI";
}

export function MessageItem({ message }: Props) {
  const role = roleOf(message);
  const typeCode = (message.type.code ?? "").toUpperCase();
  const isCard = typeCode === "CARD";

  // 시스템 메시지: 중앙 안내 (레시피 실행 진입점 등)
  if (role === "SYSTEM") {
    return <div className="chat-system-notice">{message.content}</div>;
  }

  const user = role === "USER";

  return (
    <div className={`message ${user ? "message--user" : "message--ai"}`}>
      <div className="message__avatar">{user ? "👤" : "🤖"}</div>
      <div className="message__content">
        {message.content && <div style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{message.content}</div>}
        {isCard && <MessageCard metadata={message.metadata} />}
      </div>
    </div>
  );
}
