// 결과 상세 뷰 데이터 훅. executionsApi.getExecution(id) 단건 조회.
// - queryKey: ['execution', executionId] — 히스토리 목록(['executions'])과 구분한다.
// - enabled: executionId 가 있을 때만 조회(상세 뷰 진입 시).

import { useQuery } from "@tanstack/react-query";
import { ApiError, executionsApi } from "../../../api";
import type { ExecutionResponse } from "../../../api/types";

export function useExecutionDetail(executionId: number | null) {
  return useQuery<ExecutionResponse>({
    queryKey: ["execution", executionId],
    queryFn: () => executionsApi.getExecution(executionId as number),
    enabled: executionId != null,
    // 404(타인 소유/삭제)는 재시도하지 않고 즉시 빈 상태로 안내한다(히스토리 상세 딥링크 대비).
    retry: (count, err) => !(err instanceof ApiError && err.status === 404) && count < 2,
  });
}
