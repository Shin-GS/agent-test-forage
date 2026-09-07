// 실행(Execution) 관련 에이전트 API.

import { request } from "./client";
import type { ExecutionResponse } from "./types";

export interface StartExecutionPayload {
  recipeId: number;
  /** 실행 모드 코드 (예: AUTO / MANUAL) */
  mode: string;
  /** 실행 시작 시 시드할 초기값 (AI 추출값 등). BE 가 recipe 변수 기본값과 병합해 context.userInput 에 넣는다 */
  initialContext?: Record<string, unknown>;
}

export interface StartPlanPayload {
  /** 실행할 레시피 ID 순서 (= 실행 순서). recipeIds 1개면 BE 가 단일(SINGLE)로 수렴한다 */
  recipeIds: number[];
  /**
   * 레시피별 값 사전 편집 맵 (플랜 카드에서 편집한 값). recipeIds 와 인덱스 1:1.
   * recipeInputs[i] 는 recipeIds[i] 의 편집값 맵이며, 미편집이면 {}. 배열 길이는 recipeIds 와 일치.
   */
  recipeInputs?: Array<Record<string, unknown>>;
  /** 실행 모드 코드 (플랜은 항상 AUTO). 미지정 시 BE 기본값 */
  mode?: string;
  /** 첫 레시피에 시드할 초기값 (AI 추출값 등). 없으면 생략 */
  initialContext?: Record<string, unknown>;
}

/* eslint-disable @typescript-eslint/no-explicit-any */
export interface ReportStepPayload {
  /** 스텝 결과 상태 코드 */
  status: string;
  summary?: string;
  userInput?: any;
  response?: any;
  errorMessage?: string;
  /** 이 스텝에서 추출한 변수 (서버 전역 context에 누적, 다음 스텝이 참조) */
  extractedValues?: Record<string, any>;
}

/** 대화방에서 실행 시작 (단일 레시피) */
export function startExecution(
  conversationId: number,
  payload: StartExecutionPayload
): Promise<ExecutionResponse> {
  return request<ExecutionResponse>(`/conversations/${conversationId}/executions`, {
    method: "POST",
    body: payload,
  });
}

/**
 * 플랜 실행 시작 (레시피 여러 개 순차 실행). plan 카드의 [자동 실행]이 트리거한다.
 * recipeIds 순서가 실행 순서이며, BE 가 첫 레시피만 RUNNING 으로 시작하고 각 레시피 완료 시
 * 자동으로 다음 레시피로 전이한다(FE 는 reportStep 만 보고, complete 호출 없음).
 * recipeIds 가 1개면 단일 실행과 동일하게 수렴한다(TYPE 표시만 SINGLE).
 */
export function startPlan(
  conversationId: number,
  payload: StartPlanPayload
): Promise<ExecutionResponse> {
  return request<ExecutionResponse>(`/conversations/${conversationId}/plan-executions`, {
    method: "POST",
    body: payload,
  });
}

/** 스텝 실행 결과 보고 */
export function reportStep(
  executionId: number,
  stepId: number,
  payload: ReportStepPayload
): Promise<ExecutionResponse> {
  return request<ExecutionResponse>(`/executions/${executionId}/steps/${stepId}`, {
    method: "POST",
    body: payload,
  });
}

// 실행 완료(complete)용 외부 엔드포인트는 제거됨(BE 계약 변경).
// 마지막 레시피의 마지막 스텝을 reportStep(SUCCESS/SKIPPED)으로 보고하면 BE 가 자동 완료한다.
// (FE 러너는 reportStep 만 호출하고 complete 를 부르지 않는다 — 부르면 404)

/** 실행 단건 조회 */
export function getExecution(executionId: number): Promise<ExecutionResponse> {
  return request<ExecutionResponse>(`/executions/${executionId}`, {
    method: "GET",
  });
}

/* eslint-disable @typescript-eslint/no-explicit-any */
export interface ActionPickerRespondPayload {
  executionId: number;
  /** pre-run 일괄 수집이면 -1 (execution.md 규약) */
  stepIndex: number;
  values: Record<string, any>;
}

/**
 * 액션 피커 입력 제출. 서버가 values 를 context.userInput 에 병합하고
 * input_waiting → executing 전이 후 실행을 재개(응답의 executing execution 으로 러너 구동).
 */
export function respondActionPicker(payload: ActionPickerRespondPayload): Promise<ExecutionResponse> {
  return request<ExecutionResponse>("/action-picker/respond", {
    method: "POST",
    body: payload,
  });
}

/**
 * 현재 세션 사용자 실행 히스토리 (커서 페이지). 최근순. userId 는 세션에서 도출.
 * 다중 필터(apiSpecId/status)는 배열로 넘기면 client 가 반복 쿼리 파라미터로 직렬화한다
 * (예: apiSpecId=1&apiSpecId=2). 빈 배열/undefined 는 생략(=전체).
 * from/to 는 YYYY-MM-DD (서버가 당일 포함 경계 처리).
 */
export function history(params?: {
  /** 레시피명 검색어 */
  keyword?: string;
  /** 서비스(apiSpecId) 다중 필터 */
  apiSpecId?: number[];
  /** 상태(ExecutionStatus) 다중 필터: SUCCESS/FAILED/STOPPED/CANCELLED */
  status?: string[];
  /** 시작일 (YYYY-MM-DD) */
  from?: string;
  /** 종료일 (YYYY-MM-DD) */
  to?: string;
  cursor?: string;
  size?: number;
}): Promise<import("./types").CursorPage<import("./types").ExecutionSummaryView>> {
  return request(`/executions`, {
    method: "GET",
    query: {
      keyword: params?.keyword,
      apiSpecId: params?.apiSpecId,
      status: params?.status,
      from: params?.from,
      to: params?.to,
      cursor: params?.cursor,
      size: params?.size,
    },
  });
}

/** 특정 대화방의 실행 히스토리 (커서 페이지). 최근순 */
export function historyByConversation(
  conversationId: number,
  params?: { cursor?: string; size?: number }
): Promise<import("./types").CursorPage<import("./types").ExecutionSummaryView>> {
  return request(`/conversations/${conversationId}/executions`, {
    method: "GET",
    query: { cursor: params?.cursor, size: params?.size },
  });
}
