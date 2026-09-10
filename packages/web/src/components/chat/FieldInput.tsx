// 액션 피커 / 플랜 값 편집 공용 필드 입력.
// ActionPicker 와 PlanCard(값 사전 편집)가 동일 스키마(ActionPickerVariable)로 폼을 렌더하므로
// 중복 구현을 피하기 위해 분리한다. 기존 ActionPicker 의 동작을 100% 보존한다.

import type { ActionPickerVariable } from "../../api/types";

/** 변수 초기값: default 우선, 없으면 타입별 빈값 */
export function initialValue(v: ActionPickerVariable): unknown {
  if (v.default != null) return v.default;
  if (v.type === "number") return "";
  if (v.type === "checkbox") return false;
  return "";
}

/**
 * select/radio 옵션 정규화.
 * BE 레시피 스키마는 옵션을 문자열 배열(["CARD","BANK"])로 저장하지만,
 * 렌더는 {label,value} 객체를 기대한다. 문자열이면 label=value 로 승격하고,
 * 이미 객체면 그대로 사용한다. (둘 다 안전하게 처리)
 */
function normalizeOptions(
  options: ActionPickerVariable["options"],
): { label: string; value: string }[] {
  return (options ?? []).map((opt) =>
    typeof opt === "string" ? { label: opt, value: opt } : opt,
  );
}

/** 변수 타입별 입력 렌더 */
export function FieldInput({
  variable,
  value,
  onChange,
  idPrefix = "ap",
}: {
  variable: ActionPickerVariable;
  value: unknown;
  onChange: (value: unknown) => void;
  /** input id 접두어 (동일 화면에 여러 폼이 있을 때 id 충돌 방지). 기본 "ap" */
  idPrefix?: string;
}) {
  const id = `${idPrefix}-${variable.key}`;
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
          {normalizeOptions(variable.options).map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      );
    case "radio":
      return (
        <div className="radio-group">
          {normalizeOptions(variable.options).map((opt) => (
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
