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
