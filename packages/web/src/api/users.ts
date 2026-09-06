// 관리자 — 사용자 관리 (라우트 "/admin/users", 전부 ADMIN 전용).
// - 목록 조회(q 검색) + 계정 생성 + 역할/상태/비밀번호 변경.
// - 비-admin 은 서버가 403 으로 강제(FE 라우트 가드는 UX 게이팅일 뿐).
// - specs.ts 스타일과 일관되게 request 클라이언트를 재사용한다.

import { request } from "./client";
import type { AdminUserItem } from "./types";

/**
 * 사용자 목록 조회.
 * - q(선택): 아이디 부분 검색. 비우면 전체.
 * BE: GET /api/v1/admin/users?q= → AdminUserItem[] (비밀번호 미포함).
 */
export function list(params?: { q?: string }): Promise<AdminUserItem[]> {
  const q = params?.q?.trim();
  return request<AdminUserItem[]>(`/admin/users`, {
    method: "GET",
    query: q ? { q } : undefined,
  });
}

/** 계정 생성 요청 바디 */
export interface CreateUserBody {
  username: string;
  password: string;
  name?: string;
  role: "USER" | "ADMIN";
}

/** 계정 생성 (ADMIN). 실패: username 규칙/중복/비밀번호 8자 400 */
export function create(body: CreateUserBody): Promise<AdminUserItem> {
  return request<AdminUserItem>(`/admin/users`, { method: "POST", body });
}

/** 역할 변경 (ADMIN). 404/400(본인 보호·마지막 ADMIN) */
export function updateRole(id: number, role: "USER" | "ADMIN"): Promise<void> {
  return request<void>(`/admin/users/${id}/role`, { method: "PATCH", body: { role } });
}

/** 상태 변경 (ADMIN). 404/400(본인 보호·마지막 ACTIVE ADMIN) */
export function updateStatus(id: number, status: "ACTIVE" | "INACTIVE"): Promise<void> {
  return request<void>(`/admin/users/${id}/status`, { method: "PATCH", body: { status } });
}

/** 비밀번호 지정 (ADMIN). 404/비밀번호 8자 400. 임시 발급이 아닌 직접 지정 */
export function updatePassword(id: number, password: string): Promise<void> {
  return request<void>(`/admin/users/${id}/password`, { method: "PATCH", body: { password } });
}
