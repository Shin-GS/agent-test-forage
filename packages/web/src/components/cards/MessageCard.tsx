// 카드 디스패처.
// 파트의 cardType 에 따라 알맞은 카드 컴포넌트를 렌더한다(payload = cardType별 필드).
// partId 는 촉발 파트 id — 실행 요청 시 BE 로 실어보내 그 파트를 CONSUMED 처리한다
// (새로고침 후 카드 재활성화 방지의 핵심). consumed 면 카드를 비활성으로 그린다.

import type { CardMeta } from "../../api/types";
import { ExecutionModeCard } from "./ExecutionModeCard";
import { CandidatesCard, PlanCard, ServiceSelectCard } from "./ListCards";

interface Props {
  /** part.cardType (execution_mode/plan/candidates/service_select) */
  cardType: string | null;
  /** part.payload — cardType별 필드가 담긴 구조화 데이터 */
  payload: unknown;
  /** 촉발 파트 id (실행 요청 시 messageId 로 전달 → BE CONSUMED 처리) */
  partId: number;
  /** 파트가 이미 CONSUMED/CANCELLED 인지 (비활성 시각처리 + 재실행 차단) */
  consumed: boolean;
}

/** payload + cardType 을 CardMeta 형태로 합성 (payload 안에 cardType 이 없을 수 있어 주입) */
function asCardMeta(cardType: string | null, payload: unknown): CardMeta | null {
  if (!cardType) return null;
  const base = payload && typeof payload === "object" ? (payload as Record<string, unknown>) : {};
  return { ...base, cardType } as CardMeta;
}

export function MessageCard({ cardType, payload, partId, consumed }: Props) {
  const card = asCardMeta(cardType, payload);
  if (!card) return null;

  switch (card.cardType) {
    case "execution_mode":
      return <ExecutionModeCard card={card} partId={partId} consumed={consumed} />;
    case "service_select":
      return <ServiceSelectCard card={card} />;
    case "candidates":
      return <CandidatesCard card={card} />;
    case "plan":
      return <PlanCard card={card} partId={partId} consumed={consumed} />;
    default:
      return null;
  }
}
