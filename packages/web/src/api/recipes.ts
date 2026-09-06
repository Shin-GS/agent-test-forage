// 레시피 조회/편집 API 클라이언트.
// 사이드 패널 목록 소비(list) + 레시피 편집 페이지(detail/create/update/remove) + 버전 관리 + 복제.

import { request } from "./client";
import type {
  CursorPage,
  RecipeCreateRequest,
  RecipeDetail,
  RecipeSummary,
  RecipeUpdateRequest,
  RecipeVersionDetail,
  RecipeVersionSummary,
} from "./types";

/** 목록 정렬 기준 (BE sort) */
export type RecipeSort = "recent" | "usage" | "name" | "updated";
/** 정렬 방향 (BE direction) */
export type SortDirection = "asc" | "desc";

/** 레시피 목록 조회 파라미터 (다중 필터 지원) */
export interface RecipeListParams {
  /** 대상 서비스 필터 (다중, 반복 쿼리). 빈 배열=전체 */
  apiSpecId?: number[];
  /** 공개범위 필터 (다중: COMMON/PRIVATE). 빈 배열=전체 */
  visibility?: string[];
  /** 태그 필터 (다중). 빈 배열=전체 */
  tag?: string[];
  /** name/description LIKE 검색 */
  keyword?: string;
  /** 정렬 기준 (기본 recent) */
  sort?: RecipeSort;
  /** 정렬 방향 (기본 desc) */
  direction?: SortDirection;
}

/**
 * 레시피 목록 조회.
 * 다중 필터(apiSpecId/visibility/tag)는 반복 쿼리 파라미터로 직렬화된다(client.buildUrl).
 * 빈 선택(빈 배열/미지정)은 "전체"로 취급되어 해당 축을 거르지 않는다.
 * 각 항목에 canEdit(권한 힌트) 포함. 삭제 제외 (BE RecipeController.list).
 */
export function list(params?: RecipeListParams): Promise<RecipeSummary[]> {
  return request<RecipeSummary[]>(`/recipes`, {
    method: "GET",
    query: {
      apiSpecId: params?.apiSpecId,
      visibility: params?.visibility,
      tag: params?.tag,
      keyword: params?.keyword,
      sort: params?.sort,
      direction: params?.direction,
    },
  });
}

/** 레시피 상세 조회 (편집 페이지 로드용). canEdit 포함. 없거나 삭제/타인 PRIVATE 시 404. */
export function detail(id: number): Promise<RecipeDetail> {
  return request<RecipeDetail>(`/recipes/${id}`, { method: "GET" });
}

/** 레시피 생성 (201). 순환 참조/검증 실패 시 400. */
export function create(body: RecipeCreateRequest): Promise<RecipeDetail> {
  return request<RecipeDetail>(`/recipes`, { method: "POST", body });
}

/** 레시피 수정 (버전 스냅샷 + 재검증). 순환 참조/검증 실패 시 400, 공통 non-admin 403. */
export function update(id: number, body: RecipeUpdateRequest): Promise<RecipeDetail> {
  return request<RecipeDetail>(`/recipes/${id}`, { method: "PUT", body });
}

/** 레시피 소프트 삭제 (204). 타인/없음 404, 공통 non-admin 403. */
export function remove(id: number): Promise<void> {
  return request<void>(`/recipes/${id}`, { method: "DELETE" });
}

/**
 * 버전 기록 목록 (커서 페이징). 최신순.
 * 응답 CursorPage<{versionNo, createdAt}> ({items, nextCursor, hasNext}).
 */
export function listVersions(
  id: number,
  cursor?: string,
  size?: number,
): Promise<CursorPage<RecipeVersionSummary>> {
  return request<CursorPage<RecipeVersionSummary>>(`/recipes/${id}/versions`, {
    method: "GET",
    query: { cursor, size },
  });
}

/** 특정 버전 상세 조회 (미리보기용, 읽기 전용). RecipeDetail 호환 + versionNo/createdAt. */
export function getVersion(id: number, versionNo: number): Promise<RecipeVersionDetail> {
  return request<RecipeVersionDetail>(`/recipes/${id}/versions/${versionNo}`, { method: "GET" });
}

/**
 * 특정 버전으로 복원 (새 버전 생성). 복원 후 최신 RecipeDetail 반환.
 * 복원 자체는 성공하되 스펙 변경으로 INVALID 상태가 될 수 있다.
 */
export function restoreVersion(id: number, versionNo: number): Promise<RecipeDetail> {
  return request<RecipeDetail>(`/recipes/${id}/versions/${versionNo}/restore`, {
    method: "POST",
  });
}

/** 레시피 복제 (201). 개인 사본 생성. 반환된 RecipeDetail 로 편집 페이지 진입. */
export function duplicate(id: number): Promise<RecipeDetail> {
  return request<RecipeDetail>(`/recipes/${id}/duplicate`, { method: "POST" });
}
