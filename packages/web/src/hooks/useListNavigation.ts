// 목록 ↔ 상세 네비게이션 헬퍼.
//
// 목록형 페이지(필터가 URL 쿼리에 있음)에서 상세로 진입한 뒤, 상세의 [← 목록으로]로
// 돌아올 때 "필터가 걸린 직전 목록"으로 복귀시키기 위한 공통 훅.
// 원칙: docs/specs/common/page-layout.md#상세--목록-복귀-필터-유지
//
// - 목록 → 상세 진입: navigateToDetail(path) — 현재 목록 URL(pathname+search)을
//   navigation state(fromList)에 담아 push 한다.
// - 상세 → 목록 복귀: useBackToList(fallback) — state.fromList 가 있으면 그리로,
//   없으면(URL 직접 진입 등) fallback 목록 경로로 이동한다.

import { useCallback } from "react";
import { useLocation, useNavigate, type NavigateOptions } from "react-router-dom";

/** 상세 진입 시 navigation state 에 담는 키. 상세 페이지가 복귀 대상으로 읽는다. */
interface ListNavState {
  /** 진입 직전 목록의 전체 URL(pathname + search). 필터 쿼리 포함. */
  fromList?: string;
}

/**
 * 목록 페이지에서 상세로 진입할 때 사용하는 navigate 래퍼.
 * 현재 목록 URL(필터 쿼리 포함)을 state.fromList 로 함께 전달한다.
 *
 * @example
 * const navigateToDetail = useListNavigation();
 * navigateToDetail(`/recipes/${id}/edit`);
 */
export function useListNavigation() {
  const navigate = useNavigate();
  const location = useLocation();

  return useCallback(
    (to: string, options?: NavigateOptions) => {
      const fromList = location.pathname + location.search;
      navigate(to, {
        ...options,
        state: { ...(options?.state as object | undefined), fromList } satisfies ListNavState,
      });
    },
    [navigate, location.pathname, location.search],
  );
}

/**
 * 상세/편집 페이지의 [← 목록으로] 뒤로가기 핸들러.
 * 진입 시 전달된 state.fromList(필터 걸린 목록 URL)가 있으면 그리로,
 * 없으면(딥링크로 상세에 직접 진입) fallback 목록 경로로 이동한다.
 *
 * @param fallback 직전 목록 정보가 없을 때 이동할 기본 목록 경로 (예: "/recipes")
 * @example
 * const backToList = useBackToList("/recipes");
 * <PageActionBar onBack={backToList} ... />
 */
export function useBackToList(fallback: string): () => void {
  const navigate = useNavigate();
  const location = useLocation();

  return useCallback(() => {
    const state = location.state as ListNavState | null;
    const target = state?.fromList ?? fallback;
    navigate(target);
  }, [navigate, location.state, fallback]);
}
