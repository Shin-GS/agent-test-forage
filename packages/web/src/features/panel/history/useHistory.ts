// 히스토리 탭 데이터 훅. executionsApi.history 를 커서 무한스크롤로 조회.
// - queryKey: ['executions', userId] (채팅 실행 완료 시 App 에서 invalidateQueries(['executions']))
// - getNextPageParam: 마지막 페이지의 nextCursor (hasNext=false 면 undefined → 종료)

import { useInfiniteQuery } from "@tanstack/react-query";
import { executionsApi } from "../../../api";
import type { CursorPage, ExecutionSummaryView } from "../../../api/types";

const PAGE_SIZE = 20;

export function useHistory(userId: number) {
  return useInfiniteQuery<CursorPage<ExecutionSummaryView>>({
    queryKey: ["executions", userId],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      executionsApi.history(userId, { cursor: pageParam as string | undefined, size: PAGE_SIZE }),
    getNextPageParam: (last) => (last.hasNext ? last.nextCursor ?? undefined : undefined),
  });
}
