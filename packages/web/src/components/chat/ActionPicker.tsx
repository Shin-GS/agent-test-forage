// 액션 피커 (디자인 명세 chat.html Case 4 + docs/specs/chat/action-picker.md).
// 실행 시작 시 필수 입력 미충족이면 표시된다(store.actionPicker, BE 가 WAITING_INPUT 으로 세움).
// - 변수 스키마(pendingInputs)로 필드 렌더: text/number/textarea/select/radio/checkbox/date
// - 기본값 프리필, required 표시
// - [취소] → POST /conversations/{id}/cancel (FE 단독으로 닫지 않음 — 서버 상태 해제)
// - [확인] → POST /action-picker/respond → 반환된 executing 실행으로 러너 구동
//
// 채팅 input 영역에 겹쳐 노출(액션 피커가 뜨면 입력창 잠금 — App/ChatInput 이 conversationStatus 로 처리).

import { useMemo, useState } from "react";
import { conversationsApi, executionsApi } from "../../api";
import type { ActionPickerVariable, ExecutionResponse } from "../../api/types";
import { runExecution } from "../../services/executionRunner";
import { applyRunResult } from "../../services/executionResult";
import { useChatStore } from "../../store/chatStore";

/** 변수 초기값: default 우선, 없으면 타입별 빈값 */
function initialValue(v: ActionPickerVariable): unknown {
  if (v.default != null) return v.default;
  if (v.type === "number") return "";
  if (v.type === "checkbox") return false;
  return "";
}

export function ActionPicker() {
  const actionPicker = useChatStore((state) => state.actionPicker);
  const currentConversationId = useChatStore((state) => state.currentConversationId);
  const setActionPicker = useChatStore((state) => state.setActionPicker);

  const variables = actionPicker?.variables ?? [];
  const [values, setValues] = useState<Record<string, unknown>>(() => {
    const init: Record<string, unknown> = {};
    for (const v of variables) init[v.key] = initialValue(v);
    return init;
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 필수 미입력 검사 (제출 버튼 활성화용)
  const canSubmit = useMemo(() => {
    return variables.every((v) => {
      if (!v.required) return true;
      const val = values[v.key];
      return val !== "" && val != null;
    });
  }, [variables, values]);

  // 현재 대화방의 액션 피커일 때만 표시
  if (!actionPicker || actionPicker.conversationId !== currentConversationId) {
    return null;
  }

  const setField = (key: string, value: unknown) => setValues((prev) => ({ ...prev, [key]: value }));

  const handleCancel = async () => {
    if (submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      // FE 단독으로 닫지 않는다 — 서버가 상태를 idle 로 해제(SSE 로 전파). action-picker.md
      await conversationsApi.cancel(actionPicker.conversationId);
      setActionPicker(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "취소에 실패했습니다");
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = async () => {
    if (submitting || !canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      // number 필드는 숫자로 변환해 전송
      const payloadValues: Record<string, unknown> = {};
      for (const v of variables) {
        const raw = values[v.key];
        payloadValues[v.key] = v.type === "number" && raw !== "" ? Number(raw) : raw;
      }

      const execution: ExecutionResponse = await executionsApi.respondActionPicker({
        executionId: actionPicker.executionId,
        stepIndex: actionPicker.stepIndex,
        values: payloadValues,
      });

      // 서버가 executing 으로 전이한 실행을 러너로 구동. 액션 피커는 닫는다(언마운트).
      const mode = actionPicker.mode;
      setActionPicker(null);
      const result = await runExecution(execution, { mode });
      // 재개 실행에서도 401(인증 필요)이 날 수 있다 → 최초 실행과 동일하게 결과를 처리(인증 안내 카드).
      // store 액션만 사용하므로 언마운트 이후 호출돼도 안전하다.
      await applyRunResult(execution, result, mode);
    } catch (err) {
      // respond 검증 실패(400: 여전히 미충족 등) — 서버가 WAITING_INPUT 유지, 카드도 유지하고 에러만 표시.
      // (이 경로는 setActionPicker(null) 이전이라 컴포넌트가 마운트 상태 → 로컬 setState 안전)
      setError(err instanceof Error ? err.message : "입력 제출에 실패했습니다");
      setSubmitting(false);
    }
  };

  return (
    <div className="action-picker-area">
      <div className="action-picker">
        <div className="action-picker__title">입력 정보</div>
        <div className="action-picker__fields">
          {variables.map((v) => (
            <div className="form-group" key={v.key}>
              <label className="form-label" htmlFor={`ap-${v.key}`}>
                {v.label}
                {v.required ? " *" : ""}
              </label>
              <FieldInput
                variable={v}
                value={values[v.key]}
                onChange={(val) => setField(v.key, val)}
              />
            </div>
          ))}
        </div>

        {error && <div className="form-error" style={{ marginTop: "var(--space-2)" }}>{error}</div>}

        <div className="action-picker__actions">
          <button type="button" className="btn btn--ghost" onClick={handleCancel} disabled={submitting}>
            취소
          </button>
          <button type="button" className="btn btn--primary" onClick={handleSubmit} disabled={submitting || !canSubmit}>
            {submitting ? "처리 중..." : "확인"}
          </button>
        </div>
      </div>
    </div>
  );
}

/** 변수 타입별 입력 렌더 */
function FieldInput({
  variable,
  value,
  onChange,
}: {
  variable: ActionPickerVariable;
  value: unknown;
  onChange: (value: unknown) => void;
}) {
  const id = `ap-${variable.key}`;
  const common = { id, className: "input" };

  switch (variable.type) {
    case "textarea":
      return (
        <textarea
          {...common}
          rows={3}
          placeholder={variable.placeholder}
          value={String(value ?? "")}
          onChange={(e) => onChange(e.target.value)}
        />
      );
    case "number":
      return (
        <input
          {...common}
          type="number"
          placeholder={variable.placeholder}
          min={variable.min}
          max={variable.max}
          value={value == null ? "" : String(value)}
          onChange={(e) => onChange(e.target.value)}
        />
      );
    case "date":
      return (
        <input {...common} type="date" value={String(value ?? "")} onChange={(e) => onChange(e.target.value)} />
      );
    case "checkbox":
      return (
        <label className="radio-group__item">
          <input type="checkbox" checked={Boolean(value)} onChange={(e) => onChange(e.target.checked)} />
          {variable.placeholder ?? ""}
        </label>
      );
    case "select":
    case "search-select":
      return (
        <select {...common} value={String(value ?? "")} onChange={(e) => onChange(e.target.value)}>
          <option value="" disabled>
            {variable.placeholder ?? "선택하세요"}
          </option>
          {(variable.options ?? []).map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      );
    case "radio":
      return (
        <div className="radio-group">
          {(variable.options ?? []).map((opt) => (
            <label className="radio-group__item" key={opt.value}>
              <input
                type="radio"
                name={id}
                checked={String(value ?? "") === opt.value}
                onChange={() => onChange(opt.value)}
              />
              {opt.label}
            </label>
          ))}
        </div>
      );
    default: // text 등
      return (
        <input
          {...common}
          type="text"
          placeholder={variable.placeholder}
          value={String(value ?? "")}
          onChange={(e) => onChange(e.target.value)}
        />
      );
  }
}
