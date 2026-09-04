// 서브레시피 스텝 편집기 (2단 레이아웃).
// 좌: 스텝명 + 대상 레시피 선택 + 입력 매핑 + 조건
// 우: 데이터 탐색기 (이전 스텝 변수 + 사용자 입력)
//
// 대상 레시피 목록은 recipesApi.list() 로 조회. 현재 편집 중인 레시피는 목록에서 제외(자기 참조 방지 힌트).

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { recipesApi } from "../../../api";
import type {
  FieldMapping,
  MappingSourceType,
  RecipeVariable,
  SubRecipeStep,
} from "../../../api/types";
import { DataExplorer } from "../DataExplorer";
import { FieldMappingTable } from "../FieldMappingTable";
import { newFieldMapping, type StepVariableGroup } from "../recipeForm";

/** 참조 문자열(userInput.x / stepN.x)로 매핑 source 추론 */
function sourceForReference(ref: string): MappingSourceType {
  return /^userInput\./.test(ref) ? "user_input" : "prev_step";
}

interface SubRecipeStepEditorProps {
  step: SubRecipeStep;
  onChange: (next: SubRecipeStep) => void;
  userVariables: RecipeVariable[];
  priorSteps: StepVariableGroup[];
  /** 편집 중인 레시피 ID (자기 자신 제외) */
  currentRecipeId?: number | null;
}

export function SubRecipeStepEditor({
  step,
  onChange,
  userVariables,
  priorSteps,
  currentRecipeId,
}: SubRecipeStepEditorProps) {
  const { data: recipes } = useQuery({
    queryKey: ["recipes", "all-for-subrecipe"],
    queryFn: () => recipesApi.list(),
  });

  const options = (recipes ?? []).filter((r) => r.id !== currentRecipeId);

  // 데이터 탐색기 클릭 삽입 대상: 마지막으로 포커스된 입력 매핑 행. 없으면 새 행 추가.
  const [activeMappingIndex, setActiveMappingIndex] = useState<number | null>(null);

  function patch(p: Partial<SubRecipeStep>) {
    onChange({ ...step, ...p });
  }

  /** 데이터 탐색기 변수 클릭 → 입력 매핑 행에 참조 삽입 */
  function handlePick(reference: string) {
    const source = sourceForReference(reference);
    const target = activeMappingIndex;
    if (target != null && target < step.inputMappings.length) {
      patch({
        inputMappings: step.inputMappings.map((m, i) =>
          i === target ? { ...m, source, value: reference } : m,
        ),
      });
    } else {
      const created = { ...newFieldMapping(), source, value: reference };
      patch({ inputMappings: [...step.inputMappings, created] });
      setActiveMappingIndex(step.inputMappings.length);
    }
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

        <div className="form-group">
          <label className="form-label">대상 레시피 *</label>
          <select
            className="input"
            value={step.recipeId ?? ""}
            onChange={(e) => patch({ recipeId: e.target.value === "" ? null : Number(e.target.value) })}
          >
            <option value="">레시피 선택...</option>
            {options.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
        </div>

        {/* 입력 매핑 */}
        <div>
          <label className="form-label" style={{ marginBottom: "var(--space-2)" }}>
            입력 매핑
          </label>
          <p className="recipe-hint" style={{ marginBottom: "var(--space-2)" }}>
            서브레시피의 사용자 입력 변수에 값을 매핑합니다
          </p>
          <FieldMappingTable
            mappings={step.inputMappings}
            onChange={(next: FieldMapping[]) => patch({ inputMappings: next })}
            editableField
            allowAdd
            onValueFocus={setActiveMappingIndex}
          />
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
