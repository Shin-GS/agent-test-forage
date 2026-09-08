// 단일 메시지 렌더 (디자인 명세 chat.html .message).
// message.type 으로 본문 렌더를 분기한다(messaging.md MessageType).
//   USER/AI/SYSTEM role 로 정렬/스타일 결정.
//   TEXT     → content 평문 + references payload 동반 시 하단에 참고 자료 버튼 리스트
//   CARD     → MessageCard 디스패처
//   PROGRESS → ProgressSteps (payload.steps 스텝 리스트, 완료 후 유지)
//   RESULT   → ResultMessage (결과 요약 + resultValues)
//   INVESTIGATE_PROGRESS → InvestigateProgress (정보 조회 단계 리스트, 종료 후 유지)
//   SYSTEM   → 중앙 안내
// payload 는 message.metadata(BE 가 payloadJson 파싱본). 파싱 실패/미지원 버전이면 content 폴백.

import type { MessageResponse } from "../../api/types";
import {
  asInvestigateProgressPayload,
  asProgressPayload,
  asReferencesPayload,
  asResultPayload,
} from "../../services/messagePayload";
import { MessageCard } from "../cards/MessageCard";
import { InvestigateProgress, shouldRenderInvestigate } from "./InvestigateProgress";
import { MessageReferences } from "./MessageReferences";
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
    case "INVESTIGATE_PROGRESS": {
      const payload = asInvestigateProgressPayload(message.metadata);
      return payload ? (
        <InvestigateProgress payload={payload} />
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
    default: {
      // investigate 답변은 TEXT + references payload 동반 가능. references 파싱 실패면 순수 텍스트만.
      const references = asReferencesPayload(message.metadata);
      return (
        <>
          {contentText(message.content)}
          {references && <MessageReferences payload={references} />}
        </>
      );
    }
  }
}

export function MessageItem({ message }: Props) {
  const role = roleOf(message);

  // 시스템 메시지: 중앙 안내
  if (role === "SYSTEM") {
    return <div className="chat-system-notice">{message.content}</div>;
  }

  // 정보 조회(INVESTIGATE_PROGRESS) 중 "정상 종료 + 스텝 0개"는 보여줄 내용이 없다.
  // 본문만 비우면 아바타/행 여백이 남아 어색하므로, 메시지 행 자체를 렌더하지 않는다.
  // (DB 기록은 유지 — 화면에서만 숨김. 실제 답변은 별도 TEXT 메시지로 표시됨)
  if ((message.type.code ?? "").toUpperCase() === "INVESTIGATE_PROGRESS") {
    const payload = asInvestigateProgressPayload(message.metadata);
    if (payload && !shouldRenderInvestigate(payload)) {
      return null;
    }
  }

  const user = role === "USER";

  return (
    <div className={`message ${user ? "message--user" : "message--ai"}`}>
      <div className="message__avatar">{user ? "👤" : "🤖"}</div>
      <div className="message__content">{renderBody(message)}</div>
    </div>
  );
}
