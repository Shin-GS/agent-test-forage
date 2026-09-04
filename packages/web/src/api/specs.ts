// 서비스 스펙(API 명세) 조회.
// 레시피 실행 시 스텝의 endpointId 를 실제 { httpMethod, path } 로 해석하고,
// 외부 서버 baseUrl 을 얻기 위해 사용한다.

import { request } from "./client";
import type { SpecDetail, SpecListItem } from "./types";

/** 스펙 상세 조회 (endpoints/ baseUrl 포함) */
export function getSpec(specId: number): Promise<SpecDetail> {
  return request<SpecDetail>(`/specs/${specId}`, { method: "GET" });
}

/**
 * 등록된 스펙(서비스) 목록 조회. 사이드 패널 레시피 탭의 서비스 드롭다운 소비용.
 * BE: GET /api/v1/specs → SpecResponse[] (id/name 포함).
 */
export function list(): Promise<SpecListItem[]> {
  return request<SpecListItem[]>(`/specs`, { method: "GET" });
}
