// 최소 렌더 카드 모음 (service_select / candidates / plan).
// 디자인 명세: 목록 항목은 .card--interactive, 버튼은 .btn.
// - service_select / candidates: 목록 표시만(클릭 동작은 후속 구현, 자리표시).
// - plan: 실구현 — 읽기 전용 미리보기 + [취소]/[자동 실행] (docs/design/web/chat.html Case 11).

/* eslint-disable @typescript-eslint/no-explicit-any */

import { useState } from "react";
import { ApiError, conversationsApi, executionsApi } from "../../api";
import type {
  CandidatesCard as CandidatesCardMeta,
  PlanCard as PlanCardMeta,
  PlanRecipeItem,
  ServiceSelectCard as ServiceSelectCardMeta,
} from "../../api/types";
import { runExecution } from "../../services/executionRunner";
import { applyRunResult } from "../../services/executionResult";
import { useChatStore } from "../../store/chatStore";
import { useToastStore } from "../../store/toastStore";
import type { ConversationRuntimeStatus } from "../../store/types";

const listStyle: React.CSSProperties = {
  display: "flex",
  flexDirection: "column",
  gap: "var(--space-2)",
  marginTop: "var(--space-2)",
};

const rowStyle: React.CSSProperties = {
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  gap: "var(--space-2)",
  padding: "var(--space-2) var(--space-3)",
  background: "var(--color-bg-tertiary)",
  borderRadius: "var(--radius-md)",
};

const nameStyle: React.CSSProperties = {
  fontSize: "var(--font-size-sm)",
  fontWeight: "var(--font-weight-medium)",
};

const descStyle: React.CSSProperties = {
  fontSize: "var(--font-size-xs)",
  color: "var(--color-text-tertiary)",
  marginTop: 2,
};

/** 서비스 선택 — 버튼 목록만 표시 (동작은 후속) */
export function ServiceSelectCard({ card }: { card: ServiceSelectCardMeta }) {
  const services: any[] = card.services ?? [];
  return (
    <div style={{ display: "flex", flexWrap: "wrap", gap: "var(--space-2)", marginTop: "var(--space-2)" }}>
      {services.map((svc, idx) => (
        <button
          key={svc.id ?? svc.apiSpecId ?? idx}
          type="button"
          className="btn btn--secondary btn--sm"
          disabled
          title="후속 구현 예정"
        >
          {svc.name ?? svc.title ?? String(svc)}
        </button>
      ))}
      {services.length === 0 && <span style={descStyle}>표시할 서비스가 없습니다</span>}
    </div>
  );
}

/** 후보 선택 — 항목 목록만 표시 (동작은 후속) */
export function CandidatesCard({ card }: { card: CandidatesCardMeta }) {
  const candidates: any[] = card.candidates ?? [];
  return (
    <div style={listStyle}>
      {candidates.map((c, idx) => (
        <div key={c.recipeId ?? c.id ?? idx} style={rowStyle}>
          <div>
            <div style={nameStyle}>{c.name ?? c.recipeName ?? `후보 ${idx + 1}`}</div>
            {(c.description ?? c.desc) && <div style={descStyle}>{c.description ?? c.desc}</div>}
          </div>
        </div>
      ))}
      {candidates.length === 0 && <span style={descStyle}>후보가 없습니다</span>}
    </div>
  );
}

/** 대화방 처리 중 안내 문구 (상태별) — ExecutionModeCard 와 동일 규칙 */
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

/**
 * 플랜 제안 카드 (읽기 전용 미리보기, 1단계 — 편집 UI 없음).
 * - 💡 rationale + 레시피 순서 목록(이름 + 서비스 뱃지 + 기본값 미리보기, 나머지는 "실행 중 결정").
 * - [취소] → 대화방 cancel API (FE 단독 해제 금지). [자동 실행] → plan-executions 시작 → 러너 구동.
 * - 🔗 이전 결과 예측 표시 금지(실행 전 확정 불가). 값 미리보기는 기본값(📌)만.
 */
export function PlanCard({ card }: { card: PlanCardMeta }) {
  const conversationId = useChatStore((state) => state.currentConversationId);
  const conversationStatus = useChatStore((state) => state.conversationStatus);
  const showToast = useToastStore((state) => state.show);

  const [running, setRunning] = useState(false);
  // 이 카드로 플랜을 시작하면(이 세션에서) 재클릭을 막는다(중복 방지). 로컬 상태만 사용.
  const [started, setStarted] = useState(false);
  // 취소로 종료된 경우(실행 아님) — "실행됨" 배지 오표기 방지. 취소/실행을 배지에서 구분한다.
  const [cancelled, setCancelled] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const recipes = card.recipes ?? [];
  const recipeIds = card.recipeIds ?? [];
  const total = recipes.length || recipeIds.length;
  const disabled = running || started || conversationId == null || recipeIds.length === 0;

  const handleCancel = async () => {
    if (running || started || conversationId == null) return;
    setRunning(true);
    setError(null);
    try {
      // FE 단독으로 닫지 않는다 — 서버가 상태를 idle 로 해제(SSE 로 전파). plan.md [취소]
      await conversationsApi.cancel(conversationId);
      setCancelled(true);
      setStarted(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "취소에 실패했습니다");
    } finally {
      setRunning(false);
    }
  };

  const handleRun = async () => {
    if (disabled) return;
    // 대화방 락: 이미 처리 중이면 새 실행을 막고 안내한다(대화방 단위 락).
    if (conversationStatus !== "idle") {
      showToast(conversationLockMessage(conversationStatus), "warning");
      return;
    }
    const convId = conversationId!;
    setRunning(true);
    setError(null);
    try {
      // 플랜은 항상 AUTO. recipeIds 1개면 BE 가 단일(SINGLE)로 수렴한다.
      const execution = await executionsApi.startPlan(convId, {
        recipeIds,
        mode: "AUTO",
      });

      // 시작 직후 첫 레시피에 pre-run 필수 입력 미충족이면 BE 가 pendingInputs 를 준다 → 액션 피커.
      if ((execution.pendingInputs?.length ?? 0) > 0) {
        useChatStore.getState().setActionPicker({
          conversationId: convId,
          executionId: execution.id,
          stepIndex: -1,
          variables: execution.pendingInputs ?? [],
          mode: "AUTO",
        });
        setStarted(true);
        return;
      }

      // 러너 구동(멀티레시피). 진행/완료는 SSE 로 스토어가 갱신.
      const result = await runExecution(execution, { mode: "AUTO" });
      // 결과 후처리: AUTH_REQUIRED → 인증 카드 / INPUT_REQUIRED → 다음 레시피 액션 피커.
      await applyRunResult(execution, result, "AUTO");
      setStarted(true);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        showToast("현재 대화방에 진행 중인 작업이 있어요. 완료 후 다시 시도해주세요.", "warning");
      } else {
        setError(err instanceof Error ? err.message : "플랜 실행에 실패했습니다");
      }
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="plan-card" role="group" aria-label="실행 계획 제안">
      <div className="plan-card__title">📋 실행 계획 ({total}단계)</div>

      {card.rationale && (
        <div className="plan-rationale">
          <span className="plan-rationale__icon" aria-hidden>
            💡
          </span>
          <span>{card.rationale}</span>
        </div>
      )}

      <div className="plan-card__recipes">
        {recipes.map((recipe, idx) => (
          <PlanRecipeRow key={recipe.recipeId ?? idx} recipe={recipe} order={idx + 1} />
        ))}
        {recipes.length === 0 && <span style={descStyle}>플랜 항목이 없습니다</span>}
      </div>

      <div className="plan-card__actions">
        <button
          type="button"
          className="btn btn--ghost"
          onClick={handleCancel}
          disabled={running || started || conversationId == null}
          aria-label="플랜 취소"
        >
          취소
        </button>
        <button
          type="button"
          className="btn btn--primary"
          onClick={handleRun}
          disabled={disabled}
          aria-label="플랜 자동 실행"
        >
          {running ? "실행 중..." : "자동 실행 ▶"}
        </button>
        {started && (
          <span className={`badge ${cancelled ? "badge--neutral" : "badge--info"}`}>
            {cancelled ? "취소됨" : "실행됨"}
          </span>
        )}
      </div>

      {error && <div className="plan-card__error">{error}</div>}
    </div>
  );
}

/** 플랜 제안 레시피 한 줄 (읽기 전용): 순서 배지 + 이름 + 서비스 뱃지 + 값 미리보기 */
function PlanRecipeRow({ recipe, order }: { recipe: PlanRecipeItem; order: number }) {
  const name = recipe.recipeName ?? (recipe.recipeId != null ? `레시피 #${recipe.recipeId}` : `레시피 ${order}`);
  const preview = recipe.inputPreview ?? [];

  return (
    <div className="plan-recipe">
      <div className="plan-recipe__head">
        <span className="plan-recipe__order" aria-hidden>
          {order}
        </span>
        <span className="plan-recipe__name">{name}</span>
        {recipe.serviceName && (
          <span className="badge badge--neutral" style={{ marginLeft: "auto" }}>
            {recipe.serviceName}
          </span>
        )}
      </div>
      <div className="plan-recipe__values">
        {preview.map((p, i) => (
          <span key={p.key ?? i}>
            {i > 0 && " · "}
            {p.label}: {String(p.value)} <span className="badge badge--neutral">📌 기본값</span>
          </span>
        ))}
        {preview.length > 0 && " · "}
        {/* 나머지 값은 실행 전 확정 불가 → "실행 중 결정" (🔗 예측 표시 금지) */}
        <span className="plan-value--pending">그 외 값은 실행 중 결정</span>
      </div>
    </div>
  );
}
