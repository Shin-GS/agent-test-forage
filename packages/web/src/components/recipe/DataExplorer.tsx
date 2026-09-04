// 스텝 편집 2단 레이아웃의 우측 데이터 탐색기.
// - 사용자 입력 변수(userInput.*)
// - 이전 스텝 extract/output 변수
// - (선택) 현재 API 스키마 필드
// 항목 클릭 시 onPick 콜백으로 참조 문자열 삽입 (예: "userInput.productId", "step1.email").

import type { RecipeVariable } from "../../api/types";
import type { StepVariableGroup } from "./recipeForm";

export interface SchemaField {
  name: string;
  hint?: string;
}

interface DataExplorerProps {
  userVariables: RecipeVariable[];
  priorSteps: StepVariableGroup[];
  /** 현재 스텝의 API 스키마 필드 (있으면 표시, 읽기 전용) */
  schemaFields?: SchemaField[];
  /** 참조 문자열 클릭 시 */
  onPick?: (reference: string) => void;
}

export function DataExplorer({
  userVariables,
  priorSteps,
  schemaFields,
  onPick,
}: DataExplorerProps) {
  const hasPrior = priorSteps.some((g) => g.variables.length > 0);

  return (
    <div className="data-explorer" aria-label="데이터 탐색기">
      {/* 사용자 입력 */}
      <div className="data-explorer__group">
        <div className="data-explorer__title">📂 사용자 입력</div>
        {userVariables.length === 0 ? (
          <div className="data-explorer__empty">변수 없음</div>
        ) : (
          userVariables
            .filter((v) => v.key)
            .map((v) => {
              const ref = `userInput.${v.key}`;
              return (
                <button
                  key={v.key}
                  type="button"
                  className="data-explorer__item"
                  onClick={() => onPick?.(ref)}
                >
                  {ref}
                </button>
              );
            })
        )}
      </div>

      {/* 이전 스텝 변수 */}
      {priorSteps.length === 0 ? (
        <div className="data-explorer__group data-explorer__group--muted">
          <div className="data-explorer__title">📂 이전 스텝 없음</div>
          <div className="data-explorer__empty">첫 번째 스텝입니다</div>
        </div>
      ) : (
        priorSteps.map((group) => (
          <div className="data-explorer__group" key={group.stepIndex}>
            <div className="data-explorer__title">
              📂 스텝 {group.stepIndex + 1}: {group.stepName}
            </div>
            {group.variables.length === 0 ? (
              <div className="data-explorer__empty">추출 변수 없음</div>
            ) : (
              group.variables.map((variable) => {
                const ref = `step${group.stepIndex + 1}.${variable}`;
                return (
                  <button
                    key={variable}
                    type="button"
                    className="data-explorer__item"
                    onClick={() => onPick?.(ref)}
                  >
                    {variable}
                  </button>
                );
              })
            )}
          </div>
        ))
      )}
      {!hasPrior && priorSteps.length > 0 && null}

      {/* 현재 API 스키마 */}
      {schemaFields && schemaFields.length > 0 && (
        <div className="data-explorer__group data-explorer__group--schema">
          <div className="data-explorer__title">📋 현재 API 스키마</div>
          {schemaFields.map((f) => (
            <div key={f.name} className="data-explorer__item data-explorer__item--readonly">
              {f.name}
              {f.hint ? ` (${f.hint})` : ""}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
