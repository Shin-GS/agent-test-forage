// 스크립트 스텝 편집기 (2단 레이아웃).
// 좌: 스텝명 + 사용할 변수 체크박스 + 코드 textarea + 출력 변수 테이블 + 조건
// 우: 데이터 탐색기 (이전 스텝 변수 + 사용자 입력)

import type { RecipeVariable, ScriptOutputDef, ScriptRecipeStep } from "../../../api/types";
import { DataExplorer } from "../DataExplorer";
import { newOutput, type StepVariableGroup } from "../recipeForm";

interface ScriptStepEditorProps {
  step: ScriptRecipeStep;
  onChange: (next: ScriptRecipeStep) => void;
  userVariables: RecipeVariable[];
  priorSteps: StepVariableGroup[];
}

export function ScriptStepEditor({
  step,
  onChange,
  userVariables,
  priorSteps,
}: ScriptStepEditorProps) {
  function patch(p: Partial<ScriptRecipeStep>) {
    onChange({ ...step, ...p });
  }

  // 체크박스 후보: userInput.* + 이전 스텝 참조 (stepN.var)
  const candidates: string[] = [
    ...userVariables.filter((v) => v.key).map((v) => `userInput.${v.key}`),
    ...priorSteps.flatMap((g) => g.variables.map((v) => `step${g.stepIndex + 1}.${v}`)),
  ];

  function toggleVar(ref: string, checked: boolean) {
    const set = new Set(step.inputVariables);
    if (checked) set.add(ref);
    else set.delete(ref);
    patch({ inputVariables: Array.from(set) });
  }

  /** 데이터 탐색기 변수 클릭 → 사용할 변수 목록에 추가(체크) */
  function handlePick(reference: string) {
    if (step.inputVariables.includes(reference)) return;
    patch({ inputVariables: [...step.inputVariables, reference] });
  }

  function updateOutput(index: number, p: Partial<ScriptOutputDef>) {
    patch({ outputs: step.outputs.map((o, i) => (i === index ? { ...o, ...p } : o)) });
  }

  return (
    <div className="split-layout">
      <div className="step-editor__col">
        <div className="form-group">
          <label className="form-label">스텝명</label>
          <input
            className="input"
            type="text"
            value={step.name}
            onChange={(e) => patch({ name: e.target.value })}
          />
        </div>

        {/* 사용할 변수 */}
        <div>
          <label className="form-label" style={{ marginBottom: "var(--space-2)" }}>
            사용할 변수
          </label>
          {candidates.length === 0 ? (
            <p className="recipe-hint">사용 가능한 변수가 없습니다 (사용자 입력/이전 스텝 추가).</p>
          ) : (
            <div className="checkbox-list">
              {candidates.map((ref) => (
                <label key={ref} className="checkbox-list__item">
                  <input
                    type="checkbox"
                    checked={step.inputVariables.includes(ref)}
                    onChange={(e) => toggleVar(ref, e.target.checked)}
                  />
                  {ref}
                </label>
              ))}
            </div>
          )}
        </div>

        {/* 코드 */}
        <div className="form-group">
          <label className="form-label" htmlFor={`script-code-${step._uid ?? step.name}`}>
            코드
          </label>
          <textarea
            id={`script-code-${step._uid ?? step.name}`}
            className="textarea code-textarea"
            spellCheck={false}
            value={step.code}
            onChange={(e) => patch({ code: e.target.value })}
          />
        </div>

        {/* 출력 변수 */}
        <div>
          <label className="form-label" style={{ marginBottom: "var(--space-2)" }}>
            출력 변수
          </label>
          {step.outputs.length > 0 && (
            <table className="mapping-table">
              <tbody>
                {step.outputs.map((o, index) => (
                  <tr key={o._uid ?? index}>
                    <td>
                      <input
                        className="input input--sm"
                        type="text"
                        aria-label={`출력 변수명 ${index + 1}`}
                        placeholder="변수명"
                        value={o.variable}
                        onChange={(e) => updateOutput(index, { variable: e.target.value })}
                      />
                    </td>
                    <td>
                      <input
                        className="input input--sm"
                        type="text"
                        aria-label={`출력 변수 설명 ${index + 1}`}
                        placeholder="설명"
                        value={o.description ?? ""}
                        onChange={(e) => updateOutput(index, { description: e.target.value })}
                      />
                    </td>
                    <td className="data-table__actions">
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        style={{ color: "var(--color-error)" }}
                        aria-label={`출력 변수 ${index + 1} 삭제`}
                        onClick={() =>
                          patch({ outputs: step.outputs.filter((_, i) => i !== index) })
                        }
                      >
                        🗑️
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <button
            type="button"
            className="btn btn--ghost btn--sm"
            style={{ marginTop: "var(--space-2)" }}
            onClick={() => patch({ outputs: [...step.outputs, newOutput()] })}
          >
            + 출력 변수 추가
          </button>
        </div>

        {/* 조건 */}
        <div className="form-group">
          <label className="form-label">조건 (선택)</label>
          <input
            className="input"
            type="text"
            placeholder="비우면 항상 실행"
            value={step.condition ?? ""}
            onChange={(e) => patch({ condition: e.target.value })}
          />
        </div>
      </div>

      <DataExplorer userVariables={userVariables} priorSteps={priorSteps} onPick={handlePick} />
    </div>
  );
}
