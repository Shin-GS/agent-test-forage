// 서비스 스펙(API 명세) 조회 + 관리자 상태 관리.
// - 레시피 실행 시 스텝의 endpointId 를 실제 { httpMethod, path } 로 해석하고,
//   외부 서버 baseUrl 을 얻기 위해 사용한다.
// - 관리자 화면(스펙 관리)은 전체(INACTIVE 포함) 목록 + 상태 토글/삭제를 사용한다.

import { request } from "./client";
import type { SpecDetail, SpecListItem } from "./types";

/** 스펙 상세 조회 (endpoints/ baseUrl 포함). 삭제 시 404, INACTIVE 도 조회 가능 */
export function getSpec(specId: number): Promise<SpecDetail> {
  return request<SpecDetail>(`/specs/${specId}`, { method: "GET" });
}

/**
 * 등록된 스펙(서비스) 목록 조회.
 * - 기본(파라미터 없음/false): ACTIVE 만 반환.
 * - includeInactive=true: 관리자 화면 전체 조회용(INACTIVE 포함). ADMIN 에게만 유효하며
 *   비-admin 이 true 를 줘도 서버가 조용히 무시하고 ACTIVE 만 반환한다(403 아님).
 * BE: GET /api/v1/specs → SpecSummaryResponse[].
 */
export function list(params?: { includeInactive?: boolean }): Promise<SpecListItem[]> {
  return request<SpecListItem[]>(`/specs`, {
    method: "GET",
    query: params?.includeInactive ? { includeInactive: true } : undefined,
  });
}

/** 스펙 비활성화 (ADMIN, 비-admin 403). 멱등. INACTIVE 로 전환되어 AI 매칭/실행에서 제외 */
export function deactivate(specId: number): Promise<void> {
  return request<void>(`/specs/${specId}/deactivate`, { method: "PATCH" });
}

/** 스펙 활성화 (ADMIN, 비-admin 403). 멱등. INACTIVE → ACTIVE 복귀 */
export function activate(specId: number): Promise<void> {
  return request<void>(`/specs/${specId}/activate`, { method: "PATCH" });
}

/** 스펙 소프트 삭제 (ADMIN, 비-admin 403). 목록에서 제외, 참조 레시피는 실행 전 경고 */
export function remove(specId: number): Promise<void> {
  return request<void>(`/specs/${specId}`, { method: "DELETE" });
}
