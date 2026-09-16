// 서비스 스펙(API 명세) 조회 + 관리자 상태 관리.
// - 레시피 실행 시 스텝의 endpointId 를 실제 { httpMethod, path } 로 해석하고,
//   외부 서버 baseUrl 을 얻기 위해 사용한다.
// - 관리자 화면(스펙 관리)은 전체(INACTIVE 포함) 목록 + 상태 토글/삭제를 사용한다.

import { request } from "./client";
import type {
  EndpointDetail,
  ManualEndpointRequest,
  ManualSpecRequest,
  SpecDetail,
  SpecListItem,
} from "./types";

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

/**
 * 서버 수동 등록 (ADMIN, 비-admin 403). 라이브러리를 못 붙이는 외부 서버를 관리자가 직접 등록.
 * baseUrl 이 이미 등록되어 있으면 신규 생성 대신 기존 스펙에 병합하고 그 스펙 id 를 반환한다(FE 는 반환 id 로 이동).
 * 검증 실패(baseUrl 형식/중복 규칙 등)는 400 ApiError.
 * BE: POST /api/v1/specs/manual → 201 { id }.
 */
export function createManual(body: ManualSpecRequest): Promise<{ id: number }> {
  return request<{ id: number }>(`/specs/manual`, { method: "POST", body });
}

/**
 * 스펙 메타 수정 (ADMIN, 비-admin 403). name/description/domain/capabilities/notes/authProfiles 갱신.
 * baseUrl 은 보내도 서버가 무시한다(식별 키). 수정본은 adminEdited 로 저장되어 라이브러리 재등록이 덮어쓰지 않는다.
 * BE: PATCH /api/v1/specs/{id} → 204 No Content(본문 없음).
 */
export function updateMeta(specId: number, body: ManualSpecRequest): Promise<void> {
  return request<void>(`/specs/${specId}`, { method: "PATCH", body });
}

// ---------------------------------------------------------------------------
// Endpoint (관리자 수동 API 등록/편집 — Case 9)
// ---------------------------------------------------------------------------

/**
 * 엔드포인트 상세 조회 (ADMIN, 비-admin 403). operationJson(문자열) 포함.
 * 수정 모드 진입 시 operationJson 을 파싱해 폼을 복원한다.
 * BE: GET /api/v1/specs/{id}/endpoints/{endpointId} → EndpointDetail.
 */
export function getEndpoint(specId: number, endpointId: number): Promise<EndpointDetail> {
  return request<EndpointDetail>(`/specs/${specId}/endpoints/${endpointId}`, { method: "GET" });
}

/**
 * 엔드포인트 신규 등록 (ADMIN, 비-admin 403). 항상 source=MANUAL 로 생성된다.
 * (method, path) 가 같은 스펙 내에서 중복이면 서버 400.
 * BE: POST /api/v1/specs/{id}/endpoints → 201 { id }.
 */
export function createEndpoint(
  specId: number,
  body: ManualEndpointRequest,
): Promise<{ id: number }> {
  return request<{ id: number }>(`/specs/${specId}/endpoints`, { method: "POST", body });
}

/**
 * 엔드포인트 수정 (ADMIN, 비-admin 403).
 * LIBRARY/DEPRECATED 는 서버가 스키마 필드를 무시하고 메타(excluded/confirm*)만 반영한다.
 * BE: PATCH /api/v1/specs/{id}/endpoints/{endpointId} → 204 No Content(본문 없음).
 */
export function updateEndpoint(
  specId: number,
  endpointId: number,
  body: ManualEndpointRequest,
): Promise<void> {
  return request<void>(`/specs/${specId}/endpoints/${endpointId}`, {
    method: "PATCH",
    body,
  });
}

/** 엔드포인트 삭제 (ADMIN, 비-admin 403). 소프트 삭제/상태 전이. Case 9 에서는 미사용, #9 에서 사용 */
export function deleteEndpoint(specId: number, endpointId: number): Promise<void> {
  return request<void>(`/specs/${specId}/endpoints/${endpointId}`, { method: "DELETE" });
}

/**
 * 엔드포인트 복제 (ADMIN, 비-admin 403). 출처 무관 복제본은 항상 source=MANUAL.
 * 반환된 신규 엔드포인트 id 로 편집 페이지에 진입해 path 를 유일하게 바꾸도록 유도한다.
 * Case 9 에서는 미사용, #9 에서 사용.
 * BE: POST /api/v1/specs/{id}/endpoints/{endpointId}/duplicate → 201 { id }.
 */
export function duplicateEndpoint(specId: number, endpointId: number): Promise<{ id: number }> {
  return request<{ id: number }>(`/specs/${specId}/endpoints/${endpointId}/duplicate`, {
    method: "POST",
  });
}
