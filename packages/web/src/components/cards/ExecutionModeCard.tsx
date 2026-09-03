// 실행 모드 선택 카드 (디자인 명세: .btn 버튼 그룹).
// metadata: { cardType: "execution_mode", recipeId, buttons: ["auto","manual"] }
// 버튼 클릭 → startExecution → runExecution 으로 실행 엔진 구동.
// 실행이 시작/완료되면 재클릭을 막는다(executed 상태로 버튼 잠금 + 배지).

import { useState } from "react";
import { executionsApi, specsApi } from "../../api";
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
  const setAuthPause = useChatStore((state) => state.setAuthPause);
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
      // 실행 엔진 구동. 진행 상태는 SSE 로 스토어가 갱신한다.
      const result = await runExecution(execution, { mode: spec.mode });

      if (result.outcome === "AUTH_REQUIRED" && result.auth) {
        // 인증 필요(401/403): 인증 안내 카드를 띄우기 위해 스펙에서 서비스명/로그인 프로필을 조회한다.
        let serviceName: string | null = null;
        let loginProfiles: { name: string; loginPageUrl: string }[] = [];
        try {
          const specDetail = await specsApi.getSpec(execution.apiSpecId);
          serviceName = specDetail.name;
          loginProfiles = (specDetail.authProfiles ?? [])
            .filter((p) => !!p.loginPageUrl)
            .map((p) => ({ name: p.name, loginPageUrl: p.loginPageUrl as string }));
        } catch {
          // 스펙 조회 실패 시에도 안내 카드는 띄운다(로그인 링크만 비어있음)
        }
        setAuthPause({
          conversationId: convId,
          httpStatus: result.auth.httpStatus,
          serviceName,
          loginProfiles,
          execution,
          resumeState: result.auth.resumeState,
          mode: spec.mode,
        });
      }

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
