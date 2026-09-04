// ④ 결과 정의 섹션.
// 변수명(key) / 표시명(선택,label) / 소스 테이블 + 추가/삭제.
// 표시명 폴백: label 있으면 사람말, 비우면 원본 key. (structure.md 표시명 폴백 체인)

import type { ResultDefinitionItem } from "../../api/types";
import { newResultItem } from "./recipeForm";

interface ResultDefinitionSectionProps {
  items: ResultDefinitionItem[];
  onChange: (next: ResultDefinitionItem[]) => void;
}

export function ResultDefinitionSection({ items, onChange }: ResultDefinitionSectionProps) {
  function update(index: number, patch: Partial<ResultDefinitionItem>) {
    onChange(items.map((it, i) => (i === index ? { ...it, ...patch } : it)));
  }
  function remove(index: number) {
    onChange(items.filter((_, i) => i !== index));
  }

  return (
    <div className="section">
      <div className="section__title">
        <span className="section__number">4</span> 결과 정의
      </div>
      <p className="recipe-hint" style={{ marginBottom: "var(--space-3)" }}>
        실행 완료 후 저장할 값 (히스토리 표시 + 템플릿에서 사용). 표시명은 선택이며, 비우면 원본 key로 폴백합니다.
      </p>
      <table className="data-table">
        <thead>
          <tr>
            <th>변수명</th>
            <th>표시명 (선택)</th>
            <th>소스</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {items.length === 0 ? (
            <tr>
              <td colSpan={4} className="data-table__empty">
                결과 변수가 없습니다
              </td>
            </tr>
          ) : (
            items.map((it, index) => (
              <tr key={it._uid ?? index}>
                <td>
                  <input
                    className="input input--sm"
                    type="text"
                    aria-label={`결과 변수명 ${index + 1}`}
                    placeholder="orderId"
                    value={it.key}
                    onChange={(e) => update(index, { key: e.target.value })}
                  />
                </td>
                <td>
                  <input
                    className="input input--sm"
                    type="text"
                    aria-label={`결과 표시명 ${index + 1}`}
                    placeholder="비우면 key로 표기"
                    value={it.label ?? ""}
                    onChange={(e) => update(index, { label: e.target.value })}
                  />
                </td>
                <td>
                  <input
                    className="input input--sm"
                    type="text"
                    aria-label={`결과 소스 ${index + 1}`}
                    placeholder="step3.orderId"
                    value={it.source}
                    onChange={(e) => update(index, { source: e.target.value })}
                  />
                </td>
                <td className="data-table__actions">
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm"
                    style={{ color: "var(--color-error)" }}
                    aria-label={`결과 변수 ${index + 1} 삭제`}
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
        onClick={() => onChange([...items, newResultItem()])}
      >
        + 결과 변수 추가
      </button>
    </div>
  );
}
