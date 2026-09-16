// endpointOperation 왕복(round-trip) 테스트.
// - 폼 → operationJson 직렬화 → 파싱 → 폼 복원 시 의미가 보존되는지 확인.
// - GET/DELETE 는 요청 바디를 미포함(보존은 폼 상태에서만)하는지 확인.
// - 잘못된 JSON 은 null 폴백하는지 확인.

import { describe, it, expect } from "vitest";
import { formToOperation, parseOperationJson, methodAllowsBody } from "./endpointOperation";
import type { EndpointFormState } from "./endpointFormTypes";

function baseForm(): EndpointFormState {
  return {
    method: "POST",
    path: "/users/{id}",
    summary: "회원 수정",
    confirmRequired: false,
    confirmMessage: "",
    excluded: false,
    pathParams: [{ _uid: "pp1", name: "id", type: "string", required: true, description: "사용자 ID" }],
    queryParams: [{ _uid: "qp1", name: "page", type: "integer", required: false, description: "" }],
    requestBodyContentType: "application/json",
    requestBodyFields: [
      { _uid: "bf1", name: "email", type: "string", required: true, description: "이메일" },
      { _uid: "bf2", name: "age", type: "integer", required: false, description: "" },
    ],
    requestHeaders: [{ _uid: "rh1", name: "X-Api-Key", required: true, description: "API 키" }],
    responses: [
      {
        _uid: "resp1",
        statusCode: "200",
        description: "성공",
        headers: [{ _uid: "resph1", name: "Location", required: false, description: "생성 위치" }],
      },
    ],
  };
}

describe("endpointOperation round-trip", () => {
  it("폼 → operationJson → 파싱 시 파라미터/바디/헤더/응답이 보존된다", () => {
    const form = baseForm();
    const op = formToOperation(form);
    const restored = parseOperationJson(JSON.stringify(op));

    expect(restored).not.toBeNull();
    if (!restored) return;

    expect(restored.summary).toBe("회원 수정");

    expect(restored.pathParams.map((p) => p.name)).toEqual(["id"]);
    expect(restored.pathParams[0].required).toBe(true);
    expect(restored.pathParams[0].type).toBe("string");

    expect(restored.queryParams.map((p) => p.name)).toEqual(["page"]);
    expect(restored.queryParams[0].required).toBe(false);

    expect(restored.requestBodyContentType).toBe("application/json");
    const bodyByName = Object.fromEntries(restored.requestBodyFields.map((f) => [f.name, f]));
    expect(bodyByName.email.required).toBe(true);
    expect(bodyByName.age.required).toBe(false);

    expect(restored.requestHeaders.map((h) => h.name)).toEqual(["X-Api-Key"]);
    expect(restored.requestHeaders[0].required).toBe(true);

    expect(restored.responses.map((r) => r.statusCode)).toEqual(["200"]);
    expect(restored.responses[0].headers.map((h) => h.name)).toEqual(["Location"]);
  });

  it("GET/DELETE 는 requestBody 를 직렬화하지 않는다", () => {
    const form = { ...baseForm(), method: "GET" };
    const op = formToOperation(form);
    expect(op.requestBody).toBeNull();
    expect(methodAllowsBody("GET")).toBe(false);
    expect(methodAllowsBody("DELETE")).toBe(false);
    expect(methodAllowsBody("POST")).toBe(true);
  });

  it("잘못된 JSON 은 null 폴백한다", () => {
    expect(parseOperationJson("{not valid json")).toBeNull();
  });

  it("null/빈 operationJson 은 빈 폼 결과를 반환한다", () => {
    const result = parseOperationJson(null);
    expect(result).not.toBeNull();
    expect(result?.pathParams).toEqual([]);
    expect(result?.requestBodyFields).toEqual([]);
  });
});
