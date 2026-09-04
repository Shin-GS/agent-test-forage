// API 스텝 편집기 (2단 레이아웃).
// 좌: 스텝명 + 표시명(선택,폴백 안내) + 엔드포인트 선택 + 요청 필드 매핑 + Extract + 조건
// 우: 데이터 탐색기 (이전 스텝 변수 + 사용자 입력 + 현재 엔드포인트 정보)
//
// 스펙 endpoints 는 요청 스키마 필드를 제공하지 않으므로(method/path/summary만),
// 요청 필드 매핑은 사용자가 필드명을 직접 추가하는 방식으로 둔다.

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { specsApi } from "../../../api";
import type {
  ApiRecipeStep,
  ExtractDef,
  ExtractMethod,
  FieldMapping,
  MappingSourceType,
  RecipeVariable,
} from "../../../api/types";
import { DataExplorer } from "../DataExplorer";
import { FieldMappingTable } from "../FieldMappingTable";
import { newExtract, newFieldMapping, type StepVariableGroup } from "../recipeForm";

/** 참조 문자열(userInput.x / stepN.x)로 매핑 source 추론 */
function sourceForReference(ref: string): MappingSourceType {
  return /^userInput\./.test(ref) ? "user_input" : "prev_step";
}

const EXTRACT_METHODS: { value: ExtractMethod; label: string }[] = [
  { value: "jsonpath", label: "JSONPath" },
  { value: "full_response", label: "전체 응답" },
  { value: "status_code", label: "상태 코드" },
  { value: "header", label: "헤더" },
];

interface ApiStepEditorProps {
  step: ApiRecipeStep;
  onChange: (next: ApiRecipeStep) => void;
  userVariables: RecipeVariable[];
  priorSteps: StepVariableGroup[];
  /** 유효성: 값 소스 미지정 매핑 인덱스 */
  mappingErrorIndexes?: number[];
}

export function ApiStepEditor({
  step,
  onChange,
  userVariables,
  priorSteps,
  mappingErrorIndexes,
}: ApiStepEditorProps) {
  // 스펙 상세 (엔드포인트 목록) — apiSpecId 있을 때만
  const { data: spec } = useQuery({
    queryKey: ["spec", step.apiSpecId],
    queryFn: () => specsApi.getSpec(step.apiSpecId as number),
    enabled: step.apiSpecId != null,
  });
  const { data: specs } = useQuery({ queryKey: ["specs"], queryFn: () => specsApi.list() });

  const selectedEndpoint = spec?.endpoints.find((e) => e.id === step.endpointId) ?? null;

  // 데이터 탐색기 클릭 삽입 대상: 마지막으로 포커스된 요청 매핑 행. 없으면 새 행 추가.
  const [activeMappingIndex, setActiveMappingIndex] = useState<number | null>(null);

  function patch(p: Partial<ApiRecipeStep>) {
    onChange({ ...step, ...p });
  }

  function updateExtract(index: number, p: Partial<ExtractDef>) {
    patch({ extracts: step.extracts.map((e, i) => (i === index ? { ...e, ...p } : e)) });
  }

  /** 데이터 탐색기 변수 클릭 → 요청 매핑 행에 참조 삽입 */
  function handlePick(reference: string) {
    const source = sourceForReference(reference);
    const target = activeMappingIndex;
    if (target != null && target < step.requestMappings.length) {
      patch({
        requestMappings: step.requestMappings.map((m, i) =>
          i === target ? { ...m, source, value: reference } : m,
        ),
      });
    } else {
      // 포커스된 행이 없으면 새 매핑 행 추가
      const created = { ...newFieldMapping(), source, value: reference };
      patch({ requestMappings: [...step.requestMappings, created] });
      setActiveMappingIndex(step.requestMappings.length);
    }
  }

  return (
    <div className="split-layout">
      {/* 좌: 설정 */}
      <div className="step-editor__col">
        <div className="field-grid">
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
            <label className="form-label">서브도메인</label>
            <select
              className="input"
              value={step.apiSpecId ?? ""}
              onChange={(e) =>
                patch({
                  apiSpecId: e.target.value === "" ? null : Number(e.target.value),
                  endpointId: null,
                })
              }
            >
              <option value="">서비스 선택...</option>
              {(specs ?? []).map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="form-group">
          <label className="form-label">API</label>
          <select
            className="input"
            disabled={step.apiSpecId == null}
            value={step.endpointId ?? ""}
            onChange={(e) => patch({ endpointId: e.target.value === "" ? null : Number(e.target.value) })}
          >
            <option value="">{step.apiSpecId == null ? "서비스를 먼저 선택" : "엔드포인트 선택..."}</option>
            {(spec?.endpoints ?? []).map((ep) => (
              <option key={ep.id} value={ep.id}>
                {ep.method} {ep.path}
                {ep.summary ? ` — ${ep.summary}` : ""}
              </option>
            ))}
          </select>
        </div>

        {/* 스텝 표시명 (선택) */}
        <div className="form-group">
          <label className="form-label" htmlFor={`step-label-${step._uid ?? step.name}`}>
            스텝 표시명 (선택)
          </label>
          <input
            id={`step-label-${step._uid ?? step.name}`}
            className="input"
            type="text"
            placeholder={
              selectedEndpoint
                ? `비우면 자동 (${selectedEndpoint.summary ?? `${selectedEndpoint.method} ${selectedEndpoint.path}`})`
                : "비우면 자동 (API summary → method + path)"
            }
            value={step.label ?? ""}
            onChange={(e) => patch({ label: e.target.value })}
          />
          <span className="form-hint">
            비개발자 화면 표기에 사용됩니다. 미입력 시 폴백: API summary → method + path
          </span>
        </div>

        {/* 경로 파라미터 (Path Params) */}
        <div>
          <label className="form-label" style={{ marginBottom: "var(--space-2)" }}>
            경로 파라미터 (Path Params)
          </label>
          <span className="form-hint" style={{ display: "block", marginBottom: "var(--space-2)" }}>
            경로의 {"{id}"} 같은 변수를 매핑합니다 (예: GET /orders/{"{id}"}).
          </span>
          <FieldMappingTable
            mappings={step.pathParamMappings ?? []}
            onChange={(next: FieldMapping[]) => patch({ pathParamMappings: next })}
            editableField
            allowAdd
          />
        </div>

        {/* 요청 필드 매핑 */}
        <div>
          <label className="form-label" style={{ marginBottom: "var(--space-2)" }}>
            요청 필드 매핑
          </label>
          <FieldMappingTable
            mappings={step.requestMappings}
            onChange={(next: FieldMapping[]) => patch({ requestMappings: next })}
            editableField
            allowAdd
            errorIndexes={mappingErrorIndexes}
            onValueFocus={setActiveMappingIndex}
          />
        </div>

        {/* Extract */}
        <div>
          <label className="form-label" style={{ marginBottom: "var(--space-2)" }}>
            응답 추출 (Extract)
          </label>
          {step.extracts.length > 0 && (
            <table className="mapping-table">
              <tbody>
                {step.extracts.map((ex, index) => (
                  <tr key={ex._uid ?? index}>
                    <td>
                      <input
                        className="input input--sm"
                        type="text"
                        aria-label={`추출 변수명 ${index + 1}`}
                        placeholder="변수명"
                        value={ex.variable}
                        onChange={(e) => updateExtract(index, { variable: e.target.value })}
                      />
                    </td>
                    <td>
                      <select
                        className="input input--sm"
                        aria-label={`추출 방식 ${index + 1}`}
                        value={ex.method}
                        onChange={(e) =>
                          updateExtract(index, { method: e.target.value as ExtractMethod })
                        }
                      >
                        {EXTRACT_METHODS.map((m) => (
                          <option key={m.value} value={m.value}>
                            {m.label}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      {ex.method === "jsonpath" || ex.method === "header" ? (
                        <input
                          className="input input--sm"
                          type="text"
                          aria-label={`추출 값 ${index + 1}`}
                          placeholder={ex.method === "jsonpath" ? "$.data.id" : "헤더 이름"}
                          value={ex.value ?? ""}
                          onChange={(e) => updateExtract(index, { value: e.target.value })}
                        />
                      ) : (
                        <span className="recipe-hint">(자동)</span>
                      )}
                    </td>
                    <td className="data-table__actions">
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        style={{ color: "var(--color-error)" }}
                        aria-label={`추출 ${index + 1} 삭제`}
                        onClick={() =>
                          patch({ extracts: step.extracts.filter((_, i) => i !== index) })
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
            onClick={() => patch({ extracts: [...step.extracts, newExtract()] })}
          >
            + 추출 추가
          </button>
        </div>

        {/* 조건 */}
        <div className="form-group">
          <label className="form-label">조건 (선택)</label>
          <input
            className="input"
            type="text"
            placeholder="비우면 항상 실행 (예: {{step1.statusCode}} === 401)"
            value={step.condition ?? ""}
            onChange={(e) => patch({ condition: e.target.value })}
          />
        </div>
      </div>

      {/* 우: 데이터 탐색기 */}
      <DataExplorer
        userVariables={userVariables}
        priorSteps={priorSteps}
        onPick={handlePick}
        schemaFields={
          selectedEndpoint
            ? [{ name: `${selectedEndpoint.method} ${selectedEndpoint.path}`, hint: selectedEndpoint.summary ?? undefined }]
            : undefined
        }
      />
    </div>
  );
}
