// 인증(세션 쿠키 기반) API.
// - login: 세션 쿠키(TESTFORGE_SESSION) 발급. 실패 시 401 ApiError.
// - logout: 세션 종료.
// - me: 현재 세션 사용자 조회. 비로그인 시 401.
// client.request 는 credentials:"include" 이므로 쿠키가 자동 전송/저장된다.

import { request } from "./client";

/** 세션 사용자 (BE 응답: {id, username, name, role}) */
export interface AuthUser {
  id: number;
  username: string;
  name: string;
  role: "USER" | "ADMIN";
}

export interface LoginPayload {
  username: string;
  password: string;
}

/** 로그인. 성공 시 사용자 정보 반환 + 세션 쿠키 발급. 실패 시 401 ApiError */
export function login(payload: LoginPayload): Promise<AuthUser> {
  return request<AuthUser>("/auth/login", {
    method: "POST",
    body: payload,
  });
}

/** 로그아웃. 세션 종료 */
export function logout(): Promise<void> {
  return request<void>("/auth/logout", {
    method: "POST",
  });
}

/** 현재 세션 사용자 조회. 비로그인 시 401 ApiError */
export function me(): Promise<AuthUser> {
  return request<AuthUser>("/auth/me", {
    method: "GET",
  });
}
