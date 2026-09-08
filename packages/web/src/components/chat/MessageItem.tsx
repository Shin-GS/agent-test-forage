// 단일 턴 렌더 (디자인 명세 chat.html .message, chat/overview.md "턴과 파트 렌더").
// 한 턴 = 아바타 1개 + 파트(블록) 세로 스택이다. 아바타(👤/🤖/시스템)는 턴 맨 위에 한 번만 그리고,
// 그 아래로 파트들이 id 오름차순으로 쌓인다(파트마다 아바타 반복 금지).
//
// role.code: USER/ASSISTANT/SYSTEM → 정렬/스타일 결정.
// 파트 type.code 별 렌더러 디스패치:
//   TEXT          → content 평문(Markdown 폴백 없이 pre-wrap)
//   CARD          → MessageCard 디스패처(cardType+payload)
//   PROGRESS      → ProgressSteps (payload.recipes 스텝 리스트, 완료 후 유지)
//   RESULT        → ResultMessage (결과 요약 + resultValues)
//   INVESTIGATE   → InvestigateProgress (정보 조회 단계 리스트, 종료 후 유지)
//   ACTION_PICKER → (현재 미표시 — 실행 액션 피커는 store.actionPicker 로 MessageList 가 렌더)
//   REFERENCES    → MessageReferences (조회 출처 칩)
// payload 는 part.payload(BE 가 payloadJson 파싱본). 파싱 실패/미지원 버전이면 content 폴백.

import type { MessageResponse, PartResponse } from "../../api/types";
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

/** 파트가 인터랙티브(카드/액션피커)면서 CONSUMED/CANCELLED 상태인지 — 비활성 시각처리 대상 */
function isConsumed(part: PartResponse): boolean {
  const status = (part.status.code ?? "").toUpperCase();
  return status === "CONSUMED" || status === "CANCELLED";
}

/**
 * 파트 하나를 화면에 렌더할지 여부.
 * 정보 조회(INVESTIGATE)인데 "정상 종료 + 스텝 0개"면 보여줄 내용이 없어 숨긴다
 * (조회를 시도했으나 붙일 소스가 없어 그냥 답한 경우 — 빈 블록 낭비 방지). DB 기록은 유지.
 */
function shouldRenderPart(part: PartResponse): boolean {
  const typeCode = (part.type.code ?? "").toUpperCase();
  if (typeCode === "INVESTIGATE") {
    const payload = asInvestigateProgressPayload(part.payload);
    if (payload && !shouldRenderInvestigate(payload)) {
      return false;
    }
  }
  return true;
}

/** 파트 타입별 본문. payload 파싱 실패 시 content 로 폴백한다. */
function renderPart(part: PartResponse) {
  const typeCode = (part.type.code ?? "").toUpperCase();

  switch (typeCode) {
    case "PROGRESS": {
      const payload = asProgressPayload(part.payload);
      return payload ? <ProgressSteps payload={payload} /> : contentText(part.content);
    }
    case "RESULT": {
      const payload = asResultPayload(part.payload);
      return payload ? (
        <ResultMessage payload={payload} content={part.content} />
      ) : (
        contentText(part.content)
      );
    }
    case "INVESTIGATE": {
      const payload = asInvestigateProgressPayload(part.payload);
      return payload ? <InvestigateProgress payload={payload} /> : contentText(part.content);
    }
    case "REFERENCES": {
      const references = asReferencesPayload(part.payload);
      return references ? <MessageReferences payload={references} /> : contentText(part.content);
    }
    case "CARD":
      return (
        <>
          {contentText(part.content)}
          <MessageCard
            cardType={part.cardType}
            payload={part.payload}
            partId={part.id}
            consumed={isConsumed(part)}
          />
        </>
      );
    case "ACTION_PICKER":
      // 실행 액션 피커는 store.actionPicker 로 MessageList 가 별도 렌더한다.
      // 저장된 ACTION_PICKER 파트 자체는 content 요약이 있으면만 노출(없으면 빈 블록).
      return contentText(part.content);
    case "TEXT":
    default:
      return contentText(part.content);
  }
}

/** 인터랙티브 파트가 소진(CONSUMED)됐으면 비활성 시각처리로 감싼다 */
function PartBlock({ part }: { part: PartResponse }) {
  const consumed = isConsumed(part);
  const typeCode = (part.type.code ?? "").toUpperCase();
  const interactive = typeCode === "CARD" || typeCode === "ACTION_PICKER";
  return (
    <div
      className={`message__part${consumed && interactive ? " message__part--consumed" : ""}`}
      aria-disabled={consumed && interactive ? true : undefined}
    >
      {renderPart(part)}
    </div>
  );
}

export function MessageItem({ message }: Props) {
  const role = roleOf(message);

  // 시스템 메시지: 중앙 안내 (SYSTEM 턴 = TEXT 파트 1개)
  if (role === "SYSTEM") {
    const text = message.parts.map((p) => p.content ?? "").join("\n").trim();
    if (!text) return null;
    return <div className="chat-system-notice">{text}</div>;
  }

  // 렌더할 파트만 추린다(빈 요소 숨김 — 예: done+스텝0 investigate).
  const parts = (message.parts ?? []).filter(shouldRenderPart);

  // 표시할 파트가 하나도 없으면 턴 행 자체를 렌더하지 않는다(빈 아바타/여백 방지).
  if (parts.length === 0) {
    return null;
  }

  const user = role === "USER";

  return (
    <div className={`message ${user ? "message--user" : "message--ai"}`}>
      <div className="message__avatar">{user ? "👤" : "🤖"}</div>
      <div className="message__content">
        {parts.map((part) => (
          <PartBlock key={part.id} part={part} />
        ))}
      </div>
    </div>
  );
}
