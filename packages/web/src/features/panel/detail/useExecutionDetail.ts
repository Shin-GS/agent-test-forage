// 결과 상세 뷰 데이터 훅. executionsApi.getExecution(id) 단건 조회.
// - queryKey: ['execution', executionId] — 히스토리 목록(['executions'])과 구분한다.
// - enabled: executionId 가 있을 때만 조회(상세 뷰 진입 시).

import { useQuery } from "@tanstack/react-query";
import { executionsApi } from "../../../api";
import type { ExecutionResponse } from "../../../api/types";

export function useExecutionDetail(executionId: number | null) {
  return useQuery<ExecutionResponse>({
    queryKey: ["execution", executionId],
    queryFn: () => executionsApi.getExecution(executionId as number),
    enabled: executionId != null,
  });
}
