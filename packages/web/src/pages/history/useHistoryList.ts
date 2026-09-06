// 전체 히스토리 페이지 데이터 훅. executionsApi.history 를 필터 + 커서 무한스크롤로 조회.
// - 필터(keyword/apiSpecId/status/from/to)가 queryKey 에 포함되어, 필터가 바뀌면
//   자동으로 커서가 리셋되고 처음부터 다시 로드된다(history-full.md URL 쿼리 동기화 규약).
// - 빈 배열/빈 문자열 필터는 API 호출에서 생략(=전체). 정렬은 서버 고정(startedAt DESC).
// - 사이드 패널 히스토리 탭(useHistory, 필터 없음)과 별개 훅으로, 뷰만 다르고 API 는 공유한다.

import { useInfiniteQuery } from "@tanstack/react-query";
import { executionsApi } from "../../api";
import type { CursorPage, ExecutionSummaryView } from "../../api/types";

const PAGE_SIZE = 20;

/** URL 조작 방어: 알려진 완료 상태 4종만 API 로 전달(history-full.md 확정) */
const ALLOWED_STATUSES = new Set(["SUCCESS", "FAILED", "STOPPED", "CANCELLED"]);

export interface HistoryFilters {
  keyword: string;
  /** 서비스(apiSpecId) 다중 */
  specIds: number[];
  /** 상태 enum 다중 (SUCCESS/FAILED/STOPPED/CANCELLED) */
  statuses: string[];
  /** YYYY-MM-DD */
  from: string;
  /** YYYY-MM-DD */
  to: string;
}

export function useHistoryList(filters: HistoryFilters) {
  const { keyword, specIds, statuses, from, to } = filters;
  // 화이트리스트 통과분만 사용(조작된 status 값 무시). specIds 의 Number.isFinite 필터와 동일 취지.
  const validStatuses = statuses.filter((s) => ALLOWED_STATUSES.has(s));
  return useInfiniteQuery<CursorPage<ExecutionSummaryView>>({
    // 필터 전체를 키에 포함 → 필터 변경 시 커서 리셋 + 재조회
    queryKey: ["executions", "history-page", { keyword, specIds, statuses: validStatuses, from, to }],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      executionsApi.history({
        keyword: keyword.trim() || undefined,
        apiSpecId: specIds.length > 0 ? specIds : undefined,
        status: validStatuses.length > 0 ? validStatuses : undefined,
        from: from || undefined,
        to: to || undefined,
        cursor: pageParam as string | undefined,
        size: PAGE_SIZE,
      }),
    getNextPageParam: (last) => (last.hasNext ? last.nextCursor ?? undefined : undefined),
  });
}
