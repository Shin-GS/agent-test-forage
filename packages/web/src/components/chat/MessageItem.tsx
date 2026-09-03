// 단일 메시지 렌더 (디자인 명세 chat.html .message).
// message.type 으로 본문 렌더를 분기한다(messaging.md MessageType).
//   USER/AI/SYSTEM role 로 정렬/스타일 결정.
//   TEXT     → content 평문(Markdown 예정)
//   CARD     → MessageCard 디스패처
//   PROGRESS → ProgressSteps (payload.steps 스텝 리스트, 완료 후 유지)
//   RESULT   → ResultMessage (결과 요약 + resultValues)
//   SYSTEM   → 중앙 안내
// payload 는 message.metadata(BE 가 payloadJson 파싱본). 파싱 실패/미지원 버전이면 content 폴백.

import type { MessageResponse } from "../../api/types";
import { asProgressPayload, asResultPayload } from "../../services/messagePayload";
import { MessageCard } from "../cards/MessageCard";
import { ProgressSteps } from "./ProgressSteps";
import { ResultMessage } from "./ResultMessage";

interface Props {
  message: MessageResponse;
}

function roleOf(message: MessageResponse): "USER" | "AI" | "SYSTEM" {
  const code = (message.role.code ?? "").toUpperCase();
  if (code === "USER") return "USER";
  if (code === "SYSTEM") return "SYSTEM";
  return "AI";
}

/** content 평문 폴백 렌더 */
function contentText(content: string | null) {
  if (!content) return null;
  return <div style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{content}</div>;
}

/** 메시지 타입별 본문. payload 파싱 실패 시 content 로 폴백한다. */
function renderBody(message: MessageResponse) {
  const typeCode = (message.type.code ?? "").toUpperCase();

  switch (typeCode) {
    case "PROGRESS": {
      const payload = asProgressPayload(message.metadata);
      return payload ? <ProgressSteps payload={payload} /> : contentText(message.content);
    }
    case "RESULT": {
      const payload = asResultPayload(message.metadata);
      return payload ? (
        <ResultMessage payload={payload} content={message.content} />
      ) : (
        contentText(message.content)
      );
    }
    case "CARD":
      return (
        <>
          {contentText(message.content)}
          <MessageCard metadata={message.metadata} />
        </>
      );
    case "TEXT":
    default:
      return contentText(message.content);
  }
}

export function MessageItem({ message }: Props) {
  const role = roleOf(message);

  // 시스템 메시지: 중앙 안내
  if (role === "SYSTEM") {
    return <div className="chat-system-notice">{message.content}</div>;
  }

  const user = role === "USER";

  return (
    <div className={`message ${user ? "message--user" : "message--ai"}`}>
      <div className="message__avatar">{user ? "👤" : "🤖"}</div>
      <div className="message__content">{renderBody(message)}</div>
    </div>
  );
}
