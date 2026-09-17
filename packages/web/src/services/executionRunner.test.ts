// executionRunner 레시피 기본값 폴백 테스트 — body 최상위 필드 + pathParams.
// authoring.md: 요청 필드/헤더/경로 파라미터는 "실행 시 사용자 입력 > 레시피 기본값" 폴백.
// 헤더는 이미 resolveMappedValue 로 적용됨. 여기선 body/path 폴백이 헤더와 동일 규칙인지 확인한다.

import { describe, it, expect } from "vitest";
import { __test__ } from "./executionRunner";

const { applyPathParams, applyBodyDefaults, substituteDeep } = __test__;

// body 폴백을 실제 executeApiStep 경로와 동일하게 재현: substituteDeep 후 applyBodyDefaults.
function resolveBody(rawBody: any, defaults: Record<string, string> | undefined, context: any): any {
  return applyBodyDefaults(substituteDeep(rawBody, context), rawBody, defaults, context);
}

describe("body 최상위 필드 기본값 폴백", () => {
  it("사용자 입력이 있으면 입력 값을 사용한다(기본값 무시)", () => {
    const body = resolveBody(
      { nickname: "{{userInput.nickname}}" },
      { nickname: "게스트" },
      { userInput: { nickname: "홍길동" } },
    );
    expect(body.nickname).toBe("홍길동");
  });

  it("사용자 입력이 비면(미해석) 레시피 기본값으로 폴백한다", () => {
    const body = resolveBody(
      { nickname: "{{userInput.nickname}}" },
      { nickname: "게스트" },
      { userInput: {} },
    );
    expect(body.nickname).toBe("게스트");
  });

  it("기본값 맵이 없으면 substituteDeep 결과와 동일(회귀 없음)", () => {
    const context = { userInput: { email: "a@b.com" } };
    const raw = { email: "{{userInput.email}}", note: "고정" };
    expect(resolveBody(raw, undefined, context)).toEqual(substituteDeep(raw, context));
  });

  it("중첩 객체/배열은 건드리지 않는다(substituteDeep 동작 유지)", () => {
    const context = { userInput: { id: 7 } };
    const raw = { profile: { id: "{{userInput.id}}" }, tags: ["{{userInput.id}}"] };
    const body = resolveBody(raw, { profile: "x" }, context);
    // 최상위 profile 은 객체이므로 문자열 규칙 대상 아님 → 그대로 치환된 객체
    expect(body.profile).toEqual({ id: 7 });
    expect(body.tags).toEqual([7]);
  });

  it("whole-match 원시타입 보존: 값이 있으면 number 유지", () => {
    const body = resolveBody(
      { amount: "{{userInput.amount}}" },
      { amount: "0" },
      { userInput: { amount: 100 } },
    );
    expect(body.amount).toBe(100);
  });
});

describe("pathParams 기본값 폴백", () => {
  it("사용자 입력이 있으면 입력 값으로 경로를 채운다", () => {
    const path = applyPathParams(
      "/orders/{id}",
      { id: "{{userInput.orderId}}" },
      { id: "999" },
      { userInput: { orderId: "42" } },
    );
    expect(path).toBe("/orders/42");
  });

  it("사용자 입력이 비면 레시피 기본값으로 폴백한다", () => {
    const path = applyPathParams(
      "/orders/{id}",
      { id: "{{userInput.orderId}}" },
      { id: "999" },
      { userInput: {} },
    );
    expect(path).toBe("/orders/999");
  });

  it("기본값이 없고 값도 비면 빈 문자열로 치환(기존 동작 유지)", () => {
    const path = applyPathParams(
      "/orders/{id}",
      { id: "{{userInput.orderId}}" },
      undefined,
      { userInput: {} },
    );
    expect(path).toBe("/orders/");
  });

  it("pathParams 가 없으면 템플릿 그대로 반환", () => {
    expect(applyPathParams("/health", undefined, undefined, {})).toBe("/health");
  });
});

// ── 멀티 서비스(스텝별 baseUrl) 해석 테스트 ─────────────────────────────────
// execution.md: resolvedSteps 우선(스텝별 baseUrl+method+path), stepIndex 못 찾으면 명확히 실패,
// resolvedSteps 자체가 없으면 레거시 단일 spec 경로로 폴백.

import { afterEach, beforeEach, vi } from "vitest";

const { extractResolvedSteps, resolveStepEndpoint, executeApiStep } = __test__;

// recipeSnapshot 만 채운 최소 ExecutionRecipeView (extractResolvedSteps 입력용).
function recipeWithSnapshot(snapshot: any): any {
  return { recipeSnapshot: snapshot };
}

// StepExecContext 최소 구성 헬퍼. resolvedStepMap/stepIndex/baseUrl/endpointMap 만 관여한다.
function execCtx(overrides: Partial<any>): any {
  return {
    baseUrl: "http://legacy.local",
    endpointMap: new Map(),
    resolvedStepMap: null,
    stepIndex: 0,
    mode: "AUTO",
    stepName: "step",
    ...overrides,
  };
}

describe("extractResolvedSteps", () => {
  it("resolvedSteps 배열을 stepIndex→항목 맵으로 만든다", () => {
    const map = extractResolvedSteps(
      recipeWithSnapshot({
        resolvedSteps: [
          { stepIndex: 0, apiSpecId: 4, endpointId: 10, method: "post", path: "/users", baseUrl: "http://a" },
          { stepIndex: 2, apiSpecId: 5, endpointId: 20, method: "get", path: "/orders/{id}", baseUrl: "http://b" },
        ],
      }),
    );
    expect(map).not.toBeNull();
    expect(map!.get(0)?.baseUrl).toBe("http://a");
    expect(map!.get(2)?.path).toBe("/orders/{id}");
    // 비연속 stepIndex(1 없음) 유지
    expect(map!.has(1)).toBe(false);
  });

  it("resolvedSteps 필드가 없으면 null(레거시 폴백 신호)", () => {
    expect(extractResolvedSteps(recipeWithSnapshot({ steps: [] }))).toBeNull();
    expect(extractResolvedSteps(recipeWithSnapshot(null))).toBeNull();
  });

  it("resolvedSteps 가 빈 배열이면 빈 맵(폴백 아님)", () => {
    const map = extractResolvedSteps(recipeWithSnapshot({ resolvedSteps: [] }));
    expect(map).not.toBeNull();
    expect(map!.size).toBe(0);
  });

});

describe("resolveStepEndpoint (스텝별 baseUrl 해석)", () => {
  it("resolvedSteps 로 그 stepIndex 의 baseUrl+method+path 를 쓴다", () => {
    const map = new Map([
      [3, { stepIndex: 3, method: "post", path: "/seats/{seatId}/bookings", baseUrl: "http://svc-b" }],
    ]);
    const resolved = resolveStepEndpoint({}, execCtx({ resolvedStepMap: map, stepIndex: 3 }));
    expect(resolved).toEqual({
      method: "post",
      path: "/seats/{seatId}/bookings",
      baseUrl: "http://svc-b",
    });
  });

  it("resolvedSteps 있는데 해당 stepIndex 없으면 명확히 실패한다", () => {
    const map = new Map([
      [0, { stepIndex: 0, method: "get", path: "/a", baseUrl: "http://svc-a" }],
    ]);
    expect(() =>
      resolveStepEndpoint({}, execCtx({ resolvedStepMap: map, stepIndex: 1 })),
    ).toThrow("스텝 1: 서비스(스펙)를 찾을 수 없습니다");
  });

  it("스텝의 명시적 method/path 는 resolvedSteps 보다 우선(baseUrl 은 resolvedSteps)", () => {
    const map = new Map([
      [0, { stepIndex: 0, method: "get", path: "/from-resolved", baseUrl: "http://svc-a" }],
    ]);
    const resolved = resolveStepEndpoint(
      { method: "DELETE", path: "/from-step" },
      execCtx({ resolvedStepMap: map, stepIndex: 0 }),
    );
    expect(resolved).toEqual({ method: "DELETE", path: "/from-step", baseUrl: "http://svc-a" });
  });

  it("resolvedSteps 미제공(null)이면 레거시 단일 spec 경로로 폴백한다", () => {
    const endpointMap = new Map([[10, { method: "GET", path: "/legacy" }]]);
    const resolved = resolveStepEndpoint(
      { endpointId: 10 },
      execCtx({ resolvedStepMap: null, endpointMap, baseUrl: "http://legacy.local" }),
    );
    expect(resolved).toEqual({ method: "GET", path: "/legacy", baseUrl: "http://legacy.local" });
  });
});

describe("executeApiStep (스텝별 baseUrl 로 요청 조립)", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn(async () => ({
      ok: true,
      status: 200,
      text: async () => JSON.stringify({ ok: true }),
    })) as any;
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("resolvedSteps 의 baseUrl+method+path 로 호출하고 pathParams 를 치환한다", async () => {
    const map = new Map([
      [0, { stepIndex: 0, method: "get", path: "/orders/{id}", baseUrl: "http://svc-b" }],
    ]);
    const result = await executeApiStep(
      { pathParams: { id: "{{userInput.orderId}}" } },
      { userInput: { orderId: "42" } },
      execCtx({ resolvedStepMap: map, stepIndex: 0 }),
    );
    const [calledUrl, init] = fetchMock.mock.calls[0];
    expect(calledUrl).toBe("http://svc-b/orders/42");
    expect(init.method).toBe("GET");
    expect(result.response).toEqual({ ok: true });
  });

  it("stepIndex 못 찾으면 fetch 없이 실패한다", async () => {
    const map = new Map([
      [0, { stepIndex: 0, method: "get", path: "/a", baseUrl: "http://svc-a" }],
    ]);
    await expect(
      executeApiStep({}, {}, execCtx({ resolvedStepMap: map, stepIndex: 5 })),
    ).rejects.toThrow("스텝 5: 서비스(스펙)를 찾을 수 없습니다");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("resolvedSteps 미제공 시 레거시 단일 baseUrl+endpointMap 으로 호출한다", async () => {
    const endpointMap = new Map([[10, { method: "GET", path: "/legacy" }]]);
    await executeApiStep(
      { endpointId: 10 },
      {},
      execCtx({ resolvedStepMap: null, endpointMap, baseUrl: "http://legacy.local" }),
    );
    const [calledUrl] = fetchMock.mock.calls[0];
    expect(calledUrl).toBe("http://legacy.local/legacy");
  });
});
