// ② 사용자 입력 변수 섹션.
// 변수명 / 라벨 / 타입 / 필수 / 기본값 테이블 + 추가/삭제.
// 타입: text, number, textarea, select, radio, checkbox, date (authoring.md ②).

import type { RecipeVariable, RecipeVariableType } from "../../api/types";
import { newVariable } from "./recipeForm";

const VARIABLE_TYPES: RecipeVariableType[] = [
  "text",
  "number",
  "textarea",
  "select",
  "radio",
  "checkbox",
  "date",
];

interface VariablesSectionProps {
  variables: RecipeVariable[];
  onChange: (next: RecipeVariable[]) => void;
}

export function VariablesSection({ variables, onChange }: VariablesSectionProps) {
  function update(index: number, patch: Partial<RecipeVariable>) {
    onChange(variables.map((v, i) => (i === index ? { ...v, ...patch } : v)));
  }
  function remove(index: number) {
    onChange(variables.filter((_, i) => i !== index));
  }

  return (
    <div className="section">
      <div className="section__title">
        <span className="section__number">2</span> 사용자 입력 변수
      </div>
      <p className="recipe-hint" style={{ marginBottom: "var(--space-3)" }}>
        실행 시 액션 피커로 사용자에게 물어볼 값
      </p>
      <table className="data-table">
        <thead>
          <tr>
            <th>변수명</th>
            <th>라벨</th>
            <th>타입</th>
            <th>필수</th>
            <th>기본값</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {variables.length === 0 ? (
            <tr>
              <td colSpan={6} className="data-table__empty">
                변수가 없습니다
              </td>
            </tr>
          ) : (
            variables.map((v, index) => (
              <tr key={v._uid ?? index}>
                <td>
                  <input
                    className="input input--sm"
                    type="text"
                    aria-label={`변수명 ${index + 1}`}
                    placeholder="productId"
                    value={v.key}
                    onChange={(e) => update(index, { key: e.target.value })}
                  />
                </td>
                <td>
                  <input
                    className="input input--sm"
                    type="text"
                    aria-label={`라벨 ${index + 1}`}
                    placeholder="상품 ID"
                    value={v.label}
                    onChange={(e) => update(index, { label: e.target.value })}
                  />
                </td>
                <td>
                  <select
                    className="input input--sm"
                    aria-label={`타입 ${index + 1}`}
                    value={v.type}
                    onChange={(e) => update(index, { type: e.target.value as RecipeVariableType })}
                  >
                    {VARIABLE_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </td>
                <td style={{ textAlign: "center" }}>
                  <input
                    type="checkbox"
                    aria-label={`필수 ${index + 1}`}
                    checked={v.required ?? false}
                    onChange={(e) => update(index, { required: e.target.checked })}
                  />
                </td>
                <td>
                  <input
                    className="input input--sm"
                    type="text"
                    aria-label={`기본값 ${index + 1}`}
                    placeholder="(없음)"
                    value={v.default ?? ""}
                    onChange={(e) => update(index, { default: e.target.value })}
                  />
                </td>
                <td className="data-table__actions">
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm"
                    style={{ color: "var(--color-error)" }}
                    aria-label={`변수 ${index + 1} 삭제`}
                    onClick={() => remove(index)}
                  >
                    🗑️
                  </button>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
      <button
        type="button"
        className="btn btn--ghost btn--sm"
        style={{ marginTop: "var(--space-2)" }}
        onClick={() => onChange([...variables, newVariable()])}
      >
        + 변수 추가
      </button>
    </div>
  );
}
