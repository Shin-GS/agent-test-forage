// 서비스 스펙(API 명세) 조회.
// 레시피 실행 시 스텝의 endpointId 를 실제 { httpMethod, path } 로 해석하고,
// 외부 서버 baseUrl 을 얻기 위해 사용한다.

import { request } from "./client";
import type { SpecDetail } from "./types";

/** 스펙 상세 조회 (endpoints/ baseUrl 포함) */
export function getSpec(specId: number): Promise<SpecDetail> {
  return request<SpecDetail>(`/specs/${specId}`, { method: "GET" });
}
