// 실행 모드 선택 카드 (디자인 명세: .btn 버튼 그룹).
// metadata: { cardType: "execution_mode", recipeId, buttons: ["auto","manual"] }
// 버튼 클릭 → startExecution → runExecution 으로 실행 엔진 구동.
// 실행이 시작/완료되면 재클릭을 막는다(executed 상태로 버튼 잠금 + 배지).

import { useState } from "react";
import { executionsApi } from "../../api";
import type { ExecutionModeCard as ExecutionModeCardMeta } from "../../api/types";
import { runExecution } from "../../services/executionRunner";
import { applyRunResult } from "../../services/executionResult";
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
  const setActionPicker = useChatStore((state) => state.setActionPicker);
  const authPause = useChatStore((state) => state.authPause);
  const [running, setRunning] = useState(false);
  // 한 번 실행을 시작하면 이 카드로 다시 실행할 수 없다(중복 실행 방지).
  const [started, setStarted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 인증 대기 배지는 store.authPause 로 판단한다(AuthRequiredCard 가 재개 완료 시 해제하면
  // 배지도 자동으로 "실행됨" 으로 바뀐다 — 로컬 state 로는 그 해제를 못 봄).
  const authPending = authPause != null && authPause.conversationId === conversationId;

  const buttons = card.buttons?.length ? card.buttons : ["auto", "manual"];
  const disabled = running || started || conversationId == null;

  const handleRun = async (buttonCode: string) => {
    if (disabled) return;
    const spec = MODE_LABELS[buttonCode] ?? { label: buttonCode, mode: buttonCode.toUpperCase() };
    const convId = conversationId!;
    setRunning(true);
    setError(null);
    try {
      const execution = await executionsApi.startExecution(convId, {
        userId,
        recipeId: card.recipeId,
        mode: spec.mode,
        // AI 가 발화에서 추출한 값을 실행 시작 시 시드(BE 가 recipe 변수 기본값과 병합).
        initialContext: card.extractedValues,
      });

      // 입력 미충족: BE 가 대화방을 WAITING_INPUT 으로 세우고 pendingInputs(수집할 변수)를 준다.
      // 판정 기준은 pendingInputs 다(execution.status 는 실행 상태=RUNNING 이라 대화방 상태와 다름).
      // pendingInputs 가 있으면 러너를 돌리지 않고 액션 피커를 띄운다(입력 후 respond→executing→재개).
      if ((execution.pendingInputs?.length ?? 0) > 0) {
        setActionPicker({
          conversationId: convId,
          executionId: execution.id,
          stepIndex: -1, // pre-run 일괄 수집
          variables: execution.pendingInputs ?? [],
          mode: spec.mode,
        });
        setStarted(true);
        return;
      }

      // 실행 엔진 구동. 진행 상태는 SSE 로 스토어가 갱신한다.
      const result = await runExecution(execution, { mode: spec.mode });
      // 결과 후처리(AUTH_REQUIRED면 인증 안내 카드). ActionPicker 재개 경로와 공통 로직 사용.
      await applyRunResult(execution, result, spec.mode);

      // 실행이 시작되면(성공/실패/인증대기 무관) 이 카드로 재실행하지 않는다.
      setStarted(true);
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
        {started && (
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
