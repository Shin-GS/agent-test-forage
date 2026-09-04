// 백엔드 DTO에 대응하는 프론트엔드 타입 정의.
// any 는 백엔드가 Object(JSON) 로 내려주는 metadata/response/context 등에만 허용한다.

/* eslint-disable @typescript-eslint/no-explicit-any */

/** 백엔드 EnumColumn 직렬화 형태 (code + description) */
export interface StatusView {
  code: string;
  description: string;
}

/** 커서 기반 페이지네이션 응답 */
export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
  hasNext: boolean;
}

/** 표준 에러 응답 바디 */
export interface ApiErrorBody {
  error: {
    code: string;
    message: string;
    traceId?: string;
  };
}

// ---------------------------------------------------------------------------
// Conversation / Message
// ---------------------------------------------------------------------------

export interface MessageResponse {
  id: number;
  conversationId: number;
  seq: number;
  role: StatusView;
  type: StatusView;
  status: StatusView;
  content: string | null;
  /** 카드 등 구조화 데이터. CardMeta 로 좁혀 사용 */
  metadata: any | null;
  referenceId: string | null;
  createdAt: string;
}

export interface ConversationDetail {
  id: number;
  userId: number;
  title: string | null;
  apiSpecId: number | null;
  status: StatusView;
  lastMessageAt: string | null;
  lastReadAt: string | null;
  unread: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationSummary {
  id: number;
  userId: number;
  title: string | null;
  apiSpecId: number | null;
  status: StatusView;
  lastMessageAt: string | null;
  lastReadAt: string | null;
  unread: boolean;
  createdAt: string;
  updatedAt: string;
}

/** POST /conversations/messages 응답 (첫 메시지로 대화방 생성) */
export interface ConversationStartResponse {
  accepted: boolean;
  conversation: ConversationDetail;
  message: MessageResponse;
}

/** POST /conversations/{id}/messages 응답 */
export interface MessageSendResponse {
  accepted: boolean;
  message: MessageResponse;
}

// ---------------------------------------------------------------------------
// Spec (서비스 스펙 상세 — 레시피 실행 시 endpointId → method/path 해석에 사용)
// ---------------------------------------------------------------------------

export interface SpecEndpointItem {
  id: number;
  /** HTTP 메서드. BE JSON 필드명은 method (httpMethod 아님) */
  method: string;
  path: string;
  summary: string | null;
  status: StatusView;
  excluded: boolean;
  confirmRequired: boolean;
}

export interface SpecAuthProfileItem {
  name: string;
  loginPageUrl: string | null;
}

export interface SpecDetail {
  id: number;
  name: string;
  baseUrl: string;
  status: StatusView;
  serviceInfo: unknown;
  endpoints: SpecEndpointItem[];
  authProfiles: SpecAuthProfileItem[];
  diagnostics: unknown;
}

/**
 * 스펙(서비스) 목록 항목 (BE GET /specs 의 SpecResponse). 사이드 패널 서비스 드롭다운 소비용.
 * status 는 BE 가 enum 문자열 또는 StatusView 로 내려줄 수 있어 느슨하게 둔다(드롭다운은 id/name 만 사용).
 */
export interface SpecListItem {
  id: number;
  name: string;
  environment?: string;
  baseUrl?: string;
  status?: StatusView | string;
  description?: string | null;
  /** 서비스 설명 (사람이 읽는 표시용, BE SpecSummaryResponse.serviceDescription) */
  serviceDescription?: string | null;
  /** 서비스 도메인 영역 (BE SpecSummaryResponse.serviceDomain) */
  serviceDomain?: string | null;
}

// ---------------------------------------------------------------------------
// Execution
// ---------------------------------------------------------------------------

export interface ExecutionStepView {
  id: number;
  stepIndex: number;
  stepName: string;
  stepType: StatusView;
  status: StatusView;
  summary: string | null;
  userInput: any;
  response: any;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface ExecutionRecipeView {
  id: number;
  recipeId: number;
  recipeName: string;
  recipeVersionNo: number;
  sequence: number;
  status: StatusView;
  recipeSnapshot: any;
  resultValues: any;
  /** 결과키 표시명 맵 (key→표시명). label 등록된 key만 포함, 없으면 원본 key 폴백 */
  resultLabels?: Record<string, string> | null;
  steps: ExecutionStepView[];
  startedAt: string | null;
  finishedAt: string | null;
}

/**
 * 액션 피커 변수 스키마 (레시피 variables 항목 그대로). action-picker.md 변수 정의 스키마.
 * key/label/type 필수, 나머지는 타입별 선택.
 */
export interface ActionPickerVariable {
  key: string;
  label: string;
  type: string; // text | number | textarea | select | multi-select | radio | checkbox | date | search-select | json
  required?: boolean;
  default?: any;
  placeholder?: string;
  options?: { label: string; value: string }[];
  min?: number;
  max?: number;
  [key: string]: any;
}

export interface ExecutionResponse {
  id: number;
  userId: number;
  conversationId: number;
  apiSpecId: number;
  type: StatusView;
  title: string;
  mode: StatusView;
  status: StatusView;
  context: any;
  resultSummary: string | null;
  recipes: ExecutionRecipeView[];
  /** 액션 피커로 수집할 변수 스키마(미충족/노출 대상). 없으면 빈 배열 */
  pendingInputs: ActionPickerVariable[];
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
}

// ---------------------------------------------------------------------------
// Card metadata (메시지 metadata 의 구조화 형태)
// ---------------------------------------------------------------------------

/** execution_mode 카드의 필요 입력값 항목 (messaging.md) */
export interface ExecutionModeInputVariable {
  key: string;
  label: string;
  /** 현재 채워진 값. 미충족이면 null */
  value: unknown | null;
  /** 값 출처: utterance(🗣️발화) / default(📌기본값) / none(✏️미입력) */
  source: "utterance" | "default" | "none" | string;
  required: boolean;
}

export interface ExecutionModeCard {
  cardType: "execution_mode";
  recipeId: number;
  /** 실행할 레시피명 (카드 제목) */
  recipeName?: string;
  /** 레시피 한줄 설명 */
  description?: string;
  /** 실행에 필요한 값 목록 */
  inputVariables?: ExecutionModeInputVariable[];
  buttons: string[];
  /** AI 가 발화에서 추출한 값 (실행 시작 시 initialContext 로 전달). 없으면 생략 */
  extractedValues?: Record<string, unknown>;
}

export interface PlanCard {
  cardType: "plan";
  [key: string]: any;
}

export interface ServiceSelectCard {
  cardType: "service_select";
  [key: string]: any;
}

export interface CandidatesCard {
  cardType: "candidates";
  [key: string]: any;
}

export type CardMeta =
  | ExecutionModeCard
  | PlanCard
  | ServiceSelectCard
  | CandidatesCard;

// ---------------------------------------------------------------------------
// 메시지 payload (message.metadata 의 구조화 형태, messaging.md payloadJson 계약)
// content 는 표시용 요약(파생물), 아래 payload 가 진실. kind 로 판별.
// ---------------------------------------------------------------------------

/** 진행 스텝 한 줄 (PROGRESS payload) */
export interface ProgressStepPayload {
  index: number;
  name: string | null;
  /** pending | running | success | failed | skipped */
  status: string;
  summary: string | null;
}

/** PROGRESS 메시지 payload */
export interface ProgressPayload {
  kind: "progress";
  schemaVersion: number;
  executionId: number;
  recipeName: string | null;
  /** running | success | partial | failed | stopped | cancelled */
  status: string;
  steps: ProgressStepPayload[];
}

/** RESULT 메시지 payload */
export interface ResultPayload {
  kind: "result";
  schemaVersion: number;
  executionId: number;
  recipeName: string | null;
  resultValues: Record<string, unknown>;
  /**
   * 결과키 → 표시명(사람말) 맵. 결과 정의(④)에 label이 등록된 key만 포함(선택, messaging.md RESULT.resultLabels).
   * 표시 폴백: resultLabels[key]가 있으면 표시명, 없으면 원본 key 그대로. 하위호환(없으면 무시).
   */
  resultLabels?: Record<string, string>;
  template?: string;
}

/** FE 가 아는 최신 payload schemaVersion. 이보다 큰 버전이면 content 폴백 */
export const SUPPORTED_PAYLOAD_SCHEMA_VERSION = 1;

// ---------------------------------------------------------------------------
// SSE
// ---------------------------------------------------------------------------

export type SseNature = "SIGNAL" | "DATA";

export interface SseEnvelope {
  eventId: number;
  category: string;
  /** 소문자 snake_case 이벤트 타입 */
  type: string;
  nature: SseNature;
  sessionId: number | null;
  data: any;
}

// ---------------------------------------------------------------------------
// 사이드 패널 (레시피 목록 + 실행 히스토리)
// ---------------------------------------------------------------------------

/** 레시피 목록 항목 (BE RecipeSummaryResponse). 스텝 상세 제외 */
export interface RecipeSummary {
  id: number;
  name: string;
  description: string | null;
  apiSpecId: number | null;
  visibility: StatusView;
  tags: string[];
  validationStatus: StatusView;
  currentVersion: number;
  usageCount: number;
  lastUsedAt: string | null;
}

/** 실행 히스토리 항목 (BE ExecutionSummaryView). 스텝 상세 제외 */
export interface ExecutionSummaryView {
  id: number;
  conversationId: number | null;
  apiSpecId: number | null;
  /** 사람이 읽는 서비스 표시명. apiSpecId가 null(플랜 등)이면 null */
  serviceName?: string | null;
  type: StatusView;
  title: string | null;
  status: StatusView;
  resultSummary: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
}

// ---------------------------------------------------------------------------
// Settings (설정 페이지 — 읽기 전용)
// ---------------------------------------------------------------------------

/**
 * GET /api/v1/settings 응답. 현재 서버에 적용된 AI/레시피 실행 설정(읽기 전용).
 * API 키 등 시크릿은 포함하지 않는다. editable=false 이면 화면에서 편집 불가(파일로만 변경).
 */
export interface SettingsResponse {
  /** AI Provider (OpenRouter 고정) */
  provider: string;
  /** 의도 분석/플랜/조회 판단용 모델 (예: openai/gpt-4o) */
  reasoningModel: string;
  /** 필드 생성/요약용 모델 (예: openai/gpt-4o-mini) */
  fastModel: string;
  /** AI 에게 전달하는 최근 대화 건수 */
  historyLimit: number;
  /** AI 호출 타임아웃 (초) */
  aiTimeoutSeconds: number;
  /** 개별 스텝 타임아웃 (초) */
  stepTimeoutSeconds: number;
  /** 레시피 전체 타임아웃 (초) */
  recipeTimeoutSeconds: number;
  /** 화면 편집 가능 여부 (현재 정책상 false) */
  editable: boolean;
}

// ---------------------------------------------------------------------------
// Recipe editing (레시피 편집 페이지 — 목록/생성/수정/삭제)
// steps/variables/resultDefinition 는 BE 에서 Object(자유 JSON)로 주고받는다.
// authoring.md / structure.md 의 폼 스키마대로 FE 타입을 정의한다.
// ---------------------------------------------------------------------------

/** 값 소스 4종 (요청 필드 매핑 / 서브레시피 입력 매핑) — authoring.md ③ */
export type MappingSourceType = "prev_step" | "user_input" | "literal" | "ai_generate";

/** 요청 필드/입력 매핑 한 줄 */
export interface FieldMapping {
  /** FE 전용 안정 로컬 id (React key용, 서버 직렬화 제외) */
  _uid?: string;
  /** 매핑 대상 필드명 (요청 필드명 또는 서브레시피 변수명) */
  field: string;
  /** 필수 필드 여부 (스키마 유래, 검증용) */
  required?: boolean;
  /** 값 소스 */
  source: MappingSourceType;
  /**
   * 매핑 값.
   * - prev_step: "스텝참조.변수명" (예: "step1.email") 또는 extract 변수명
   * - user_input: "userInput.xxx"
   * - literal: 직접 입력 문자열
   * - ai_generate: 사용 안 함(자동)
   */
  value?: string;
}

/** Extract 추출 방식 (API 스텝 응답값 추출) — authoring.md ③ */
export type ExtractMethod = "jsonpath" | "full_response" | "status_code" | "header";

/** Extract 한 줄 */
export interface ExtractDef {
  /** FE 전용 안정 로컬 id (React key용, 서버 직렬화 제외) */
  _uid?: string;
  /** 추출한 값을 담을 변수명 */
  variable: string;
  method: ExtractMethod;
  /** jsonpath 경로 또는 header 이름. full_response/status_code 는 미사용 */
  value?: string;
}

/** 스크립트 스텝 출력 변수 정의 */
export interface ScriptOutputDef {
  /** FE 전용 안정 로컬 id (React key용, 서버 직렬화 제외) */
  _uid?: string;
  variable: string;
  description?: string;
}

/** 스텝 공통 필드 */
interface RecipeStepBase {
  /** FE 전용 안정 로컬 id (React key/expanded용, 서버 직렬화 제외) */
  _uid?: string;
  /** 스텝명 (내부 식별) */
  name: string;
  /** 실행 조건 (선택). 비우면 항상 실행 */
  condition?: string | null;
}

/** API 스텝 — 외부 API 호출 */
export interface ApiRecipeStep extends RecipeStepBase {
  type: "api";
  /** 스텝 표시명 (선택). 비우면 summary → method+path 폴백 */
  label?: string | null;
  /** 대상 스펙(서브도메인) ID */
  apiSpecId?: number | null;
  /** 선택한 엔드포인트 ID (SpecEndpointItem.id) */
  endpointId?: number | null;
  /** 경로 파라미터 매핑 목록 (path의 {id} 등). 서버 pathParams 객체 맵과 왕복 */
  pathParamMappings: FieldMapping[];
  /** 요청 필드 매핑 목록 */
  requestMappings: FieldMapping[];
  /** 응답 추출 목록 */
  extracts: ExtractDef[];
}

/** 스크립트 스텝 — JavaScript 실행 */
export interface ScriptRecipeStep extends RecipeStepBase {
  type: "script";
  /** context 에서 사용할 변수 키 목록 (체크박스 선택) */
  inputVariables: string[];
  /** 실행 코드 */
  code: string;
  /** 출력 변수 정의 */
  outputs: ScriptOutputDef[];
}

/** 서브레시피 스텝 — 다른 레시피 호출 */
export interface SubRecipeStep extends RecipeStepBase {
  type: "recipe";
  /** 호출할 레시피 ID */
  recipeId?: number | null;
  /** 서브레시피 사용자 입력 변수에 대한 입력 매핑 */
  inputMappings: FieldMapping[];
}

export type RecipeStep = ApiRecipeStep | ScriptRecipeStep | SubRecipeStep;

/** 사용자 입력 변수 타입 (authoring.md ②) */
export type RecipeVariableType =
  | "text"
  | "number"
  | "textarea"
  | "select"
  | "radio"
  | "checkbox"
  | "date";

/** 사용자 입력 변수 정의 (레시피 메타 ②) */
export interface RecipeVariable {
  /** FE 전용 안정 로컬 id (React key용, 서버 직렬화 제외) */
  _uid?: string;
  key: string;
  label: string;
  type: RecipeVariableType;
  required?: boolean;
  default?: string | null;
}

/** 결과 정의 한 줄 (authoring.md ④) */
export interface ResultDefinitionItem {
  /** FE 전용 안정 로컬 id (React key용, 서버 직렬화 제외) */
  _uid?: string;
  /** 결과키 (변수명) */
  key: string;
  /** 표시명(선택). 비우면 key 로 폴백 */
  label?: string | null;
  /** 소스: "스텝참조.변수명" (예: "step2.orderId") */
  source: string;
}

/** 레시피 상세 (BE RecipeDetailResponse). steps/variables/resultDefinition 는 자유 JSON */
export interface RecipeDetail {
  id: number;
  ownerUserId: number | null;
  apiSpecId: number | null;
  name: string;
  description: string;
  visibility: StatusView;
  tags: string[];
  variables: RecipeVariable[] | null;
  steps: RecipeStep[] | null;
  resultDefinition: ResultDefinitionItem[] | null;
  resultTemplate: string | null;
  currentVersion: number;
  validationStatus: StatusView;
  validationMessage: string | null;
  usageCount: number;
  lastUsedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 레시피 생성 요청 (BE RecipeCreateRequest) */
export interface RecipeCreateRequest {
  ownerUserId: number;
  apiSpecId: number | null;
  name: string;
  description: string;
  /** COMMON / PRIVATE */
  visibility: string;
  tags: string[];
  variables: RecipeVariable[];
  /** 서버 저장용 스텝 JSON(자유 스키마). 폼 스텝 → formStepToServer 로 직렬화한 형태 */
  steps: any[];
  resultDefinition: ResultDefinitionItem[];
  resultTemplate: string | null;
}

/** 레시피 수정 요청 (BE RecipeUpdateRequest — apiSpecId/ownerUserId 없음) */
export interface RecipeUpdateRequest {
  name: string;
  description: string;
  visibility: string;
  tags: string[];
  variables: RecipeVariable[];
  /** 서버 저장용 스텝 JSON(자유 스키마). 폼 스텝 → formStepToServer 로 직렬화한 형태 */
  steps: any[];
  resultDefinition: ResultDefinitionItem[];
  resultTemplate: string | null;
}
