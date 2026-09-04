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
  type: StatusView;
  title: string | null;
  status: StatusView;
  resultSummary: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
}
