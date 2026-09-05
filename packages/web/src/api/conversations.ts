// 대화방 / 메시지 관련 에이전트 API.

import { request } from "./client";
import type {
  ConversationDetail,
  ConversationStartResponse,
  ConversationSummary,
  CursorPage,
  MessageResponse,
  MessageSendResponse,
} from "./types";

export interface StartMessagePayload {
  content: string;
  /** 대상 서비스. 미지정(미전달/null)이면 서버가 서비스 없이 대화방을 생성한다 */
  apiSpecId?: number | null;
  /** 참조 태그 (사이드 패널 레시피 실행 시 recipeId 를 문자열로 전달) */
  referenceId?: string;
}

export interface SendMessagePayload {
  content: string;
  /** 참조 태그 (사이드 패널 레시피 실행 시 recipeId 를 문자열로 전달) */
  referenceId?: string;
}

/** 새 대화방 시작 + 첫 메시지 전송. 응답: { accepted, conversation, message } */
export function startMessage(payload: StartMessagePayload): Promise<ConversationStartResponse> {
  return request<ConversationStartResponse>("/conversations/messages", {
    method: "POST",
    body: payload,
  });
}

/** 기존 대화방에 메시지 전송. 응답: { accepted, message } */
export function sendMessage(
  conversationId: number,
  payload: SendMessagePayload
): Promise<MessageSendResponse> {
  return request<MessageSendResponse>(`/conversations/${conversationId}/messages`, {
    method: "POST",
    body: payload,
  });
}

/** 현재 세션 사용자의 대화방 목록 조회 (userId 는 세션에서 도출) */
export function listConversations(): Promise<ConversationSummary[]> {
  return request<ConversationSummary[]>("/conversations", {
    method: "GET",
  });
}

/** 대화방 단건 조회 */
export function getConversation(conversationId: number): Promise<ConversationDetail> {
  return request<ConversationDetail>(`/conversations/${conversationId}`, {
    method: "GET",
  });
}

/** 대화방 메시지 목록 (커서 페이지네이션) */
export function listMessages(
  conversationId: number,
  cursor?: string,
  size?: number
): Promise<CursorPage<MessageResponse>> {
  return request<CursorPage<MessageResponse>>(`/conversations/${conversationId}/messages`, {
    method: "GET",
    query: { cursor, size },
  });
}

/** 대화방 읽음 처리 (BE: PATCH /conversations/{id}/read) */
export function markRead(conversationId: number): Promise<void> {
  return request<void>(`/conversations/${conversationId}/read`, {
    method: "PATCH",
  });
}

/** 진행 중인 AI 응답 취소 */
export function cancel(conversationId: number): Promise<void> {
  return request<void>(`/conversations/${conversationId}/cancel`, {
    method: "POST",
  });
}

/** 실행 중지 */
export function stop(conversationId: number): Promise<void> {
  return request<void>(`/conversations/${conversationId}/stop`, {
    method: "POST",
  });
}

/** 대화방 삭제 */
export function deleteConversation(conversationId: number): Promise<void> {
  return request<void>(`/conversations/${conversationId}`, {
    method: "DELETE",
  });
}

/** 대화방 삭제 (deleteConversation alias — 개편 API 명칭) */
export function remove(conversationId: number): Promise<void> {
  return deleteConversation(conversationId);
}

/**
 * 대화방 대상 서비스 변경.
 * BE: PATCH /conversations/{id}/service, body {apiSpecId: number|null}.
 * apiSpecId=null 은 미지정으로 변경. 응답: ConversationDetail(serviceName 포함).
 * 404(대화 없음/타인), 400(유효하지 않은 서비스).
 */
export function updateService(
  conversationId: number,
  apiSpecId: number | null
): Promise<ConversationDetail> {
  return request<ConversationDetail>(`/conversations/${conversationId}/service`, {
    method: "PATCH",
    body: { apiSpecId },
  });
}

export interface UpdateTitlePayload {
  title: string;
}

/**
 * 대화방 이름 변경 (인라인 편집 저장).
 * BE: PATCH /conversations/{id}/title. 서버도 동일 검증(트림/길이/제어문자 제거) 수행 가정.
 */
export function updateTitle(conversationId: number, title: string): Promise<ConversationDetail> {
  return request<ConversationDetail>(`/conversations/${conversationId}/title`, {
    method: "PATCH",
    body: { title },
  });
}
