// 카드 디스패처.
// 메시지 metadata.cardType 에 따라 알맞은 카드 컴포넌트를 렌더한다.

import type { CardMeta } from "../../api/types";
import { ExecutionModeCard } from "./ExecutionModeCard";
import { CandidatesCard, PlanCard, ServiceSelectCard } from "./ListCards";

interface Props {
  metadata: unknown;
}

/** metadata 가 CardMeta 형태인지 최소 검증 */
function asCardMeta(value: unknown): CardMeta | null {
  if (value && typeof value === "object" && "cardType" in value) {
    return value as CardMeta;
  }
  return null;
}

export function MessageCard({ metadata }: Props) {
  const card = asCardMeta(metadata);
  if (!card) return null;

  switch (card.cardType) {
    case "execution_mode":
      return <ExecutionModeCard card={card} />;
    case "service_select":
      return <ServiceSelectCard card={card} />;
    case "candidates":
      return <CandidatesCard card={card} />;
    case "plan":
      return <PlanCard card={card} />;
    default:
      return null;
  }
}
