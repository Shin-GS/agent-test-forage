// 요청 필드 매핑 / 서브레시피 입력 매핑 공통 테이블.
// 값 소스 4종(이전 스텝 결과 / 사용자 입력 / 직접 입력 / AI 생성) 선택 + 매핑 값 입력.
// authoring.md ③ 값 소스 표 참조.

import type { FieldMapping, MappingSourceType } from "../../api/types";
import { newFieldMapping } from "./recipeForm";

const SOURCE_OPTIONS: { value: MappingSourceType; label: string }[] = [
  { value: "prev_step", label: "이전 스텝 결과" },
  { value: "user_input", label: "사용자 입력" },
  { value: "literal", label: "직접 입력" },
  { value: "ai_generate", label: "AI 생성" },
];

interface FieldMappingTableProps {
  mappings: FieldMapping[];
  onChange: (next: FieldMapping[]) => void;
  /** 필드명 편집 가능 여부 (서브레시피 변수는 편집 가능, API 스키마 필드는 고정) */
  editableField?: boolean;
  /** 값 입력 필드 하이라이트할 인덱스 (유효성 에러) */
  errorIndexes?: number[];
  /** 필드명 열 추가 버튼 표시 (서브레시피 입력 매핑) */
  allowAdd?: boolean;
  /** 값 입력 필드가 포커스될 때 (데이터 탐색기 클릭 삽입 대상 추적) */
  onValueFocus?: (index: number) => void;
}

export function FieldMappingTable({
  mappings,
  onChange,
  editableField = false,
  errorIndexes = [],
  allowAdd = false,
  onValueFocus,
}: FieldMappingTableProps) {
  function update(index: number, patch: Partial<FieldMapping>) {
    onChange(mappings.map((m, i) => (i === index ? { ...m, ...patch } : m)));
  }
  function remove(index: number) {
    onChange(mappings.filter((_, i) => i !== index));
  }
  function add() {
    onChange([...mappings, newFieldMapping()]);
  }

  if (mappings.length === 0 && !allowAdd) {
    return <p className="recipe-hint">요청 필드가 없습니다 (엔드포인트를 선택하세요).</p>;
  }

  return (
    <>
      <table className="mapping-table">
        <tbody>
          {mappings.map((m, index) => {
            const isError = errorIndexes.includes(index);
            return (
              <tr key={m._uid ?? index} className={isError ? "mapping-table__row--error" : undefined}>
                <td>
                  {editableField ? (
                    <input
                      className="input input--sm"
                      type="text"
                      aria-label={`매핑 필드명 ${index + 1}`}
                      placeholder="필드명"
                      value={m.field}
                      onChange={(e) => update(index, { field: e.target.value })}
                    />
                  ) : (
                    <span className="mapping-table__field">
                      {m.field}
                      {m.required && <span className="mapping-table__required">*</span>}
                    </span>
                  )}
                </td>
                <td>
                  <select
                    className="input input--sm"
                    aria-label={`${m.field || index + 1} 값 소스`}
                    value={m.source}
                    onChange={(e) =>
                      update(index, { source: e.target.value as MappingSourceType })
                    }
                  >
                    {SOURCE_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  {m.source === "ai_generate" ? (
                    <span className="recipe-hint">(자동)</span>
                  ) : (
                    <input
                      className="input input--sm"
                      type="text"
                      aria-label={`${m.field || index + 1} 매핑 값`}
                      placeholder={
                        m.source === "prev_step"
                          ? "step1.email"
                          : m.source === "user_input"
                            ? "userInput.productId"
                            : "고정 값"
                      }
                      value={m.value ?? ""}
                      onFocus={() => onValueFocus?.(index)}
                      onChange={(e) => update(index, { value: e.target.value })}
                    />
                  )}
                </td>
                <td className="data-table__actions">
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm"
                    style={{ color: "var(--color-error)" }}
                    aria-label={`매핑 ${index + 1} 삭제`}
                    onClick={() => remove(index)}
                  >
                    🗑️
                  </button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {allowAdd && (
        <button type="button" className="btn btn--ghost btn--sm" onClick={add} style={{ marginTop: "var(--space-2)" }}>
          + 매핑 추가
        </button>
      )}
    </>
  );
}
