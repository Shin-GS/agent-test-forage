// 실행(Execution) 관련 에이전트 API.

import { request } from "./client";
import type { ExecutionResponse } from "./types";

export interface StartExecutionPayload {
  userId: number;
  recipeId: number;
  /** 실행 모드 코드 (예: AUTO / MANUAL) */
  mode: string;
  /** 실행 시작 시 시드할 초기값 (AI 추출값 등). BE 가 recipe 변수 기본값과 병합해 context.userInput 에 넣는다 */
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

export interface CompleteExecutionPayload {
  /** 최종 상태 코드 */
  status: string;
  resultSummary?: string;
}

/** 대화방에서 실행 시작 */
export function startExecution(
  conversationId: number,
  payload: StartExecutionPayload
): Promise<ExecutionResponse> {
  return request<ExecutionResponse>(`/conversations/${conversationId}/executions`, {
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

/** 실행 완료 처리 */
export function completeExecution(
  executionId: number,
  payload: CompleteExecutionPayload
): Promise<ExecutionResponse> {
  return request<ExecutionResponse>(`/executions/${executionId}/complete`, {
    method: "POST",
    body: payload,
  });
}

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

/** 사용자 실행 히스토리 (커서 페이지). 최근순. 사이드 패널/전체 히스토리 소비용 */
export function history(
  userId: number,
  params?: { status?: string; keyword?: string; cursor?: string; size?: number }
): Promise<import("./types").CursorPage<import("./types").ExecutionSummaryView>> {
  return request(`/executions`, {
    method: "GET",
    query: {
      userId,
      status: params?.status,
      keyword: params?.keyword,
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
