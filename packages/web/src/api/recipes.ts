// 레시피 조회 API 클라이언트 (사이드 패널 레시피 목록 소비용).

import { request } from "./client";
import type { RecipeSummary } from "./types";

/**
 * 레시피 목록 조회.
 * - apiSpecId: 대상 서비스 필터(현재 대화방 서비스)
 * - keyword: name/description LIKE 검색
 * - visibility: COMMON / PRIVATE 필터
 * name 오름차순, 삭제 제외 (BE RecipeController.list).
 */
export function list(params?: {
  apiSpecId?: number;
  keyword?: string;
  visibility?: string;
}): Promise<RecipeSummary[]> {
  return request<RecipeSummary[]>(`/recipes`, {
    method: "GET",
    query: {
      apiSpecId: params?.apiSpecId,
      keyword: params?.keyword,
      visibility: params?.visibility,
    },
  });
}
