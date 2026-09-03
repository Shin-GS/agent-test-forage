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
  userId: number;
  content: string;
  apiSpecId?: number;
}

export interface SendMessagePayload {
  userId: number;
  content: string;
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

/** 사용자의 대화방 목록 조회 */
export function listConversations(userId: number): Promise<ConversationSummary[]> {
  return request<ConversationSummary[]>("/conversations", {
    method: "GET",
    query: { userId },
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
