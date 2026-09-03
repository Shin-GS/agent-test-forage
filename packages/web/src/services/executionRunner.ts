// 레시피 실행 엔진 (프로토타입 최소 구현).
//
// startExecution 응답(ExecutionResponse)을 받아 브라우저에서 스텝을 순차 실행한다.
// - recipeSnapshot 의 스텝 정의(steps 배열)와 서버 스텝 레코드(recipes[0].steps[]: id=stepId)를
//   순서(stepIndex / 배열 순서)로 매칭한다.
// - 각 스텝을 실행하고 executionsApi.reportStep 으로 결과를 보고한다.
// - 모든 스텝 종료 후 completeExecution 으로 마무리한다.
// - 진행/완료 화면 갱신은 SSE(execution_progress/execution_complete)로 스토어가 처리하므로,
//   러너는 실제 실행 로직에만 집중한다.
//
// ── 알려진 한계 (프로토타입) ─────────────────────────────────────────────
// - baseUrl: 실행 응답/스냅샷에 외부 서버 baseUrl 이 없을 수 있어, 못 찾으면 상수(DEFAULT_BASE_URL)
//   를 사용한다. 실제로는 apiSpec 에 baseUrl 이 실려야 한다.
// - 조건 평가: eval 미사용. "{{expr}} OP literal" 형태의 단순 이항 비교만 지원
//   (=== !== == != > >= < <=). 그 외 표현식은 "평가 불가 → 실행"으로 처리한다.
// - 변수 치환: {{path}} 를 context 경로 값으로 치환하는 단순 방식. 중첩 경로(a.b.c) 지원.
// - script: recipeSnapshot 의 code(function execute(context){...})를 new Function 으로 실행한다.
//   신뢰된 관리자 정의 레시피에 한함(사용자 임의 입력 아님)이라는 전제.
// ────────────────────────────────────────────────────────────────────────

/* eslint-disable @typescript-eslint/no-explicit-any */

import { executionsApi, specsApi } from "../api";
import type { ExecutionResponse, ExecutionRecipeView, SpecDetail } from "../api/types";

/** baseUrl 을 못 찾을 때 사용할 임시 기본값 (demo-shop) */
const DEFAULT_BASE_URL = "http://localhost:9101";

/** endpointId → { method, path } 해석 맵 (스펙 상세에서 구성) */
type EndpointMap = Map<number, { method: string; path: string }>;

/**
 * 인증 필요(401/403) 스텝에서 던지는 오류. 실행을 "실패"가 아닌 "인증 대기"로 다루기 위해
 * 일반 오류와 구분한다. 로그인 후 이 스텝부터 이어서 재개한다.
 */
export class AuthRequiredError extends Error {
  readonly httpStatus: number;
  constructor(httpStatus: number, message: string) {
    super(message);
    this.name = "AuthRequiredError";
    this.httpStatus = httpStatus;
  }
}

/** 스텝 상태 코드 (EXECUTION_STEP.STATUS) */
type StepStatus = "SUCCESS" | "FAILED" | "SKIPPED";
/** 실행 최종 상태 코드 (EXECUTION.STATUS) */
type ExecutionStatus = "SUCCESS" | "PARTIAL" | "FAILED";

/** 실행 컨텍스트: extract 변수 + StepN 원시응답 + userInput 누적 */
type RunContext = Record<string, any>;

/** recipeSnapshot 내 스텝 정의 (백엔드 확정 전이라 느슨하게 정의) */
interface SnapshotStep {
  name?: string;
  type?: string; // api | script | recipe | userInput / user_input
  method?: string;
  path?: string;
  endpointId?: number | string;
  body?: any;
  extract?: Record<string, string> | ExtractDef[];
  condition?: string;
  pathParams?: Record<string, string>;
  code?: string;
  inputs?: InputVarDef[];
  [key: string]: any;
}

interface ExtractDef {
  name: string;
  path: string;
}

interface InputVarDef {
  name: string;
  label?: string;
  default?: any;
  required?: boolean;
}

export interface RunExecutionOptions {
  /** 'AUTO' | 'MANUAL' */
  mode: string;
  /** MANUAL 모드에서 사용자 입력을 수집하는 콜백. 미지정 시 window.prompt 사용 */
  collectInput?: (vars: InputVarDef[], stepName: string) => Promise<Record<string, any>>;
  /**
   * 재개 상태. 인증(401) 대기 후 "계속 진행" 시, 중단된 스텝 인덱스와 그때까지의 context 를
   * 넘겨 이미 성공한 스텝을 재실행하지 않고 이어서 실행한다(중복 호출 방지 — UX).
   */
  resume?: { startIndex: number; context: RunContext; anySucceeded?: boolean };
}

/**
 * 실행 결과.
 * - outcome=AUTH_REQUIRED: 401/403 로 중단. auth 정보로 인증 안내 카드를 띄우고,
 *   로그인 후 resumeState 를 넘겨 runExecution 을 다시 호출하면 그 지점부터 재개한다.
 *   이 경우 completeExecution 을 호출하지 않는다(실행은 아직 끝나지 않음).
 * - 그 외: 실행이 종료되어 completeExecution 까지 마쳤다.
 */
export interface RunResult {
  outcome: ExecutionStatus | "AUTH_REQUIRED";
  auth?: {
    httpStatus: number;
    /** 인증이 필요한 스텝 인덱스 (0-based) */
    stepIndex: number;
    /** 재개용 상태 */
    resumeState: { startIndex: number; context: RunContext; anySucceeded: boolean };
  };
}

/**
 * 실행 응답을 받아 스텝을 순차 실행한다. 401/403 을 만나면 실행을 "실패"가 아닌
 * "인증 대기(AUTH_REQUIRED)"로 반환하여, 로그인 후 그 스텝부터 재개할 수 있게 한다.
 */
export async function runExecution(
  execution: ExecutionResponse,
  options: RunExecutionOptions
): Promise<RunResult> {
  const recipe = execution.recipes?.[0];
  if (!recipe) {
    await safeComplete(execution.id, "FAILED", "실행할 레시피가 없습니다");
    return { outcome: "FAILED" };
  }

  const snapshotSteps = extractSnapshotSteps(recipe);
  // 재개면 이전 context 를 이어받고, 아니면 새로 시작한다.
  const context: RunContext = options.resume?.context ?? { userInput: {} };
  const startIndex = options.resume?.startIndex ?? 0;

  // 스펙 상세를 조회해 baseUrl 과 endpointId→{method,path} 맵을 구성한다.
  // 레시피 스텝은 path/method 를 직접 담지 않고 endpointId 만 가지므로 이 해석이 필요하다.
  let spec: SpecDetail | null = null;
  try {
    spec = await specsApi.getSpec(execution.apiSpecId);
  } catch {
    // 스펙 조회 실패 시 baseUrl 상수 + endpoint 미해석으로 진행(스텝에 path 가 직접 있으면 동작)
  }
  const endpointMap = buildEndpointMap(spec);
  const baseUrl = resolveBaseUrl(execution, recipe, spec);

  let anyFailed = false;
  let anySucceeded = options.resume?.anySucceeded ?? false;

  for (let index = startIndex; index < recipe.steps.length; index += 1) {
    const stepRecord = recipe.steps[index];
    const snapshot = snapshotSteps[index] ?? {};
    const stepType = normalizeStepType(stepRecord.stepType?.code ?? snapshot.type ?? "API");

    // 조건 평가 — 불만족 시 SKIPPED
    if (snapshot.condition && !evaluateCondition(snapshot.condition, context)) {
      await safeReport(execution.id, stepRecord.id, {
        status: "SKIPPED",
        summary: "조건 불만족으로 스킵",
      });
      continue;
    }

    try {
      const result = await executeStep(stepType, snapshot, context, {
        baseUrl,
        endpointMap,
        mode: options.mode,
        collectInput: options.collectInput,
        stepName: stepRecord.stepName ?? snapshot.name ?? `Step${index}`,
      });

      // context 누적: StepN 원시응답 + extract 값 + userInput 반영
      context[`Step${index}`] = result.response;
      Object.assign(context, result.extractedValues);

      await safeReport(execution.id, stepRecord.id, {
        status: "SUCCESS",
        summary: result.summary,
        response: result.response,
        userInput: result.userInput,
        // 추출값을 서버 전역 context에도 누적(다음 스텝이 참조 — structure.md 스텝 간 데이터 전달)
        extractedValues: result.extractedValues,
      });
      anySucceeded = true;
    } catch (error) {
      // 인증 필요(401/403): 실행을 종료하지 않고 인증 대기로 반환한다.
      // 이 스텝은 아직 성공/실패로 보고하지 않는다(로그인 후 이 스텝부터 재개).
      if (error instanceof AuthRequiredError) {
        return {
          outcome: "AUTH_REQUIRED",
          auth: {
            httpStatus: error.httpStatus,
            stepIndex: index,
            resumeState: { startIndex: index, context, anySucceeded },
          },
        };
      }

      const message = error instanceof Error ? error.message : String(error);
      await safeReport(execution.id, stepRecord.id, {
        status: "FAILED",
        summary: "스텝 실패",
        errorMessage: message,
      });
      anyFailed = true;
      // 프로토타입: 실패 시 이후 스텝 중단
      break;
    }
  }

  const finalStatus: ExecutionStatus = anyFailed
    ? anySucceeded
      ? "PARTIAL"
      : "FAILED"
    : "SUCCESS";

  await safeComplete(
    execution.id,
    finalStatus,
    buildResultSummary(recipe, finalStatus)
  );

  return { outcome: finalStatus };
}

// ---------------------------------------------------------------------------
// 스텝 실행
// ---------------------------------------------------------------------------

interface StepExecContext {
  baseUrl: string;
  endpointMap: EndpointMap;
  mode: string;
  collectInput?: RunExecutionOptions["collectInput"];
  stepName: string;
}

interface StepResult {
  response: any;
  summary: string;
  extractedValues: Record<string, any>;
  userInput?: Record<string, any>;
}

async function executeStep(
  stepType: string,
  snapshot: SnapshotStep,
  context: RunContext,
  exec: StepExecContext
): Promise<StepResult> {
  switch (stepType) {
    case "API":
      return executeApiStep(snapshot, context, exec);
    case "SCRIPT":
      return executeScriptStep(snapshot, context);
    case "USER_INPUT":
      return executeUserInputStep(snapshot, context, exec);
    case "RECIPE":
      // 서브레시피 실행은 프로토타입 범위 밖 — 스킵 취급 (성공 처리하되 요약에 명시)
      return {
        response: null,
        summary: "서브레시피 실행 미지원(프로토타입)",
        extractedValues: {},
      };
    default:
      throw new Error(`알 수 없는 스텝 타입: ${stepType}`);
  }
}

async function executeApiStep(
  snapshot: SnapshotStep,
  context: RunContext,
  exec: StepExecContext
): Promise<StepResult> {
  // 스텝은 path/method 를 직접 담지 않고 endpointId 만 가질 수 있다.
  // 우선순위: 스텝의 명시적 method/path > endpointId 로 스펙에서 해석.
  const resolved = resolveEndpoint(snapshot, exec.endpointMap);
  const method = resolved.method.toUpperCase();
  // 경로 변수: 먼저 pathParams({{expr}} 치환) 를 경로 템플릿({id})에 적용, 그다음 context 치환.
  const withPathParams = applyPathParams(resolved.path, snapshot.pathParams, context);
  const path = substitute(withPathParams, context);
  const url = joinUrl(exec.baseUrl, path);

  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const hasBody = snapshot.body !== undefined && snapshot.body !== null && method !== "GET";
  const body = hasBody ? substituteDeep(snapshot.body, context) : undefined;

  const response = await fetch(url, {
    method,
    headers,
    credentials: "include",
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  const text = await response.text();
  let parsed: any = null;
  if (text.length > 0) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = text;
    }
  }

  if (response.status === 401 || response.status === 403) {
    // 인증/권한 부족 — 실패가 아닌 인증 대기로 구분(로그인 후 이 스텝부터 재개)
    throw new AuthRequiredError(response.status, `${method} ${path} → ${response.status}`);
  }
  if (!response.ok) {
    throw new Error(`${method} ${path} 실패 (HTTP ${response.status})`);
  }

  const extractedValues = applyExtract(snapshot.extract, parsed);

  return {
    response: parsed,
    summary: `${method} ${path} → ${response.status}`,
    extractedValues,
  };
}

function executeScriptStep(snapshot: SnapshotStep, context: RunContext): StepResult {
  const code = snapshot.code;
  if (!code) {
    return { response: null, summary: "스크립트 없음", extractedValues: {} };
  }

  // recipeSnapshot 의 code 는 function execute(context){...} 형태.
  // new Function 으로 감싸 실행하고 반환 객체를 context 에 병합한다.
  // (신뢰된 관리자 정의 레시피 전제)
  // eslint-disable-next-line @typescript-eslint/no-implied-eval
  const factory = new Function(
    "context",
    `${code}\nreturn typeof execute === "function" ? execute(context) : undefined;`
  );
  const returned = factory(context) as Record<string, any> | undefined;
  const merged = returned && typeof returned === "object" ? returned : {};

  return {
    response: merged,
    summary: "스크립트 실행 완료",
    extractedValues: merged,
  };
}

async function executeUserInputStep(
  snapshot: SnapshotStep,
  context: RunContext,
  exec: StepExecContext
): Promise<StepResult> {
  const vars = snapshot.inputs ?? [];
  const collected: Record<string, any> = {};

  if (exec.mode.toUpperCase() === "MANUAL" && vars.length > 0) {
    if (exec.collectInput) {
      Object.assign(collected, await exec.collectInput(vars, exec.stepName));
    } else {
      // 폴백: window.prompt 로 최소 수집
      for (const v of vars) {
        const answer = window.prompt(
          `${v.label ?? v.name}${v.required ? " *" : ""}`,
          v.default != null ? String(v.default) : ""
        );
        collected[v.name] = answer ?? v.default ?? "";
      }
    }
  } else {
    // AUTO: 기본값 사용
    for (const v of vars) {
      collected[v.name] = v.default ?? "";
    }
  }

  // userInput 컨텍스트 병합
  context.userInput = { ...(context.userInput ?? {}), ...collected };

  return {
    response: collected,
    summary: `입력 ${Object.keys(collected).length}건`,
    extractedValues: {},
    userInput: collected,
  };
}

// ---------------------------------------------------------------------------
// 변수 치환 / 조건 평가 / extract
// ---------------------------------------------------------------------------

const TEMPLATE_RE = /\{\{\s*([^}]+?)\s*\}\}/g;

/** 문자열 내 {{path}} 를 context 값으로 치환 */
function substitute(input: string, context: RunContext): string {
  return input.replace(TEMPLATE_RE, (_match, expr: string) => {
    const value = resolvePath(context, expr.trim());
    return value == null ? "" : String(value);
  });
}

/** 객체/배열/문자열을 재귀적으로 치환. 문자열 전체가 단일 {{path}} 면 원시 타입 보존 */
function substituteDeep(value: any, context: RunContext): any {
  if (typeof value === "string") {
    const whole = value.match(/^\{\{\s*([^}]+?)\s*\}\}$/);
    if (whole) {
      return resolvePath(context, whole[1].trim());
    }
    return substitute(value, context);
  }
  if (Array.isArray(value)) {
    return value.map((item) => substituteDeep(item, context));
  }
  if (value && typeof value === "object") {
    const out: Record<string, any> = {};
    for (const [key, val] of Object.entries(value)) {
      out[key] = substituteDeep(val, context);
    }
    return out;
  }
  return value;
}

/** "a.b.c" 경로로 중첩 값 조회 */
function resolvePath(source: any, path: string): any {
  const segments = path.split(".");
  let current = source;
  for (const seg of segments) {
    if (current == null) return undefined;
    current = current[seg];
  }
  return current;
}

const CONDITION_RE = /^(.*?)\s*(===|!==|==|!=|>=|<=|>|<)\s*(.*)$/;

/**
 * 단순 이항 비교만 지원. 좌/우변의 {{...}} 를 치환한 뒤 비교한다.
 * 파싱 불가하면 true(실행) 반환 — 프로토타입 한계.
 */
function evaluateCondition(condition: string, context: RunContext): boolean {
  const substituted = substitute(condition, context);
  const match = substituted.match(CONDITION_RE);
  if (!match) {
    // 비교 연산자가 없으면 truthy 판단 (빈 문자열/"false"/"0" 은 false)
    const trimmed = substituted.trim();
    if (trimmed === "" || trimmed === "false" || trimmed === "0") return false;
    return true;
  }
  const [, leftRaw, op, rightRaw] = match;
  const left = coerce(leftRaw.trim());
  const right = coerce(rightRaw.trim());

  switch (op) {
    case "===":
    case "==":
      // eslint-disable-next-line eqeqeq
      return left == right;
    case "!==":
    case "!=":
      // eslint-disable-next-line eqeqeq
      return left != right;
    case ">":
      return Number(left) > Number(right);
    case ">=":
      return Number(left) >= Number(right);
    case "<":
      return Number(left) < Number(right);
    case "<=":
      return Number(left) <= Number(right);
    default:
      return true;
  }
}

/** 문자열 리터럴을 number/boolean/string 으로 변환 */
function coerce(raw: string): string | number | boolean {
  const unquoted = raw.replace(/^["']|["']$/g, "");
  if (unquoted !== raw) return unquoted; // 따옴표가 있었으면 문자열
  if (raw === "true") return true;
  if (raw === "false") return false;
  if (raw !== "" && !Number.isNaN(Number(raw))) return Number(raw);
  return raw;
}

/** extract 정의(맵 또는 배열)를 응답에 적용해 값 추출 */
function applyExtract(
  extract: SnapshotStep["extract"],
  response: any
): Record<string, any> {
  const out: Record<string, any> = {};
  if (!extract) return out;

  const entries: ExtractDef[] = Array.isArray(extract)
    ? extract
    : Object.entries(extract).map(([name, path]) => ({ name, path }));

  for (const { name, path } of entries) {
    // JSONPath 전체 지원 대신 단순 dot 경로만 (선행 "$." 제거)
    const cleaned = path.replace(/^\$\.?/, "");
    out[name] = resolvePath(response, cleaned);
  }
  return out;
}

// ---------------------------------------------------------------------------
// 헬퍼
// ---------------------------------------------------------------------------

function extractSnapshotSteps(recipe: ExecutionRecipeView): SnapshotStep[] {
  const snapshot = recipe.recipeSnapshot as any;
  if (!snapshot) return [];
  // 스냅샷은 레시피 전체를 감싼 객체다. 스텝은 아래 중 하나에 있다:
  //  - steps (이미 배열로 풀린 경우)
  //  - stepsJson (JSON 문자열 — 서버 스냅샷 기본 형태)
  let steps: unknown = snapshot.steps ?? snapshot.recipeSteps;
  if (steps == null && typeof snapshot.stepsJson === "string") {
    try {
      steps = JSON.parse(snapshot.stepsJson);
    } catch {
      steps = [];
    }
  }
  return Array.isArray(steps) ? (steps as SnapshotStep[]) : [];
}

/** 스펙 상세의 endpoints 로 endpointId → {method, path} 맵 구성 */
function buildEndpointMap(spec: SpecDetail | null): EndpointMap {
  const map: EndpointMap = new Map();
  if (!spec?.endpoints) return map;
  for (const e of spec.endpoints) {
    map.set(e.id, { method: e.method, path: e.path });
  }
  return map;
}

/** 스텝의 method/path 를 해석한다. 명시적 값 우선, 없으면 endpointId 로 스펙 맵 조회 */
function resolveEndpoint(
  snapshot: SnapshotStep,
  endpointMap: EndpointMap
): { method: string; path: string } {
  if (snapshot.path) {
    return { method: snapshot.method ?? "GET", path: snapshot.path };
  }
  const endpointId = snapshot.endpointId != null ? Number(snapshot.endpointId) : NaN;
  if (!Number.isNaN(endpointId) && endpointMap.has(endpointId)) {
    const hit = endpointMap.get(endpointId)!;
    return { method: snapshot.method ?? hit.method, path: hit.path };
  }
  // 해석 실패 — 루트로 보내지 않도록 명확히 실패시킨다.
  throw new Error(`endpointId=${snapshot.endpointId} 에 대한 경로를 스펙에서 찾을 수 없습니다`);
}

/** 경로 템플릿의 {name} 을 pathParams(값은 {{expr}} 치환) 로 채운다. 예: /orders/{id} + {id:"{{orderId}}"} */
function applyPathParams(
  pathTemplate: string,
  pathParams: SnapshotStep["pathParams"],
  context: RunContext
): string {
  if (!pathParams) return pathTemplate;
  let out = pathTemplate;
  for (const [key, rawValue] of Object.entries(pathParams)) {
    const value = substitute(String(rawValue), context);
    out = out.replace(new RegExp(`\\{${key}\\}`, "g"), value);
  }
  return out;
}

function normalizeStepType(raw: string): string {
  const upper = raw.toUpperCase().replace(/[-\s]/g, "_");
  if (upper === "USERINPUT") return "USER_INPUT";
  return upper;
}

/** baseUrl 해석: 실행 context > 스냅샷 > 스펙 상세 baseUrl > 기본 상수 */
function resolveBaseUrl(
  execution: ExecutionResponse,
  recipe: ExecutionRecipeView,
  spec: SpecDetail | null
): string {
  const fromContext = (execution.context as any)?.baseUrl;
  const fromSnapshot = (recipe.recipeSnapshot as any)?.baseUrl;
  return fromContext ?? fromSnapshot ?? spec?.baseUrl ?? DEFAULT_BASE_URL;
}

function joinUrl(baseUrl: string, path: string): string {
  const base = baseUrl.replace(/\/+$/, "");
  const suffix = path.startsWith("/") ? path : `/${path}`;
  return `${base}${suffix}`;
}

function buildResultSummary(recipe: ExecutionRecipeView, status: ExecutionStatus): string {
  const label =
    status === "SUCCESS" ? "성공" : status === "PARTIAL" ? "부분 성공" : "실패";
  return `${recipe.recipeName} — ${label}`;
}

async function safeReport(
  executionId: number,
  stepId: number,
  payload: {
    status: StepStatus;
    summary?: string;
    response?: any;
    userInput?: any;
    errorMessage?: string;
    extractedValues?: Record<string, any>;
  }
): Promise<void> {
  try {
    await executionsApi.reportStep(executionId, stepId, payload);
  } catch {
    // 보고 실패는 실행 흐름을 막지 않는다 (SSE 로 상태가 갱신되지 않을 뿐)
  }
}

async function safeComplete(
  executionId: number,
  status: ExecutionStatus,
  resultSummary: string
): Promise<void> {
  try {
    await executionsApi.completeExecution(executionId, { status, resultSummary });
  } catch {
    // 무시
  }
}
