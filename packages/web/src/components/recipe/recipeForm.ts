// 레시피 편집 폼 상태 모델 + 헬퍼.
// RecipeDetail(서버) ↔ 폼 상태 변환, 빈 스텝/변수 생성, create/update 바디 빌드.
// 스텝/변수/결과정의는 authoring.md/structure.md 스키마를 따른다.

import type {
  ApiRecipeStep,
  ExtractDef,
  ExtractMethod,
  FieldMapping,
  MappingSourceType,
  RecipeCreateRequest,
  RecipeDetail,
  RecipeStep,
  RecipeUpdateRequest,
  RecipeVariable,
  ResultDefinitionItem,
  ScriptOutputDef,
  ScriptRecipeStep,
  SubRecipeStep,
} from "../../api/types";

/** 폼 전체 상태 */
export interface RecipeFormState {
  name: string;
  description: string;
  apiSpecId: number | null;
  visibility: "COMMON" | "PRIVATE";
  tags: string[];
  variables: RecipeVariable[];
  steps: RecipeStep[];
  resultDefinition: ResultDefinitionItem[];
  resultTemplate: string;
}

export type StepType = RecipeStep["type"];

// --- 안정 로컬 id (major4) ---
// 배열 항목의 React key/expanded 추적용. 서버 직렬화 시 제외한다(_uid).
let uidCounter = 0;
export function newUid(): string {
  uidCounter += 1;
  const rand =
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? crypto.randomUUID()
      : Math.random().toString(36).slice(2);
  return `uid-${uidCounter}-${rand}`;
}

/** 새 레시피 빈 폼 */
export function emptyForm(): RecipeFormState {
  return {
    name: "",
    description: "",
    apiSpecId: null,
    visibility: "COMMON",
    tags: [],
    variables: [],
    steps: [],
    resultDefinition: [],
    resultTemplate: "",
  };
}

/** RecipeDetail → 폼 상태 (편집 로드) */
export function detailToForm(detail: RecipeDetail): RecipeFormState {
  const visibilityCode = (detail.visibility?.code ?? "COMMON").toUpperCase();
  const apiSpecId = detail.apiSpecId ?? null;
  return {
    name: detail.name ?? "",
    description: detail.description ?? "",
    apiSpecId,
    visibility: visibilityCode === "PRIVATE" ? "PRIVATE" : "COMMON",
    tags: Array.isArray(detail.tags) ? detail.tags : [],
    variables: Array.isArray(detail.variables)
      ? detail.variables.map((v) => ({ ...v, _uid: newUid() }))
      : [],
    steps: Array.isArray(detail.steps)
      ? detail.steps.map((raw) => serverStepToForm(raw as unknown, apiSpecId))
      : [],
    resultDefinition: Array.isArray(detail.resultDefinition)
      ? detail.resultDefinition.map((it) => ({ ...it, _uid: newUid() }))
      : [],
    resultTemplate: detail.resultTemplate ?? "",
  };
}

/** 폼 상태 → 생성 요청 바디 (ownerUserId 는 서버 세션에서 도출) */
export function formToCreateRequest(form: RecipeFormState): RecipeCreateRequest {
  return {
    apiSpecId: form.apiSpecId,
    name: form.name.trim(),
    description: form.description.trim(),
    visibility: form.visibility,
    tags: form.tags,
    variables: form.variables.map(stripUid),
    steps: form.steps.map(formStepToServer),
    resultDefinition: form.resultDefinition.map(stripUid),
    resultTemplate: form.resultTemplate.trim() || null,
  };
}

/** 폼 상태 → 수정 요청 바디 (apiSpecId/ownerUserId 없음) */
export function formToUpdateRequest(form: RecipeFormState): RecipeUpdateRequest {
  return {
    name: form.name.trim(),
    description: form.description.trim(),
    visibility: form.visibility,
    tags: form.tags,
    variables: form.variables.map(stripUid),
    steps: form.steps.map(formStepToServer),
    resultDefinition: form.resultDefinition.map(stripUid),
    resultTemplate: form.resultTemplate.trim() || null,
  };
}

/** 서버 직렬화 시 FE 전용 _uid 필드 제거 */
function stripUid<T extends { _uid?: string }>(item: T): Omit<T, "_uid"> {
  const { _uid, ...rest } = item;
  void _uid;
  return rest;
}

// --- 빈 항목 팩토리 ---

export function newVariable(): RecipeVariable {
  return { _uid: newUid(), key: "", label: "", type: "text", required: false, default: "" };
}

export function newResultItem(): ResultDefinitionItem {
  return { _uid: newUid(), key: "", label: "", source: "" };
}

export function newExtract(): ExtractDef {
  return { _uid: newUid(), variable: "", method: "jsonpath", value: "" };
}

export function newFieldMapping(field = ""): FieldMapping {
  return { _uid: newUid(), field, source: "prev_step", value: "" };
}

export function newOutput(): ScriptOutputDef {
  return { _uid: newUid(), variable: "", description: "" };
}

export function newStep(type: StepType, index: number): RecipeStep {
  const name = `스텝 ${index + 1}`;
  if (type === "api") {
    const step: ApiRecipeStep = {
      _uid: newUid(),
      type: "api",
      name,
      label: "",
      apiSpecId: null,
      endpointId: null,
      pathParamMappings: [],
      requestMappings: [],
      extracts: [],
      condition: "",
    };
    return step;
  }
  if (type === "script") {
    const step: ScriptRecipeStep = {
      _uid: newUid(),
      type: "script",
      name,
      inputVariables: [],
      code: "function execute(context) {\n  return {};\n}",
      outputs: [],
      condition: "",
    };
    return step;
  }
  const step: SubRecipeStep = {
    _uid: newUid(),
    type: "recipe",
    name,
    recipeId: null,
    inputMappings: [],
    condition: "",
  };
  return step;
}

// ---------------------------------------------------------------------------
// 서버 스텝 JSON ↔ FE 폼 스텝 변환
//
// 서버 저장 스키마(자유 JSON)는 FE 폼 스키마와 형태가 다르다.
//  - API 스텝: body(객체 맵) / extract(객체 맵: 변수명→JSONPath) / pathParams(객체 맵)
//  - FE 폼:    requestMappings(배열) / extracts(배열: {variable,method,value})
// detailToForm 로드 시 서버→폼 정규화, 저장 시 폼→서버 직렬화로 왕복 일관성을 보장한다.
// 누락 필드는 항상 안전 기본값(빈 배열/빈 문자열)으로 채워 undefined.map() 재발을 방지한다.
// ---------------------------------------------------------------------------

type UnknownRecord = Record<string, unknown>;

function asRecord(value: unknown): UnknownRecord {
  return value && typeof value === "object" && !Array.isArray(value) ? (value as UnknownRecord) : {};
}

function asString(value: unknown): string {
  return typeof value === "string" ? value : value == null ? "" : String(value);
}

function asNumberOrNull(value: unknown): number | null {
  if (value == null || value === "") return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

// ---------------------------------------------------------------------------
// 매핑 source ↔ 서버 값 문자열 왕복 인코딩 (major1)
//
// 서버 body/pathParams/inputMapping 은 {필드:값문자열} 평면 객체 맵 계약을 유지한다.
// source 정보를 값 문자열에 무손실 인코딩하여, 로드→저장→재로드 시 source 가 보존되게 한다.
//   - ai_generate : "{{ai:필드명}}"       (값 불필요, 필드명은 참고용)
//   - user_input  : "{{userInput.<값>}}"  (값이 userInput. 접두 없으면 부여)
//   - prev_step   : "{{<값>}}"            (이미 {{ }} 로 감싸졌으면 그대로)
//   - literal     : 값 그대로. 단 값이 "{{" 로 시작하면 "{{=<값>}}" 로 이스케이프하여
//                   재로드 시 다른 source 로 오인되지 않게 한다.
// decodeMappingValue 는 위 인코딩을 역변환하여 {source, value} 를 복원한다(대칭).
// ---------------------------------------------------------------------------

/** 서버 값 문자열 → {source, value} 복원 (encodeMappingValue 의 역변환) */
function decodeMappingValue(rawValue: unknown): { source: MappingSourceType; value: string } {
  const raw = asString(rawValue);
  const inner = raw.trim().match(/^\{\{\s*([\s\S]*?)\s*\}\}$/);
  if (!inner) {
    // 감싸지 않은 순수 문자열 → literal
    return { source: "literal", value: raw };
  }
  const body = inner[1];
  // literal 이스케이프: {{=...}}
  if (body.startsWith("=")) {
    return { source: "literal", value: body.slice(1) };
  }
  // ai_generate: {{ai:필드}}
  if (body.startsWith("ai:")) {
    return { source: "ai_generate", value: "" };
  }
  // user_input: {{userInput.x}}
  if (/^userInput\./.test(body)) {
    return { source: "user_input", value: body };
  }
  // 그 외 {{...}} → prev_step (참조 표현)
  return { source: "prev_step", value: body };
}

/** {source, value} → 서버 값 문자열 인코딩 (decodeMappingValue 의 역변환) */
function encodeMappingValue(m: FieldMapping): string {
  const value = (m.value ?? "").trim();
  switch (m.source) {
    case "ai_generate":
      return `{{ai:${m.field}}}`;
    case "user_input": {
      if (value === "") return "";
      const ref = /^userInput\./.test(value) ? value : `userInput.${value}`;
      return `{{${ref}}}`;
    }
    case "prev_step": {
      if (value === "") return "";
      // 이미 {{ }} 로 감싸졌으면 그대로, 아니면 감싼다
      return /^\{\{[\s\S]*\}\}$/.test(value) ? value : `{{${value}}}`;
    }
    case "literal":
    default: {
      // 리터럴이 {{ 로 시작하면 오인 방지 위해 이스케이프
      return value.startsWith("{{") ? `{{=${value}}}` : value;
    }
  }
}

/** 배열 형태 매핑 항목 정규화: source 필드가 있으면 그대로, 없으면 값에서 복원 */
function normalizeArrayMapping(rec: UnknownRecord): FieldMapping {
  const field = asString(rec.field);
  const hasSource = typeof rec.source === "string" && rec.source !== "";
  if (hasSource) {
    return {
      _uid: newUid(),
      field,
      source: rec.source as MappingSourceType,
      value: asString(rec.value),
      ...(rec.required === true ? { required: true } : {}),
    };
  }
  const { source, value } = decodeMappingValue(rec.value);
  return {
    _uid: newUid(),
    field,
    source,
    value,
    ...(rec.required === true ? { required: true } : {}),
  };
}

/** 서버 객체 맵({필드:값}) → FE FieldMapping[] (source 복원) */
function objectMapToMappings(map: UnknownRecord): FieldMapping[] {
  return Object.entries(map).map(([field, rawValue]) => {
    const { source, value } = decodeMappingValue(rawValue);
    return { _uid: newUid(), field, source, value };
  });
}

/** FE FieldMapping[] → 서버 객체 맵({필드:값}). source 를 값 문자열에 인코딩 */
function mappingsToObjectMap(mappings: FieldMapping[]): UnknownRecord {
  const out: UnknownRecord = {};
  for (const m of mappings) {
    if (!m.field) continue;
    out[m.field] = encodeMappingValue(m);
  }
  return out;
}

/** 서버 extract 맵({변수명:JSONPath}) → FE ExtractDef[] */
function extractMapToExtracts(map: UnknownRecord): ExtractDef[] {
  return Object.entries(map).map(([variable, path]) => ({
    _uid: newUid(),
    variable,
    method: "jsonpath" as ExtractMethod,
    value: asString(path),
  }));
}

/** FE ExtractDef[] → 서버 extract 맵({변수명:JSONPath}). jsonpath/header 만 값 보존 */
function extractsToMap(extracts: ExtractDef[]): UnknownRecord {
  const out: UnknownRecord = {};
  for (const ex of extracts) {
    if (!ex.variable) continue;
    out[ex.variable] = ex.value ?? "";
  }
  return out;
}

/** 서버 스텝 JSON → FE 폼 스텝 (정규화). 알 수 없는 필드는 안전 기본값 */
export function serverStepToForm(raw: unknown, recipeApiSpecId: number | null): RecipeStep {
  const step = asRecord(raw);
  const type = asString(step.type);
  const name = asString(step.name);
  const condition = step.condition == null ? "" : asString(step.condition);

  if (type === "script") {
    const inputsRaw = step.inputs ?? step.inputVariables;
    const inputVariables = Array.isArray(inputsRaw) ? inputsRaw.map(asString).filter(Boolean) : [];
    const outputsRaw = Array.isArray(step.outputs) ? step.outputs : [];
    const outputs: ScriptOutputDef[] = outputsRaw.map((o) => {
      if (typeof o === "string") return { _uid: newUid(), variable: o, description: "" };
      const rec = asRecord(o);
      return {
        _uid: newUid(),
        variable: asString(rec.variable ?? rec.key),
        description: asString(rec.description),
      };
    });
    const result: ScriptRecipeStep = {
      _uid: newUid(),
      type: "script",
      name,
      inputVariables,
      code: asString(step.code) || "function execute(context) {\n  return {};\n}",
      outputs,
      condition,
    };
    return result;
  }

  if (type === "recipe") {
    const inputMappingRaw = step.inputMapping ?? step.inputMappings;
    const inputMappings = Array.isArray(inputMappingRaw)
      ? // 이미 배열이면 FieldMapping 형태로 정규화 (source 없으면 값에서 복원)
        inputMappingRaw.map((m) => normalizeArrayMapping(asRecord(m)))
      : objectMapToMappings(asRecord(inputMappingRaw));
    const result: SubRecipeStep = {
      _uid: newUid(),
      type: "recipe",
      name,
      recipeId: asNumberOrNull(step.recipeId),
      inputMappings,
      condition,
    };
    return result;
  }

  // 기본: API 스텝 (type 이 "api" 이거나 알 수 없는 경우)
  const pathParamsRaw = step.pathParams ?? step.pathParamMappings;
  const pathParamMappings = Array.isArray(pathParamsRaw)
    ? pathParamsRaw.map((m) => normalizeArrayMapping(asRecord(m)))
    : objectMapToMappings(asRecord(pathParamsRaw));

  const bodyRaw = step.body ?? step.requestMappings;
  const requestMappings = Array.isArray(bodyRaw)
    ? bodyRaw.map((m) => normalizeArrayMapping(asRecord(m)))
    : objectMapToMappings(asRecord(bodyRaw));

  const extractRaw = step.extract ?? step.extracts;
  const extracts = Array.isArray(extractRaw)
    ? extractRaw.map((e) => {
        const rec = asRecord(e);
        return {
          _uid: newUid(),
          variable: asString(rec.variable),
          method: (asString(rec.method) as ExtractMethod) || "jsonpath",
          value: asString(rec.value),
        } satisfies ExtractDef;
      })
    : extractMapToExtracts(asRecord(extractRaw));

  const result: ApiRecipeStep = {
    _uid: newUid(),
    type: "api",
    name,
    label: step.label == null ? "" : asString(step.label),
    apiSpecId: asNumberOrNull(step.apiSpecId) ?? recipeApiSpecId,
    endpointId: asNumberOrNull(step.endpointId),
    pathParamMappings,
    requestMappings,
    extracts,
    condition,
  };
  return result;
}

/** FE 폼 스텝 → 서버 스텝 JSON (직렬화). 왕복 일관성 유지 */
export function formStepToServer(step: RecipeStep): UnknownRecord {
  const condition = step.condition && step.condition.trim() ? step.condition : undefined;

  if (step.type === "script") {
    return {
      type: "script",
      name: step.name,
      code: step.code,
      inputs: step.inputVariables ?? [],
      outputs: (step.outputs ?? []).filter((o) => o.variable),
      ...(condition ? { condition } : {}),
    };
  }

  if (step.type === "recipe") {
    return {
      type: "recipe",
      name: step.name,
      recipeId: step.recipeId ?? null,
      inputMapping: mappingsToObjectMap(step.inputMappings ?? []),
      ...(condition ? { condition } : {}),
    };
  }

  // API
  const pathParams = mappingsToObjectMap(step.pathParamMappings ?? []);
  return {
    type: "api",
    name: step.name,
    ...(step.label ? { label: step.label } : {}),
    endpointId: step.endpointId ?? null,
    ...(Object.keys(pathParams).length > 0 ? { pathParams } : {}),
    body: mappingsToObjectMap(step.requestMappings ?? []),
    extract: extractsToMap(step.extracts ?? []),
    ...(condition ? { condition } : {}),
  };
}

/** 스텝 타입 표시 라벨 */
export function stepTypeLabel(type: StepType): string {
  switch (type) {
    case "api":
      return "API";
    case "script":
      return "스크립트";
    case "recipe":
      return "서브레시피";
  }
}

/**
 * 데이터 탐색기용: 현재 스텝(index) 이전 스텝들이 노출하는 참조 변수 목록.
 * - API: extracts[].variable
 * - script: outputs[].variable
 * - recipe: (출력 스키마 미상 — 프로토타입에선 생략)
 */
export interface StepVariableGroup {
  stepIndex: number;
  stepName: string;
  variables: string[];
}

export function priorStepVariables(steps: RecipeStep[], upto: number): StepVariableGroup[] {
  const groups: StepVariableGroup[] = [];
  for (let i = 0; i < upto && i < steps.length; i++) {
    const step = steps[i];
    let variables: string[] = [];
    if (step.type === "api") variables = (step.extracts ?? []).map((e) => e.variable).filter(Boolean);
    else if (step.type === "script") variables = (step.outputs ?? []).map((o) => o.variable).filter(Boolean);
    groups.push({ stepIndex: i, stepName: step.name, variables });
  }
  return groups;
}
