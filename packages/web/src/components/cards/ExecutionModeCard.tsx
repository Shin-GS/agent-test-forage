// 실행 모드 선택 카드 (디자인 명세: .btn 버튼 그룹).
// metadata: { cardType: "execution_mode", recipeId, buttons: ["auto","manual"] }
// 버튼 클릭 → startExecution → runExecution 으로 실행 엔진 구동.
// 실행이 시작/완료되면 재클릭을 막는다(executed 상태로 버튼 잠금 + 배지).

import type { ExecutionModeCard as ExecutionModeCardMeta } from "../../api/types";
import { useChatStore } from "../../store/chatStore";
import { useRecipeRun } from "./useRecipeRun";

interface Props {
  card: ExecutionModeCardMeta;
  /** 촉발 파트 id (실행 요청 messageId 로 전달 → BE CONSUMED 처리) */
  partId: number;
  /** 파트가 이미 CONSUMED/CANCELLED 인지 (재실행 차단 + 배지 표시) */
  consumed: boolean;
}

/** buttons 코드 → 표시 라벨 + 실행 모드 코드 (기획 messaging.md: auto→바로 실행, manual→값 확인 후 실행) */
const MODE_LABELS: Record<string, { label: string; mode: string }> = {
  auto: { label: "바로 실행", mode: "AUTO" },
  manual: { label: "값 확인 후 실행", mode: "MANUAL" },
};

/** 값 출처 → 아이콘/설명 (messaging.md) */
const SOURCE_BADGE: Record<string, { icon: string; text: string }> = {
  utterance: { icon: "🗣️", text: "발화" },
  default: { icon: "📌", text: "기본값" },
  none: { icon: "✏️", text: "미입력" },
};

/** 값 표시: 채워졌으면 값, 미충족이면 "(입력 필요)" */
function renderValue(value: unknown | null): string {
  if (value === null || value === undefined || value === "") return "(입력 필요)";
  return String(value);
}

export function ExecutionModeCard({ card, partId, consumed }: Props) {
  const conversationId = useChatStore((state) => state.currentConversationId);
  // 실행 시작 로직은 공통 훅으로 위임(대화방 락/중복/409 토스트/pendingInputs 분기 포함).
  const { running, started, authPending, error, startRun } = useRecipeRun(consumed);

  const buttons = card.buttons?.length ? card.buttons : ["auto", "manual"];
  // consumed(파트 소진, 새로고침 복원)면 항상 비활성. 로컬 started 는 이 세션 중복 방지.
  const disabled = running || started || consumed || conversationId == null;

  const handleRun = (buttonCode: string) => {
    if (disabled) return;
    const spec = MODE_LABELS[buttonCode] ?? { label: buttonCode, mode: buttonCode.toUpperCase() };
    void startRun({
      recipeId: card.recipeId,
      mode: spec.mode,
      extractedValues: card.extractedValues,
      partId,
    });
  };

  const inputVariables = card.inputVariables ?? [];

  return (
    <div className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)", marginTop: "var(--space-2)" }}>
      {/* 레시피 정보: 무엇을 실행하는지 */}
      <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-1)" }}>
        <div style={{ fontWeight: 600 }}>
          📋 {card.recipeName ?? "레시피 실행"}
        </div>
        {card.description && (
          <div style={{ fontSize: "var(--font-size-sm)", color: "var(--color-text-secondary)" }}>
            {card.description}
          </div>
        )}
      </div>

      {/* 필요한 값 목록: 어떤 값이 필요한지 + 현재 값/출처 */}
      {inputVariables.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-1)" }}>
          <div style={{ fontSize: "var(--font-size-sm)", color: "var(--color-text-secondary)" }}>필요한 값</div>
          <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: "var(--space-1)" }}>
            {inputVariables.map((v) => {
              const badge = SOURCE_BADGE[v.source] ?? SOURCE_BADGE.none;
              const missing = v.value === null || v.value === undefined || v.value === "";
              return (
                <li key={v.key} style={{ display: "flex", gap: "var(--space-2)", alignItems: "center", fontSize: "var(--font-size-sm)" }}>
                  <span style={{ color: "var(--color-text-secondary)", minWidth: 90 }}>
                    {v.label}
                    {v.required && <span style={{ color: "var(--color-error)" }}> *</span>}
                  </span>
                  <span style={{ color: missing ? "var(--color-text-tertiary)" : "var(--color-text-primary)" }}>
                    {renderValue(v.value)}
                  </span>
                  <span title={badge.text} style={{ fontSize: "var(--font-size-xs)", color: "var(--color-text-tertiary)" }}>
                    {badge.icon} {badge.text}
                  </span>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      <div style={{ display: "flex", gap: "var(--space-2)", flexWrap: "wrap", alignItems: "center" }}>
        {buttons.map((code, idx) => {
          const spec = MODE_LABELS[code] ?? { label: code, mode: code };
          const primary = idx === 0;
          return (
            <button
              key={code}
              type="button"
              className={`btn ${primary ? "btn--primary" : "btn--secondary"} btn--sm`}
              disabled={disabled}
              onClick={() => handleRun(code)}
            >
              {spec.label}
            </button>
          );
        })}
        {(started || consumed) && (
          <span className={`badge ${authPending ? "badge--warning" : "badge--info"}`}>
            {authPending ? "인증 대기" : "실행됨"}
          </span>
        )}
      </div>
      {running && (
        <span style={{ fontSize: "var(--font-size-xs)", color: "var(--color-text-secondary)" }}>실행 중입니다...</span>
      )}
      {error && <span style={{ fontSize: "var(--font-size-xs)", color: "var(--color-error)" }}>{error}</span>}
    </div>
  );
}
