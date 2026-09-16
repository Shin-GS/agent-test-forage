// API 스텝 편집기 (2단 레이아웃).
// 좌: 스텝명 + 표시명(선택,폴백 안내) + 엔드포인트 선택 + 요청 필드 매핑 + Extract + 조건
// 우: 데이터 탐색기 (이전 스텝 변수 + 사용자 입력 + 현재 엔드포인트 정보)
//
// 스펙 endpoints 는 요청 스키마 필드를 제공하지 않으므로(method/path/summary만),
// 요청 필드 매핑은 사용자가 필드명을 직접 추가하는 방식으로 둔다.

import { useEffect, useRef, useState } from "react";
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
import { parseOperationJson } from "../../../pages/admin/endpointOperation";
import { DataExplorer } from "../DataExplorer";
import { FieldMappingTable } from "../FieldMappingTable";
import { newExtract, newFieldMapping, newUid, type StepVariableGroup } from "../recipeForm";
import { useServiceOptions } from "../useServiceOptions";

/** 참조 문자열(userInput.x / stepN.x)로 매핑 source 추론 */
function sourceForReference(ref: string): MappingSourceType {
  return /^userInput\./.test(ref) ? "user_input" : "prev_step";
}

/**
 * 스키마 필드(이름+필수)를 기존 매핑에 병합한다(authoring.md 병합 원칙).
 * - 기존 매핑에 이미 있는 field 는 값·source·기본값을 그대로 보존한다(자동 나열이 편집을 덮어쓰지 않음).
 * - 스키마에만 있는 새 field 만 매핑 행으로 추가한다(source 기본값=직접 입력, value="").
 * - required 는 스키마 기준으로 갱신한다(필수 표시 `*` 정확성).
 * 스키마가 비어 있으면(폴백) 기존 매핑을 그대로 반환한다.
 */
function mergeSchemaFields(
  existing: FieldMapping[],
  schemaFields: { name: string; required: boolean }[],
): FieldMapping[] {
  if (schemaFields.length === 0) return existing;
  const byField = new Map(existing.map((m) => [m.field, m]));
  const merged: FieldMapping[] = existing.map((m) => {
    const schema = schemaFields.find((s) => s.name === m.field);
    return schema ? { ...m, required: schema.required } : m;
  });
  for (const s of schemaFields) {
    if (s.name && !byField.has(s.name)) {
      merged.push({
        _uid: newUid(),
        field: s.name,
        source: "literal",
        value: "",
        ...(s.required ? { required: true } : {}),
      });
    }
  }
  return merged;
}

/**
 * 스키마 필드로부터 매핑을 새로 만든다(API 교체 시 사용).
 * 기존 매핑을 보존하지 않고 스키마 필드만으로 초기화한다(source=직접 입력, value 빈값).
 * 스키마가 비어 있으면(폴백) 빈 배열 → 수동 입력 안내로 이어진다.
 */
function schemaFieldsToMappings(
  schemaFields: { name: string; required: boolean }[],
): FieldMapping[] {
  return schemaFields
    .filter((s) => s.name)
    .map((s) => ({
      _uid: newUid(),
      field: s.name,
      source: "literal" as MappingSourceType,
      value: "",
      ...(s.required ? { required: true } : {}),
    }));
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
  // 스펙 상세 (엔드포인트 목록) — apiSpecId 있을 때만.
  // queryKey ["spec", id] 는 useServiceOptions 의 단건 조회와 캐시를 공유한다(중복 요청 없음).
  const { data: spec } = useQuery({
    queryKey: ["spec", step.apiSpecId],
    queryFn: () => specsApi.getSpec(step.apiSpecId as number),
    enabled: step.apiSpecId != null,
  });

  // 엔드포인트 상세(operationJson 포함) — 자동 필드/헤더 나열용. non-admin 도 조회 허용(authoring.md).
  // apiSpecId + endpointId 가 모두 있을 때만 조회. 실패해도 폴백(수동 입력)으로 편집이 막히지 않는다.
  const { data: endpointDetail, isError: endpointError } = useQuery({
    queryKey: ["endpoint", step.apiSpecId, step.endpointId],
    queryFn: () => specsApi.getEndpoint(step.apiSpecId as number, step.endpointId as number),
    enabled: step.apiSpecId != null && step.endpointId != null,
  });

  // ACTIVE 스펙 목록 + 현재 참조가 비활성/목록밖이면 보존 옵션 추가 (recipe-editor.md 정책)
  const { options: serviceOptions, deletedReference } = useServiceOptions(step.apiSpecId ?? null);

  const selectedEndpoint = spec?.endpoints.find((e) => e.id === step.endpointId) ?? null;

  // operationJson 파싱 결과(스키마 필드/헤더). null 이면 파싱 실패(폴백), undefined 면 아직 로드 전.
  const parsedOperation =
    endpointDetail != null ? parseOperationJson(endpointDetail.operationJson) : undefined;
  // 스키마가 없어(구 데이터/파싱 실패) 수동 입력 안내를 띄울지 여부
  const schemaUnavailable =
    step.endpointId != null && (endpointError || parsedOperation === null);

  // 데이터 탐색기 클릭 삽입 대상: 마지막으로 포커스된 요청 매핑 행. 없으면 새 행 추가.
  const [activeMappingIndex, setActiveMappingIndex] = useState<number | null>(null);

  // 자동 나열을 이미 반영한 endpointId. "초기 로드/스키마 지연 반영"(보존 병합)과
  // "사용자의 실제 API 교체"(초기화)를 구분하기 위한 기준값이다.
  //  - undefined: 아직 한 번도 자동 나열을 안 함(편집기 최초 마운트)
  //  - null / 숫자: 마지막으로 자동 나열을 수행한 endpointId
  const populatedForRef = useRef<number | null | undefined>(undefined);

  // API 교체로 방금 비워진 응답 추출 변수명(일시 배지). 다른 편집이 일어나면 사라진다.
  const [clearedExtractVars, setClearedExtractVars] = useState<string[]>([]);

  function patch(p: Partial<ApiRecipeStep>) {
    // 사용자가 다른 편집을 시작하면 "끊긴 참조" 일시 배지를 걷는다.
    if (clearedExtractVars.length > 0) setClearedExtractVars([]);
    onChange({ ...step, ...p });
  }

  // 스키마 로드/변경 시 자동 필드 나열.
  //  - 초기 로드(기존 레시피) 또는 같은 API에서 스키마가 뒤늦게 로드된 경우:
  //    기존 매핑을 보존하며 스키마 필드만 병합(mergeSchemaFields).
  //  - 사용자가 API(endpointId)를 실제로 다른 값으로 교체한 경우:
  //    요청/경로/헤더 매핑을 새 스키마로 초기화하고 응답 추출을 비운다.
  //    (condition/스텝명/표시명은 보존. 이후 스텝은 건드리지 않음 — 끊긴 참조는 배지로 안내)
  useEffect(() => {
    // endpointId 가 없으면(선택 해제) 자동 나열 대상이 없다. 기존 매핑은 그대로 둔다(교체 아님).
    if (step.endpointId == null) return;
    // endpointDetail 이 로드되기 전(undefined)에는 아무것도 하지 않는다.
    if (endpointDetail == null) return;
    // 로드된 상세가 "현재 endpointId" 에 대응할 때만 처리한다.
    // (endpointId 는 바뀌었는데 상세는 아직 이전 것인 로딩 중 프레임에서
    //  이전 API 스키마로 잘못 초기화되는 것을 방지 — endpointDetail.id 로 대조)
    if (endpointDetail.id !== step.endpointId) return;

    // parseOperationJson 이 null(파싱 실패)이면 스키마 필드는 빈 목록으로 취급한다
    // (교체 시엔 초기화되고, 폴백 안내로 수동 입력을 유도한다).
    const bodyFields = (parsedOperation?.requestBodyFields ?? []).map((f) => ({
      name: f.name,
      required: f.required,
    }));
    const pathFields = (parsedOperation?.pathParams ?? []).map((p) => ({
      name: p.name,
      required: p.required,
    }));
    const headerFields = (parsedOperation?.requestHeaders ?? []).map((h) => ({
      name: h.name,
      required: h.required,
    }));

    const prev = populatedForRef.current;
    const current = step.endpointId ?? null;
    // 실제 교체: 이전에 다른 endpointId 로 자동 나열을 했었고, 지금 값이 그와 다르다.
    // (prev === undefined 는 최초 로드이므로 교체가 아니라 보존 병합)
    const isSwap = prev !== undefined && prev !== current;

    if (isSwap) {
      const removedVars = (step.extracts ?? []).map((e) => e.variable).filter(Boolean);
      populatedForRef.current = current;
      setClearedExtractVars(removedVars);
      onChange({
        ...step,
        requestMappings: schemaFieldsToMappings(bodyFields),
        pathParamMappings: schemaFieldsToMappings(pathFields),
        headerMappings: schemaFieldsToMappings(headerFields),
        extracts: [],
      });
      return;
    }

    // 파싱 실패/빈 스키마(폴백)면 병합할 필드가 없으므로 기준값만 갱신하고 종료.
    if (!parsedOperation) {
      populatedForRef.current = current;
      return;
    }

    // 보존 병합(초기 로드 / 스키마 지연 반영)
    const nextRequest = mergeSchemaFields(step.requestMappings, bodyFields);
    const nextPath = mergeSchemaFields(step.pathParamMappings ?? [], pathFields);
    const nextHeaders = mergeSchemaFields(step.headerMappings ?? [], headerFields);

    populatedForRef.current = current;

    // 변경이 있을 때만 patch (무한 렌더 방지 — 길이/필드 구성이 실제로 달라졌는지 확인).
    const changed =
      nextRequest.length !== step.requestMappings.length ||
      nextPath.length !== (step.pathParamMappings ?? []).length ||
      nextHeaders.length !== (step.headerMappings ?? []).length ||
      nextRequest.some((m, i) => m.required !== step.requestMappings[i]?.required);
    if (changed) {
      onChange({
        ...step,
        requestMappings: nextRequest,
        pathParamMappings: nextPath,
        headerMappings: nextHeaders,
      });
    }
    // endpointId 변경(교체) 과 endpointDetail 로드(캐시 포함) 양쪽에 반응한다.
    // step 의 나머지 필드는 onChange 로 갱신되므로 의존성에서 제외(무한 렌더 방지).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step.endpointId, endpointDetail]);

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
              {serviceOptions.map((opt) => (
                <option key={opt.id} value={opt.id}>
                  {opt.label}
                </option>
              ))}
            </select>
            {deletedReference && (
              <span className="form-hint" style={{ color: "var(--color-error)" }}>
                참조 서비스를 찾을 수 없음(삭제됨). 서비스를 다시 선택해주세요.
              </span>
            )}
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
          {clearedExtractVars.length > 0 && (
            <div className="mapping-note mapping-note--warn" role="status">
              ⚠️ API를 바꿔 이 스텝의 요청·응답 매핑을 새 API 기준으로 초기화했습니다. 비워진 응답
              추출 변수(<strong>{clearedExtractVars.join(", ")}</strong>)를 참조하던 이후 스텝이 있다면
              참조를 확인해 주세요.
            </div>
          )}
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
            showDefault
          />
        </div>

        {/* 요청 필드 매핑 */}
        <div>
          <label className="form-label" style={{ marginBottom: "var(--space-2)" }}>
            요청 필드 매핑
          </label>
          {schemaUnavailable && (
            <span className="form-hint" style={{ display: "block", marginBottom: "var(--space-2)" }}>
              이 API는 상세 스키마가 없어 요청 필드를 수동으로 입력합니다. [+ 매핑 추가]로 필드를 추가하세요.
            </span>
          )}
          <FieldMappingTable
            mappings={step.requestMappings}
            onChange={(next: FieldMapping[]) => patch({ requestMappings: next })}
            editableField
            allowAdd
            showDefault
            errorIndexes={mappingErrorIndexes}
            onValueFocus={setActiveMappingIndex}
          />
        </div>

        {/* 요청 헤더 매핑 (요청 필드와 동일 패턴. 엔드포인트 헤더 정의가 있으면 자동 제시) */}
        <div>
          <label className="form-label" style={{ marginBottom: "var(--space-2)" }}>
            요청 헤더 매핑
          </label>
          <div className="mapping-note">
            🔐 인증 key 등 민감 헤더는 값 소스를 <strong>"사용자 입력"</strong>으로 두는 것을
            권장합니다. 직접 입력한 고정값은 레시피 정의에 그대로 저장됩니다. 인증·식별 헤더에는{" "}
            <strong>'AI 생성'</strong> 값 소스를 권장하지 않습니다.
          </div>
          <FieldMappingTable
            mappings={step.headerMappings ?? []}
            onChange={(next: FieldMapping[]) => patch({ headerMappings: next })}
            editableField
            allowAdd
            showDefault
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
        schemaFields={buildSchemaFields()}
      />
    </div>
  );

  /** 데이터 탐색기 "현재 API 스키마" 표시용 필드 구성: 엔드포인트 + 요청 필드 + 헤더(파싱 성공 시) */
  function buildSchemaFields() {
    if (!selectedEndpoint) return undefined;
    const fields: { name: string; hint?: string }[] = [
      {
        name: `${selectedEndpoint.method} ${selectedEndpoint.path}`,
        hint: selectedEndpoint.summary ?? undefined,
      },
    ];
    if (parsedOperation) {
      for (const f of parsedOperation.requestBodyFields) {
        fields.push({ name: f.name, hint: `${f.type}${f.required ? ", 필수" : ""}` });
      }
      for (const h of parsedOperation.requestHeaders) {
        fields.push({ name: h.name, hint: `헤더${h.required ? ", 필수" : ""}` });
      }
    }
    return fields;
  }
}
