// recipeForm 왕복(round-trip) 테스트 — 2단계 요청 헤더 매핑 + 레시피별 기본값.
// - formStepToServer → serverStepToForm 왕복 시 headerMappings/defaultValue 가 보존되는지 확인.
// - 기본값/헤더가 없는 기존 레시피는 저장 형태가 동일(하위 호환)한지 확인.

import { describe, it, expect } from "vitest";
import { formStepToServer, serverStepToForm } from "./recipeForm";
import type { ApiRecipeStep, FieldMapping } from "../../api/types";

function apiStep(over: Partial<ApiRecipeStep> = {}): ApiRecipeStep {
  return {
    _uid: "s1",
    type: "api",
    name: "회원 조회",
    label: "",
    apiSpecId: 1,
    endpointId: 42,
    pathParamMappings: [],
    requestMappings: [],
    headerMappings: [],
    extracts: [],
    condition: "",
    ...over,
  };
}

function mapping(over: Partial<FieldMapping>): FieldMapping {
  return { _uid: "m", field: "", source: "literal", value: "", ...over };
}

describe("recipeForm 왕복 — 헤더 매핑 + 기본값", () => {
  it("headerMappings 와 defaultValue 가 왕복 후 보존된다", () => {
    const step = apiStep({
      requestMappings: [
        mapping({ field: "email", source: "user_input", value: "userInput.email", required: true }),
        mapping({ field: "nickname", source: "user_input", value: "userInput.nickname", defaultValue: "게스트" }),
      ],
      headerMappings: [
        mapping({ field: "X-Auth-Key", source: "user_input", value: "userInput.authKey", required: true }),
        mapping({ field: "X-Client-Id", source: "literal", value: "demo-client", defaultValue: "demo-client" }),
      ],
    });

    const server = formStepToServer(step);
    // 서버 저장 형태: headers 객체맵 + 별도 기본값 맵
    expect(server.headers).toBeDefined();
    expect((server.headerDefaults as Record<string, string>)["X-Client-Id"]).toBe("demo-client");
    expect((server.bodyDefaults as Record<string, string>)["nickname"]).toBe("게스트");

    const restored = serverStepToForm(server, 1);
    expect(restored.type).toBe("api");
    if (restored.type !== "api") return;

    const header = restored.headerMappings.find((m) => m.field === "X-Client-Id");
    expect(header?.source).toBe("literal");
    expect(header?.value).toBe("demo-client");
    expect(header?.defaultValue).toBe("demo-client");

    const authHeader = restored.headerMappings.find((m) => m.field === "X-Auth-Key");
    expect(authHeader?.source).toBe("user_input");
    expect(authHeader?.value).toBe("userInput.authKey");

    const nickname = restored.requestMappings.find((m) => m.field === "nickname");
    expect(nickname?.defaultValue).toBe("게스트");
  });

  it("헤더/기본값이 없으면 기존 저장 형태와 동일(하위 호환)", () => {
    const step = apiStep({
      requestMappings: [mapping({ field: "email", source: "user_input", value: "userInput.email" })],
    });
    const server = formStepToServer(step);
    // 헤더/기본값 관련 키가 생성되지 않아야 한다
    expect(server.headers).toBeUndefined();
    expect(server.headerDefaults).toBeUndefined();
    expect(server.bodyDefaults).toBeUndefined();
    expect(server.pathParamDefaults).toBeUndefined();
    // body 객체맵은 기존과 동일하게 유지
    expect(server.body).toEqual({ email: "{{userInput.email}}" });
  });

  it("구 데이터(headers 없음)를 로드해도 headerMappings 는 빈 배열", () => {
    const legacy = { type: "api", name: "old", endpointId: 1, body: { email: "{{userInput.email}}" } };
    const restored = serverStepToForm(legacy, 1);
    expect(restored.type).toBe("api");
    if (restored.type !== "api") return;
    expect(restored.headerMappings).toEqual([]);
  });
});

describe("recipeForm 왕복 — 스텝 apiSpecId (멀티 서비스 확정)", () => {
  it("스텝 apiSpecId(명시)가 있으면 그대로 직렬화되고 왕복 후 보존된다", () => {
    const step = apiStep({ apiSpecId: 7 });
    const server = formStepToServer(step, 1); // 레시피 대상은 1이지만 스텝 명시 7 우선
    expect(server.apiSpecId).toBe(7);

    const restored = serverStepToForm(server, 1);
    expect(restored.type).toBe("api");
    if (restored.type !== "api") return;
    expect(restored.apiSpecId).toBe(7);
  });

  it("스텝 apiSpecId 가 없으면(상속) 레시피 대상 apiSpecId 로 확정 저장된다", () => {
    const step = apiStep({ apiSpecId: null });
    const server = formStepToServer(step, 5);
    expect(server.apiSpecId).toBe(5); // 상속 확정

    const restored = serverStepToForm(server, 5);
    expect(restored.type).toBe("api");
    if (restored.type !== "api") return;
    expect(restored.apiSpecId).toBe(5);
  });

  it("스텝/레시피 apiSpecId 둘 다 없으면 apiSpecId 를 생략한다(빈 스텝)", () => {
    const step = apiStep({ apiSpecId: null });
    const server = formStepToServer(step, null);
    expect(server.apiSpecId).toBeUndefined();

    // 복원 시에도 recipeApiSpecId 가 없으면 null 로 유지
    const restored = serverStepToForm(server, null);
    expect(restored.type).toBe("api");
    if (restored.type !== "api") return;
    expect(restored.apiSpecId).toBeNull();
  });
});
