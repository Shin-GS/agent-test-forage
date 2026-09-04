// ③ 스텝 섹션.
// 스텝 카드 목록 (펼침/접힘, 순서 이동 위/아래, 삭제) + [+ API/스크립트/서브레시피 스텝].
// 스텝 타입별 편집기(ApiStepEditor/ScriptStepEditor/SubRecipeStepEditor)로 위임.

import { useState } from "react";
import type {
  ApiRecipeStep,
  RecipeStep,
  RecipeVariable,
  ScriptRecipeStep,
  SubRecipeStep,
} from "../../api/types";
import { newStep, priorStepVariables, stepTypeLabel, type StepType } from "./recipeForm";
import { ApiStepEditor } from "./steps/ApiStepEditor";
import { ScriptStepEditor } from "./steps/ScriptStepEditor";
import { SubRecipeStepEditor } from "./steps/SubRecipeStepEditor";

interface StepsSectionProps {
  steps: RecipeStep[];
  onChange: (next: RecipeStep[]) => void;
  userVariables: RecipeVariable[];
  currentRecipeId?: number | null;
  /** 유효성: stepIndex → 매핑 에러 인덱스 목록 */
  stepMappingErrors?: Record<number, number[]>;
  /** 유효성: 에러가 있는 stepIndex 집합 */
  errorStepIndexes?: number[];
}

export function StepsSection({
  steps,
  onChange,
  userVariables,
  currentRecipeId,
  stepMappingErrors,
  errorStepIndexes = [],
}: StepsSectionProps) {
  // 펼침 상태는 스텝의 안정 로컬 id(_uid) 기준. 순서이동/중간삭제에도 열린 카드가 유지된다.
  const [expandedUid, setExpandedUid] = useState<string | null>(steps[0]?._uid ?? null);

  function addStep(type: StepType) {
    const created = newStep(type, steps.length);
    onChange([...steps, created]);
    setExpandedUid(created._uid ?? null);
  }

  function updateStep(index: number, step: RecipeStep) {
    onChange(steps.map((s, i) => (i === index ? step : s)));
  }

  function removeStep(index: number) {
    const removed = steps[index];
    onChange(steps.filter((_, i) => i !== index));
    if (expandedUid === removed?._uid) setExpandedUid(null);
  }

  function move(index: number, dir: -1 | 1) {
    const target = index + dir;
    if (target < 0 || target >= steps.length) return;
    const next = [...steps];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  }

  return (
    <div className="section">
      <div className="section__title">
        <span className="section__number">3</span> 스텝 ({steps.length}개)
      </div>

      {steps.length === 0 && (
        <div className="recipe-empty">스텝을 추가하여 워크플로우를 구성하세요</div>
      )}

      {steps.map((step, index) => {
        const stepUid = step._uid ?? `idx-${index}`;
        const isOpen = expandedUid === stepUid;
        const isError = errorStepIndexes.includes(index);
        const priorSteps = priorStepVariables(steps, index);
        return (
          <div
            className="step-card"
            key={stepUid}
            style={isError ? { borderColor: "var(--color-error)" } : isOpen ? { borderColor: "var(--color-accent)" } : undefined}
          >
            <div
              className="step-card__header"
              style={isOpen ? { background: "var(--color-accent-subtle)" } : undefined}
            >
              <span className="step-card__number">{index + 1}</span>
              <span className="step-card__name">{step.name || "(이름 없음)"}</span>
              <span className="step-card__type">{stepTypeLabel(step.type)}</span>
              <div className="step-card__controls">
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  aria-label={`스텝 ${index + 1} 위로`}
                  disabled={index === 0}
                  onClick={() => move(index, -1)}
                >
                  ↑
                </button>
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  aria-label={`스텝 ${index + 1} 아래로`}
                  disabled={index === steps.length - 1}
                  onClick={() => move(index, 1)}
                >
                  ↓
                </button>
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  style={{ color: "var(--color-error)" }}
                  aria-label={`스텝 ${index + 1} 삭제`}
                  onClick={() => removeStep(index)}
                >
                  🗑️
                </button>
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  aria-label={isOpen ? `스텝 ${index + 1} 접기` : `스텝 ${index + 1} 펼치기`}
                  aria-expanded={isOpen}
                  onClick={() => setExpandedUid(isOpen ? null : stepUid)}
                >
                  {isOpen ? "▴" : "▾"}
                </button>
              </div>
            </div>

            {isOpen && (
              <div className="step-card__body">
                {step.type === "api" && (
                  <ApiStepEditor
                    step={step}
                    onChange={(s: ApiRecipeStep) => updateStep(index, s)}
                    userVariables={userVariables}
                    priorSteps={priorSteps}
                    mappingErrorIndexes={stepMappingErrors?.[index]}
                  />
                )}
                {step.type === "script" && (
                  <ScriptStepEditor
                    step={step}
                    onChange={(s: ScriptRecipeStep) => updateStep(index, s)}
                    userVariables={userVariables}
                    priorSteps={priorSteps}
                  />
                )}
                {step.type === "recipe" && (
                  <SubRecipeStepEditor
                    step={step}
                    onChange={(s: SubRecipeStep) => updateStep(index, s)}
                    userVariables={userVariables}
                    priorSteps={priorSteps}
                    currentRecipeId={currentRecipeId}
                  />
                )}
              </div>
            )}
          </div>
        );
      })}

      <div className="step-add-actions">
        <button type="button" className="btn btn--secondary btn--sm" onClick={() => addStep("api")}>
          + API 스텝
        </button>
        <button type="button" className="btn btn--secondary btn--sm" onClick={() => addStep("script")}>
          + 스크립트 스텝
        </button>
        <button type="button" className="btn btn--secondary btn--sm" onClick={() => addStep("recipe")}>
          + 서브레시피 스텝
        </button>
      </div>
    </div>
  );
}
