// 서버 설정 조회 (읽기 전용).
// 설정 페이지(/settings)에서 현재 적용된 AI/레시피 실행 설정을 표시하는 용도.
// 저장/수정 API 는 없다 — 설정 변경은 서버 설정 파일(.env / application.yml)로만 한다.
// API 키 등 시크릿은 응답에 포함되지 않는다.

import { request } from "./client";
import type { SettingsResponse } from "./types";

/** GET /api/v1/settings — 현재 적용된 AI/실행 설정을 읽기 전용으로 반환 */
export function get(): Promise<SettingsResponse> {
  return request<SettingsResponse>(`/settings`, { method: "GET" });
}
