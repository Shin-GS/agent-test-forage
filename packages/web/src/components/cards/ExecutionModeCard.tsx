// 실행 모드 선택 카드 (디자인 명세: .btn 버튼 그룹).
// metadata: { cardType: "execution_mode", recipeId, buttons: ["auto","manual"] }
// 버튼 클릭 → startExecution → runExecution 으로 실행 엔진 구동.
// 실행이 시작/완료되면 재클릭을 막는다(executed 상태로 버튼 잠금 + 배지).

import { useState } from "react";
import { ApiError, executionsApi } from "../../api";
import type { ExecutionModeCard as ExecutionModeCardMeta } from "../../api/types";
import { runExecution } from "../../services/executionRunner";
import { applyRunResult } from "../../services/executionResult";
import { useChatStore } from "../../store/chatStore";
import { useToastStore } from "../../store/toastStore";
import type { ConversationRuntimeStatus } from "../../store/types";

/** 대화방 처리 중 안내 문구 (상태별) */
function conversationLockMessage(status: ConversationRuntimeStatus): string {
  switch (status) {
    case "ai_responding":
      return "AI가 응답 중이에요. 완료 후 다시 시도해주세요.";
    case "executing":
      return "레시피 실행 중이에요. 완료 후 다시 시도해주세요.";
    case "input_waiting":
      return "입력 대기 중이에요. 먼저 진행 중인 작업을 마쳐주세요.";
    default:
      return "현재 작업이 진행 중이에요. 완료 후 다시 시도해주세요.";
  }
}

interface Props {
  card: ExecutionModeCardMeta;
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

export function ExecutionModeCard({ card }: Props) {
  const conversationId = useChatStore((state) => state.currentConversationId);
  const userId = useChatStore((state) => state.userId);
  const conversationStatus = useChatStore((state) => state.conversationStatus);
  const setActionPicker = useChatStore((state) => state.setActionPicker);
  const authPause = useChatStore((state) => state.authPause);
  const showToast = useToastStore((state) => state.show);
  const [running, setRunning] = useState(false);
  // 이 카드로 실행을 시작하면(이 세션에서) 재실행을 막는다(중복 방지). 로컬 상태만 사용한다.
  // 새로고침 후에는 다시 활성화되며, 실행 중복은 대화방 락(아래 processing 체크 + BE 409)으로 방어한다.
  const [started, setStarted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 인증 대기 배지는 store.authPause 로 판단한다(AuthRequiredCard 가 재개 완료 시 해제하면
  // 배지도 자동으로 "실행됨" 으로 바뀐다 — 로컬 state 로는 그 해제를 못 봄).
  const authPending = authPause != null && authPause.conversationId === conversationId;

  const buttons = card.buttons?.length ? card.buttons : ["auto", "manual"];
  const disabled = running || started || conversationId == null;

  const handleRun = async (buttonCode: string) => {
    if (disabled) return;
    // 대화방 락: 이미 처리 중(AI 응답/실행/입력 대기)이면 새 실행을 막고 안내한다(기획: 대화방 단위 락).
    if (conversationStatus !== "idle") {
      showToast(conversationLockMessage(conversationStatus), "warning");
      return;
    }
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
      // 대화방 락 경합(409 CONVERSATION_BUSY): 다른 탭/요청이 선점한 경우. 토스트로 안내.
      if (err instanceof ApiError && err.status === 409) {
        showToast("현재 대화방에 진행 중인 작업이 있어요. 완료 후 다시 시도해주세요.", "warning");
      } else {
        setError(err instanceof Error ? err.message : "실행에 실패했습니다");
      }
    } finally {
      setRunning(false);
    }
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
