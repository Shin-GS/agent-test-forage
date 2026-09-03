// 실행 모드 선택 카드 (디자인 명세: .btn 버튼 그룹).
// metadata: { cardType: "execution_mode", recipeId, buttons: ["auto","manual"] }
// 버튼 클릭 → startExecution → runExecution 으로 실행 엔진 구동.
// 실행이 시작/완료되면 재클릭을 막는다(executed 상태로 버튼 잠금 + 배지).

import { useState } from "react";
import { executionsApi } from "../../api";
import type { ExecutionModeCard as ExecutionModeCardMeta } from "../../api/types";
import { runExecution } from "../../services/executionRunner";
import { useChatStore } from "../../store/chatStore";

interface Props {
  card: ExecutionModeCardMeta;
}

/** buttons 코드 → 표시 라벨 + 실행 모드 코드 */
const MODE_LABELS: Record<string, { label: string; mode: string }> = {
  auto: { label: "자동 실행", mode: "AUTO" },
  manual: { label: "직접 입력하며 실행", mode: "MANUAL" },
};

export function ExecutionModeCard({ card }: Props) {
  const conversationId = useChatStore((state) => state.currentConversationId);
  const userId = useChatStore((state) => state.userId);
  const [running, setRunning] = useState(false);
  // 한 번 실행하면 이 카드는 다시 실행할 수 없다(중복 실행 방지).
  const [executedMode, setExecutedMode] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const buttons = card.buttons?.length ? card.buttons : ["auto", "manual"];
  const disabled = running || executedMode != null || conversationId == null;

  const handleRun = async (buttonCode: string) => {
    if (disabled) return;
    const spec = MODE_LABELS[buttonCode] ?? { label: buttonCode, mode: buttonCode.toUpperCase() };
    setRunning(true);
    setError(null);
    try {
      const execution = await executionsApi.startExecution(conversationId!, {
        userId,
        recipeId: card.recipeId,
        mode: spec.mode,
      });
      // 실행 엔진 구동. 진행 상태는 SSE 로 스토어가 갱신한다.
      await runExecution(execution, { mode: spec.mode });
      setExecutedMode(buttonCode);
    } catch (err) {
      setError(err instanceof Error ? err.message : "실행에 실패했습니다");
    } finally {
      setRunning(false);
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)", marginTop: "var(--space-2)" }}>
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
        {executedMode && <span className="badge badge--info">실행됨</span>}
      </div>
      {running && (
        <span style={{ fontSize: "var(--font-size-xs)", color: "var(--color-text-secondary)" }}>실행 중입니다...</span>
      )}
      {error && <span style={{ fontSize: "var(--font-size-xs)", color: "var(--color-error)" }}>{error}</span>}
    </div>
  );
}
