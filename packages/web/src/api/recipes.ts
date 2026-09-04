// 레시피 조회/편집 API 클라이언트.
// 사이드 패널 목록 소비(list) + 레시피 편집 페이지(detail/create/update/remove).

import { request } from "./client";
import type {
  RecipeCreateRequest,
  RecipeDetail,
  RecipeSummary,
  RecipeUpdateRequest,
} from "./types";

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

/** 레시피 상세 조회 (편집 페이지 로드용). 없거나 삭제 시 404. */
export function detail(id: number): Promise<RecipeDetail> {
  return request<RecipeDetail>(`/recipes/${id}`, { method: "GET" });
}

/** 레시피 생성 (201). 순환 참조/검증 실패 시 400. */
export function create(body: RecipeCreateRequest): Promise<RecipeDetail> {
  return request<RecipeDetail>(`/recipes`, { method: "POST", body });
}

/** 레시피 수정 (버전 스냅샷 + 재검증). 순환 참조/검증 실패 시 400. */
export function update(id: number, body: RecipeUpdateRequest): Promise<RecipeDetail> {
  return request<RecipeDetail>(`/recipes/${id}`, { method: "PUT", body });
}

/** 레시피 소프트 삭제 (204). */
export function remove(id: number): Promise<void> {
  return request<void>(`/recipes/${id}`, { method: "DELETE" });
}
