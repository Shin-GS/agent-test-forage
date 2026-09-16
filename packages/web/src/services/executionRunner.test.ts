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
