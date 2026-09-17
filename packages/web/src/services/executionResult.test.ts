// resolveAuthApiSpecId 회귀 방지 테스트 — 멀티서비스 인증 서비스 해석.
// 인증(401/403)이 난 스텝의 실제 서비스(apiSpecId)를 해석한다:
// RUNNING 레시피의 resolvedSteps[stepIndex].apiSpecId 우선, 없으면 execution.apiSpecId 폴백.
// 이 파일은 순수 함수 단위 테스트라 api/스토어 mock 불필요.

import { describe, it, expect } from "vitest";
import { resolveAuthApiSpecId } from "./executionResult";
import type { ExecutionResponse } from "../api/types";

/**
 * 최소 ExecutionResponse 구성 헬퍼.
 * - apiSpecId: 레시피 대표 서비스(폴백 대상)
 * - recipeStatus: 레시피 상태 코드(RUNNING 이어야 resolvedSteps 사용)
 * - resolvedSteps: recipeSnapshot.resolvedSteps (null 이면 구 스냅샷)
 */
function makeExecution(opts: {
  apiSpecId: number;
  recipeStatus?: string;
  resolvedSteps?: Array<{ stepIndex: number; apiSpecId?: number }> | null;
}): ExecutionResponse {
  const { apiSpecId, recipeStatus = "RUNNING", resolvedSteps } = opts;
  const recipes =
    resolvedSteps === undefined
      ? [] // 레시피 없음 케이스
      : [
          {
            status: { code: recipeStatus },
            recipeSnapshot:
              resolvedSteps === null
                ? { steps: [] } // resolvedSteps 자체 없음(구 스냅샷)
                : { resolvedSteps },
          },
        ];
  return {
    apiSpecId,
    conversationId: 1,
    recipes,
  } as unknown as ExecutionResponse;
}

describe("resolveAuthApiSpecId", () => {
  it("resolvedSteps 의 해당 stepIndex apiSpecId 를 반환한다(대표 apiSpecId 무시, 멀티서비스)", () => {
    const execution = makeExecution({
      apiSpecId: 1, // 레시피 대표
      resolvedSteps: [
        { stepIndex: 0, apiSpecId: 1 },
        { stepIndex: 1, apiSpecId: 4 }, // 인증 실패 스텝의 실제 서비스
      ],
    });
    expect(resolveAuthApiSpecId(execution, 1)).toBe(4);
  });

  it("resolvedSteps 에 해당 stepIndex 가 없으면 execution.apiSpecId 로 폴백한다", () => {
    const execution = makeExecution({
      apiSpecId: 9,
      resolvedSteps: [{ stepIndex: 0, apiSpecId: 4 }],
    });
    expect(resolveAuthApiSpecId(execution, 2)).toBe(9);
  });

  it("RUNNING 레시피가 없으면 execution.apiSpecId 로 폴백한다", () => {
    const execution = makeExecution({
      apiSpecId: 7,
      recipeStatus: "SUCCESS", // RUNNING 아님 → findRunningRecipe null
      resolvedSteps: [{ stepIndex: 0, apiSpecId: 4 }],
    });
    expect(resolveAuthApiSpecId(execution, 0)).toBe(7);
  });

  it("resolvedSteps 자체가 없으면(구 스냅샷) execution.apiSpecId 로 폴백한다", () => {
    const execution = makeExecution({
      apiSpecId: 3,
      resolvedSteps: null,
    });
    expect(resolveAuthApiSpecId(execution, 0)).toBe(3);
  });
});
