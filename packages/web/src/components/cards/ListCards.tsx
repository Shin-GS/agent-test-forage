// 최소 렌더 카드 모음 (service_select / candidates / plan).
// 디자인 명세: 목록 항목은 .card--interactive, 버튼은 .btn.
// - service_select / candidates: 목록 표시만(클릭 동작은 후속 구현, 자리표시).
// - plan: 실구현 — 읽기 전용 미리보기 + [취소]/[자동 실행] (docs/design/web/chat.html Case 11).

/* eslint-disable @typescript-eslint/no-explicit-any */

import { useState } from "react";
import { ApiError, conversationsApi, executionsApi } from "../../api";
import type {
  ActionPickerVariable,
  CandidatesCard as CandidatesCardMeta,
  PlanCard as PlanCardMeta,
  PlanRecipeItem,
  ServiceSelectCard as ServiceSelectCardMeta,
} from "../../api/types";
import { runExecution } from "../../services/executionRunner";
import { applyRunResult } from "../../services/executionResult";
import { useChatStore } from "../../store/chatStore";
import { useToastStore } from "../../store/toastStore";
import { FieldInput, initialValue } from "../chat/FieldInput";
import { conversationLockMessage, useRecipeRun } from "./useRecipeRun";

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

/**
 * 서비스 선택 카드 — 각 서비스 버튼 클릭 시 대화방 대상 서비스를 설정한다.
 * - conversationsApi.updateService(convId, apiSpecId, partId) 호출 → BE 가 서비스 설정 + 카드 CONSUMED.
 * - 서비스 변경은 실행이 아니지만, 대화방이 처리 중(AI 응답/실행/입력 대기)이면 상태 꼬임 방지를 위해
 *   막고 안내한다(ExecutionModeCard 락 규칙과 동일).
 * - 성공 시: 목록 배지는 BE 가 발행하는 session_list_update → 목록 재조회로 반영된다
 *   (대화방 목록은 낙관적 UI 대상이 아님 — messaging.md). 카드는 started/consumed 로 비활성.
 * - payload: services[] = { apiSpecId, name, label }.
 */
export function ServiceSelectCard({
  card,
  partId,
  consumed = false,
}: {
  card: ServiceSelectCardMeta;
  /** 촉발 파트 id (서비스 설정 시 messageId 로 전달 → BE CONSUMED 처리) */
  partId?: number;
  /** 파트가 이미 CONSUMED/CANCELLED 인지 (새로고침 복원 시 재선택 차단) */
  consumed?: boolean;
}) {
  const conversationId = useChatStore((state) => state.currentConversationId);
  const conversationStatus = useChatStore((state) => state.conversationStatus);
  const showToast = useToastStore((state) => state.show);

  const [running, setRunning] = useState(false);
  // 이 세션에서 서비스를 고르면 재선택을 막는다(중복 방지). 새로고침 후에는 consumed 로 복원.
  const [started, setStarted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const services: any[] = card.services ?? [];
  const disabled = running || started || consumed || conversationId == null;

  const handleSelect = async (apiSpecId: number) => {
    if (disabled) return;
    // 대화방 락: 실행/응답/입력 대기 중이면 서비스 변경도 막는다(상태 꼬임 방지).
    if (conversationStatus !== "idle") {
      showToast(conversationLockMessage(conversationStatus), "warning");
      return;
    }
    const convId = conversationId!;
    setRunning(true);
    setError(null);
    try {
      // 촉발 파트 id 를 함께 보내 BE 가 이 카드 파트를 CONSUMED 처리하게 한다(messaging.md).
      await conversationsApi.updateService(convId, apiSpecId, partId);
      // 목록 배지는 BE 가 발행하는 session_list_update → 목록 재조회로 반영된다.
      // (대화방 목록은 낙관적 UI 대상이 아니다 — messaging.md. 목록의 진실은 서버.)
      showToast("서비스가 설정되었습니다", "success");
      setStarted(true);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        showToast("현재 대화방에 진행 중인 작업이 있어요. 완료 후 다시 시도해주세요.", "warning");
      } else {
        setError(err instanceof Error ? err.message : "서비스 설정에 실패했습니다");
      }
    } finally {
      setRunning(false);
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)", marginTop: "var(--space-2)" }}>
      <div style={{ display: "flex", flexWrap: "wrap", gap: "var(--space-2)", alignItems: "center" }}>
        {services.map((svc, idx) => {
          const apiSpecId: number | undefined = svc.apiSpecId ?? svc.id;
          const label: string = svc.label ?? svc.name ?? svc.title ?? String(svc);
          return (
            <button
              key={apiSpecId ?? idx}
              type="button"
              className="btn btn--secondary btn--sm"
              disabled={disabled || apiSpecId == null}
              onClick={() => apiSpecId != null && handleSelect(apiSpecId)}
            >
              {label}
            </button>
          );
        })}
        {services.length === 0 && <span style={descStyle}>표시할 서비스가 없습니다</span>}
        {(started || consumed) && <span className="badge badge--info">설정됨</span>}
      </div>
      {running && (
        <span style={{ fontSize: "var(--font-size-xs)", color: "var(--color-text-secondary)" }}>설정 중입니다...</span>
      )}
      {error && <span style={{ fontSize: "var(--font-size-xs)", color: "var(--color-error)" }}>{error}</span>}
    </div>
  );
}

/**
 * 후보 선택 카드 — 각 후보(레시피) 항목 클릭 시 그 레시피를 AUTO 로 실행한다.
 * ExecutionModeCard 와 동일한 실행 경로(useRecipeRun): 대화방 락 체크 + pendingInputs 분기 + 러너 구동.
 * - payload: candidates[] = { id(recipeId), name, description }.
 * - consumed(파트 소진)면 비활성. 실행 시작 후에도 재실행하지 않는다(started).
 */
export function CandidatesCard({
  card,
  partId,
  consumed = false,
}: {
  card: CandidatesCardMeta;
  /** 촉발 파트 id (실행 요청 messageId 로 전달 → BE CONSUMED 처리) */
  partId?: number;
  /** 파트가 이미 CONSUMED/CANCELLED 인지 (새로고침 복원 시 재실행 차단) */
  consumed?: boolean;
}) {
  const conversationId = useChatStore((state) => state.currentConversationId);
  const { running, started, error, startRun } = useRecipeRun(consumed);

  // BE candidates 카드 payload 는 recipes 키로 후보를 내려준다(ChatProcessor.candidatesCard:
  // {cardType:"candidates", recipes:[{id,name,description}]}). 과거 candidates 키도 방어적으로 폴백.
  const candidates: any[] = card.recipes ?? card.candidates ?? [];
  const disabled = running || started || consumed || conversationId == null;

  const handleSelect = (recipeId: number) => {
    if (disabled) return;
    void startRun({ recipeId, mode: "AUTO", partId });
  };

  return (
    <div style={listStyle}>
      {candidates.map((c, idx) => {
        const recipeId: number | undefined = c.id ?? c.recipeId;
        const name = c.name ?? c.recipeName ?? `후보 ${idx + 1}`;
        const desc = c.description ?? c.desc;
        return (
          <button
            key={recipeId ?? idx}
            type="button"
            className="card card--interactive"
            style={{ ...rowStyle, textAlign: "left", border: "none", cursor: disabled ? "default" : "pointer", width: "100%" }}
            disabled={disabled || recipeId == null}
            onClick={() => recipeId != null && handleSelect(recipeId)}
            aria-label={`${name} 실행`}
          >
            <div>
              <div style={nameStyle}>{name}</div>
              {desc && <div style={descStyle}>{desc}</div>}
            </div>
            {running && <span style={descStyle}>실행 중...</span>}
          </button>
        );
      })}
      {candidates.length === 0 && <span style={descStyle}>후보가 없습니다</span>}
      {(started || consumed) && <span className="badge badge--info">실행됨</span>}
      {error && <span style={{ fontSize: "var(--font-size-xs)", color: "var(--color-error)" }}>{error}</span>}
    </div>
  );
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
  /**
   * 사용자가 편집한 값 맵 (key→값). 미편집 key 는 아예 담지 않는다(프리필하지 않음).
   * 이렇게 두면 "실제 편집 여부"를 키 존재만으로 판별할 수 있고, 자동 실행 시 편집값만 전송한다.
   */
  inputs: Record<string, unknown>;
}

/** 변수 값이 "비어있는지"(미입력으로 취급) 판정. 빈 문자열/undefined/null 은 미입력 */
function isEmptyValue(value: unknown): boolean {
  return value === "" || value === undefined || value === null;
}

/**
 * 자동 실행에 보낼 편집값 맵 구성. 비어있지 않은 값만 담고, number 타입은 숫자로 변환한다
 * (ActionPicker 제출 규칙과 동일). 편집이 없으면 빈 맵({}).
 */
function normalizeInputs(
  variables: ActionPickerVariable[] | undefined,
  inputs: Record<string, unknown>
): Record<string, unknown> {
  const byKey = new Map((variables ?? []).map((v) => [v.key, v]));
  const out: Record<string, unknown> = {};
  for (const [key, raw] of Object.entries(inputs)) {
    if (isEmptyValue(raw)) continue;
    const variable = byKey.get(key);
    out[key] = variable?.type === "number" ? Number(raw) : raw;
  }
  return out;
}

/**
 * 플랜 제안 카드 (2단계 — 스킵 + 순서변경 편집 가능, plan.md "제안 카드 편집").
 * - 💡 rationale + 편집 가능한 레시피 목록(↑/↓ 이동 · 체크박스 스킵 · 순서번호 재매김 · 값 미리보기).
 * - 조정은 카드 로컬 상태로만 관리(payload 불변). [자동 실행] 시 체크된 항목만 화면 순서대로 recipeIds 구성.
 * - [취소] → 대화방 cancel API (FE 단독 해제 금지). [자동 실행] → plan-executions 시작 → 러너 구동.
 * - 🔗 이전 결과 예측 표시 금지(실행 전 확정 불가). 값 미리보기는 기본값(📌)만.
 */
export function PlanCard({
  card,
  partId,
  consumed = false,
}: {
  card: PlanCardMeta;
  /** 촉발 파트 id (실행 요청 messageId 로 전달 → BE CONSUMED 처리) */
  partId?: number;
  /** 파트가 이미 CONSUMED/CANCELLED 인지 (새로고침 복원 시 재실행 차단) */
  consumed?: boolean;
}) {
  const conversationId = useChatStore((state) => state.currentConversationId);
  const conversationStatus = useChatStore((state) => state.conversationStatus);
  const showToast = useToastStore((state) => state.show);

  const [running, setRunning] = useState(false);
  // 이 카드로 플랜을 시작하면(이 세션에서) 재클릭을 막는다(중복 방지). 로컬 상태만 사용.
  // 새로고침 후에는 파트 status(CONSUMED)로 복원되어 재활성화되지 않는다(consumed prop).
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
      inputs: {},
    }))
  );
  // [값 지정] 아코디언 펼침 상태 (uid 집합). 순서변경/스킵 시에도 uid 기준이라 자연 유지.
  const [expanded, setExpanded] = useState<Set<string>>(() => new Set());
  // 순서변경/스킵 결과를 스크린리더에 알리는 문구 (aria-live)
  const [announce, setAnnounce] = useState("");

  const total = rows.length;
  const includedCount = rows.filter((r) => r.included).length;
  // 편집 잠금: 실행/취소 후 또는 파트 소진(consumed) 시 체크박스/이동 버튼을 잠근다.
  const locked = running || started || consumed;
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

  /** [값 지정] 아코디언 펼침 토글. 잠금 상태에서도 열람은 허용하나, 입력은 disabled 로 막는다 */
  const toggleExpanded = (uid: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(uid)) next.delete(uid);
      else next.add(uid);
      return next;
    });
  };

  /** 특정 행의 변수 값 변경. 빈 값이면 key 를 제거(미편집으로 되돌림), 아니면 저장 */
  const setRowInput = (index: number, key: string, value: unknown) => {
    if (locked) return;
    setRows((prev) =>
      prev.map((row, i) => {
        if (i !== index) return row;
        const nextInputs = { ...row.inputs };
        if (isEmptyValue(value)) delete nextInputs[key];
        else nextInputs[key] = value;
        return { ...row, inputs: nextInputs };
      })
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

  // recipeInputs: buildRecipeIds 와 인덱스 1:1. 체크된 항목만 화면 순서대로, 편집값만 정규화.
  // 미편집 레시피는 {}. number 는 숫자 변환.
  const buildRecipeInputs = (): Array<Record<string, unknown>> =>
    rows
      .filter((r) => r.included && r.item.recipeId != null)
      .map((r) => normalizeInputs(r.item.variables, r.inputs));

  const handleCancel = async () => {
    if (running || started || consumed || conversationId == null) return;
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
    // 값 사전 편집 맵 (recipeIds 와 인덱스 1:1). 편집값만 담고 미편집은 {}.
    const recipeInputs = buildRecipeInputs();
    setRunning(true);
    setError(null);
    try {
      // 플랜은 항상 AUTO. recipeIds 1개면 BE 가 단일(SINGLE)로 수렴한다.
      const execution = await executionsApi.startPlan(convId, {
        recipeIds,
        recipeInputs,
        mode: "AUTO",
        // 촉발 파트를 CONSUMED 처리하도록 파트 id 전달(messaging.md — messageId 필드).
        messageId: partId,
      });

      // 시작 직후 첫 레시피에 pre-run 필수 입력 미충족이면 BE 가 pendingInputs 를 준다 → 액션 피커.
      if ((execution.pendingInputs?.length ?? 0) > 0) {
        useChatStore.getState().setActionPicker({
          conversationId: convId,
          executionId: execution.id,
          stepIndex: -1,
          variables: execution.pendingInputs ?? [],
          mode: "AUTO",
          partId: execution.actionPickerPartId ?? undefined,
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
          const hasVariables = (row.item.variables?.length ?? 0) > 0;
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
              // 값 편집: 변수 있고, 포함되고, recipeId 있고, 잠금 아닐 때만 토글 가능
              canEditValues={hasVariables && row.included && row.item.recipeId != null && !locked}
              expanded={expanded.has(row.uid)}
              edited={Object.keys(row.inputs).length > 0}
              inputs={row.inputs}
              onToggleExpanded={() => toggleExpanded(row.uid)}
              onInputChange={(key, value) => setRowInput(idx, key, value)}
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
          disabled={running || started || consumed || conversationId == null}
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
        {(started || consumed) && (
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
  /** [값 지정] 토글 가능 여부 (변수 있고 포함 + 미잠금) */
  canEditValues: boolean;
  /** 편집 폼 펼침 여부 */
  expanded: boolean;
  /** 사용자가 값을 편집했는지 ("값 지정됨" 뱃지 표시용) */
  edited: boolean;
  /** 현재 편집값 맵 (미편집 key 는 없음) */
  inputs: Record<string, unknown>;
  onToggleExpanded: () => void;
  onInputChange: (key: string, value: unknown) => void;
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
  canEditValues,
  expanded,
  edited,
  inputs,
  onToggleExpanded,
  onInputChange,
}: PlanRecipeRowProps) {
  const preview = recipe.inputPreview ?? [];
  const variables = recipe.variables ?? [];
  const hasVariables = variables.length > 0;
  // 편집 폼 컨테이너 id (aria-controls 연결용). recipeId 폴백으로 안정 문자열 구성.
  const formId = `plan-edit-${recipe.recipeId ?? name}`;

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
        {edited && included && <span className="badge badge--info">값 지정됨</span>}
        {recipe.serviceName && (
          <span className="badge badge--neutral" style={{ marginLeft: "auto" }}>
            {recipe.serviceName}
          </span>
        )}
        {/* [값 지정] 아코디언 토글: 변수 있는 행에만 노출. 스킵/삭제/잠금이면 비활성 */}
        {hasVariables && (
          <button
            type="button"
            className="plan-recipe__edit-toggle"
            style={recipe.serviceName ? undefined : { marginLeft: "auto" }}
            aria-expanded={expanded}
            aria-controls={formId}
            aria-label={`${name} 값 지정`}
            disabled={!canEditValues}
            onClick={onToggleExpanded}
          >
            값 지정{" "}
            <span
              className={`plan-recipe__edit-caret${expanded ? " plan-recipe__edit-caret--open" : ""}`}
              aria-hidden
            >
              ▸
            </span>
          </button>
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
      {/* 값 편집 폼: 펼쳤고 포함된 행에만. 각 변수 FieldInput 재사용(중복 구현 금지). */}
      {included && expanded && hasVariables && (
        <div className="plan-recipe__edit-form" id={formId} role="group" aria-label={`${name} 변수 입력`}>
          {variables.map((v) => {
            // 편집값이 있으면 그 값, 없으면 default 기반 초기값(표시용 폴백)
            const current = v.key in inputs ? inputs[v.key] : initialValue(v);
            return (
              <div className="form-group" key={v.key}>
                <label className="form-label" htmlFor={`plan-${recipe.recipeId ?? name}-${v.key}`}>
                  {v.label}
                  {v.required ? " *" : ""}
                </label>
                <FieldInput
                  variable={v}
                  value={current}
                  onChange={(val) => onInputChange(v.key, val)}
                  idPrefix={`plan-${recipe.recipeId ?? name}`}
                />
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
