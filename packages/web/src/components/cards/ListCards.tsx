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
 * 편집 상태의 플랜 항목 한 줄 (로컬 상태). item 은 카드 payload 원본, included 는 실행 포함 여부.
 * uid 는 React key 전용 안정 식별자 — recipeId 가 null(삭제 레시피)이어도 순서변경 시 DOM 재사용이
 * 꼬이지 않도록 초기화 시점에 1회 부여한다(배열 인덱스를 key 로 쓰지 않기 위함).
 */
interface PlanEditRow {
  uid: string;
  item: PlanRecipeItem;
  included: boolean;
}

/**
 * 플랜 제안 카드 (2단계 — 스킵 + 순서변경 편집 가능, plan.md "제안 카드 편집").
 * - 💡 rationale + 편집 가능한 레시피 목록(↑/↓ 이동 · 체크박스 스킵 · 순서번호 재매김 · 값 미리보기).
 * - 조정은 카드 로컬 상태로만 관리(payload 불변). [자동 실행] 시 체크된 항목만 화면 순서대로 recipeIds 구성.
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

  // 편집 상태: 카드 payload(recipes) 원본 순서로 초기화, 전부 included=true.
  // recipeId 가 null(삭제된 레시피)이면 실행 불가 → 처음부터 스킵(체크 불가)으로 둔다.
  const [rows, setRows] = useState<PlanEditRow[]>(() =>
    (card.recipes ?? []).map((item, i) => ({
      uid: `plan-row-${i}`,
      item,
      included: item.recipeId != null,
    }))
  );
  // 순서변경/스킵 결과를 스크린리더에 알리는 문구 (aria-live)
  const [announce, setAnnounce] = useState("");

  const total = rows.length;
  const includedCount = rows.filter((r) => r.included).length;
  // 편집 잠금: 실행/취소 후에는 체크박스/이동 버튼을 잠근다.
  const locked = running || started;
  const disabled = locked || conversationId == null || includedCount === 0;

  const rowName = (item: PlanRecipeItem, order: number): string =>
    item.recipeName ?? (item.recipeId != null ? `레시피 #${item.recipeId}` : `레시피 ${order}`);

  /** 체크박스 토글 (스킵/포함). recipeId null 은 토글 불가 */
  const toggleIncluded = (index: number) => {
    if (locked) return;
    setRows((prev) =>
      prev.map((row, i) =>
        i === index && row.item.recipeId != null ? { ...row, included: !row.included } : row
      )
    );
  };

  /** ↑/↓ 이동 (배열 내 위치 swap). 전체 배열 기준 경계 */
  const move = (index: number, dir: -1 | 1) => {
    if (locked) return;
    setRows((prev) => {
      const target = index + dir;
      // 최신 상태 기준으로 경계를 재확인(연속 클릭에도 안전)
      if (target < 0 || target >= prev.length) return prev;
      const next = [...prev];
      [next[index], next[target]] = [next[target], next[index]];
      // announce 는 화면에 보이는 "실행 순서번호"(included 항목만 재매김)를 말한다.
      // 배열 물리 위치(target+1)를 쓰면 앞에 스킵 행이 있을 때 화면 번호와 어긋나므로,
      // 이동한 행 앞의 included 개수 +1 로 계산한다. 스킵 행 자체는 순서번호가 없다.
      const movedRow = next[target];
      if (movedRow.included) {
        const orderNo = next.slice(0, target).filter((r) => r.included).length + 1;
        setAnnounce(`${rowName(movedRow.item, target + 1)}을 ${orderNo}번째로 이동했습니다`);
      } else {
        setAnnounce(`${rowName(movedRow.item, target + 1)}을 이동했습니다 (실행에서 제외된 항목)`);
      }
      return next;
    });
  };

  // 실행에 사용할 recipeIds: 체크된 항목만, 현재 배열 순서대로. recipeId null 은 제외.
  const buildRecipeIds = (): number[] =>
    rows
      .filter((r) => r.included && r.item.recipeId != null)
      .map((r) => r.item.recipeId as number);

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
    // 체크된 항목만, 화면 순서대로 recipeIds 구성 (스킵/순서변경 반영). plan.md "BE 변경 없음".
    const recipeIds = buildRecipeIds();
    if (recipeIds.length === 0) return;
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

  // included 항목에만 1,2,3… 순서번호 재매김 (스킵 행은 번호 미부여)
  let orderCounter = 0;

  return (
    <div className="plan-card" role="group" aria-label="실행 계획 제안">
      {/* 순서변경/스킵 결과를 스크린리더에 알리는 영역 */}
      <div className="sr-only" role="status" aria-live="polite">
        {announce}
      </div>

      <div className="plan-card__title">📋 실행 계획</div>

      {card.rationale && (
        <div className="plan-rationale">
          <span className="plan-rationale__icon" aria-hidden>
            💡
          </span>
          <span>{card.rationale}</span>
        </div>
      )}

      {/* 헤더 카운터: 스킵이 있으면 "전체 N단계 중 M단계 실행", 없으면 간결히 "(N단계)" */}
      <div className="plan-counter">
        {includedCount === total
          ? `전체 ${total}단계`
          : `전체 ${total}단계 중 ${includedCount}단계 실행`}
      </div>

      <div className="plan-card__recipes">
        {rows.map((row, idx) => {
          const order = row.included ? ++orderCounter : null;
          return (
            <PlanRecipeRow
              key={row.uid}
              recipe={row.item}
              included={row.included}
              order={order}
              name={rowName(row.item, idx + 1)}
              canToggle={row.item.recipeId != null && !locked}
              canMoveUp={idx > 0 && !locked}
              canMoveDown={idx < rows.length - 1 && !locked}
              onToggle={() => toggleIncluded(idx)}
              onMoveUp={() => move(idx, -1)}
              onMoveDown={() => move(idx, 1)}
            />
          );
        })}
        {rows.length === 0 && <span style={descStyle}>플랜 항목이 없습니다</span>}
      </div>

      {/* 최소 1개 선택 가드 안내 */}
      {includedCount === 0 && total > 0 && (
        <div className="plan-card__guard">최소 1개 선택해야 실행할 수 있어요.</div>
      )}

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

interface PlanRecipeRowProps {
  recipe: PlanRecipeItem;
  /** 실행 포함 여부 (스킵이면 false) */
  included: boolean;
  /** 재매김된 순서번호 (스킵이면 null → 번호 미부여) */
  order: number | null;
  /** 표시명 (aria-label/이름용, 상위에서 계산) */
  name: string;
  canToggle: boolean;
  canMoveUp: boolean;
  canMoveDown: boolean;
  onToggle: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
}

/**
 * 플랜 제안 레시피 한 줄 (편집 가능): ↑/↓ 이동 + 체크박스 + 순서번호 + 이름 + 서비스 뱃지 + 값 미리보기.
 * 스킵 행은 plan-recipe--skipped + "(제외됨)" + 순서번호 미부여.
 */
function PlanRecipeRow({
  recipe,
  included,
  order,
  name,
  canToggle,
  canMoveUp,
  canMoveDown,
  onToggle,
  onMoveUp,
  onMoveDown,
}: PlanRecipeRowProps) {
  const preview = recipe.inputPreview ?? [];

  return (
    <div className={`plan-recipe${included ? "" : " plan-recipe--skipped"}`}>
      <div className="plan-recipe__head">
        <span className="plan-recipe__move">
          <button
            type="button"
            className="plan-recipe__move-btn"
            aria-label={`${name} 위로 이동`}
            disabled={!canMoveUp}
            onClick={onMoveUp}
          >
            ▲
          </button>
          <button
            type="button"
            className="plan-recipe__move-btn"
            aria-label={`${name} 아래로 이동`}
            disabled={!canMoveDown}
            onClick={onMoveDown}
          >
            ▼
          </button>
        </span>
        <input
          type="checkbox"
          className="plan-recipe__check"
          aria-label={`${name} 실행 포함`}
          checked={included}
          disabled={!canToggle}
          onChange={onToggle}
        />
        {order != null ? (
          <span className="plan-recipe__order">{order}</span>
        ) : (
          <span className="plan-recipe__order plan-recipe__order--empty" aria-hidden />
        )}
        <span className="plan-recipe__name">{name}</span>
        {!included && <span className="plan-recipe__excluded">(제외됨)</span>}
        {recipe.serviceName && (
          <span className="badge badge--neutral" style={{ marginLeft: "auto" }}>
            {recipe.serviceName}
          </span>
        )}
      </div>
      {/* 스킵 행은 값 미리보기 숨김 (디자인 Case 11: 제외 행은 head 만) */}
      {included && (
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
      )}
    </div>
  );
}
