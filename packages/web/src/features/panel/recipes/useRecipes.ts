// 레시피 탭 데이터 훅.
// 현재 BE recipesApi.list 는 배열(페이지네이션 없음)을 반환하므로, 1차는 단순 목록으로 조회한다.
// (무한스크롤 골격은 shared/useInfiniteScroll + history 에 존재. recipes 는 BE 가 커서를
//  지원하게 되면 useInfiniteQuery 로 승격한다 — 지금은 과설계 금지.)
//
// - 서비스 드롭다운/검색/필터는 서버 쿼리 파라미터로 전달(apiSpecId/keyword/visibility).
// - keyword 디바운스(300ms)는 RecipesView 에서 처리해 이 훅에 확정값으로 넘긴다.

import { useQuery } from "@tanstack/react-query";
import { recipesApi, specsApi } from "../../../api";
import type { RecipeSummary, SpecListItem } from "../../../api/types";

export type RecipeFilter = "all" | "private" | "common";

/** 필터 → BE visibility 코드 (all 이면 미전달) */
function toVisibility(filter: RecipeFilter): string | undefined {
  if (filter === "private") return "PRIVATE";
  if (filter === "common") return "COMMON";
  return undefined;
}

/** 서비스(스펙) 목록 — 드롭다운용. 자주 안 바뀌므로 staleTime 넉넉히. */
export function useServices() {
  return useQuery<SpecListItem[]>({
    queryKey: ["specs"],
    queryFn: () => specsApi.list(),
    staleTime: 5 * 60 * 1000,
  });
}

interface RecipesParams {
  /** 탐색 대상 서비스. null = 전체 서비스 */
  apiSpecId: number | null;
  /** 디바운스 완료된 검색어 */
  keyword: string;
  filter: RecipeFilter;
}

export function useRecipes({ apiSpecId, keyword, filter }: RecipesParams) {
  const trimmed = keyword.trim();
  return useQuery<RecipeSummary[]>({
    queryKey: ["recipes", apiSpecId, trimmed, filter],
    queryFn: () =>
      recipesApi.list({
        apiSpecId: apiSpecId ?? undefined,
        keyword: trimmed || undefined,
        visibility: toVisibility(filter),
      }),
  });
}
