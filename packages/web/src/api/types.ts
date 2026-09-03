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
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
}

// ---------------------------------------------------------------------------
// Card metadata (메시지 metadata 의 구조화 형태)
// ---------------------------------------------------------------------------

export interface ExecutionModeCard {
  cardType: "execution_mode";
  recipeId: number;
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
