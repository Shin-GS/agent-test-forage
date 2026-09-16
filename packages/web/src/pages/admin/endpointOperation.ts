// 관리자 API 편집(Case 9) 폼 모델 ↔ OpenAPI Operation(operationJson) 직렬화/역직렬화 유틸.
//
// 배경: 수정 모드 진입 시 BE 가 내려준 EndpointDetail.operationJson(OpenAPI Operation 문자열)을
// 파싱해 폼(EndpointFormState)을 복원해야 한다. 신규/저장 시에는 폼을 ManualEndpointRequest
// (구조화된 parameters/requestBody/headers/responses)로 직렬화해 BE 로 보낸다.
//
// operationJson 구조(OpenAPI Operation, 최소 부분집합):
//   {
//     summary,
//     parameters: [{ name, in: "path"|"query"|"header", required, description, schema: { type } }],
//     requestBody: { content: { <contentType>: { schema: { type:"object", properties: {..}, required:[..] } } } },
//     responses: { <code>: { description, headers: { <name>: { required, description } } } }
//   }
//
// 폼 → 요청(ManualEndpointRequest)은 서버 계약(parameters[]/requestBody/headers[]/responses[])
// 형태로 보내면 되고, operationJson 직렬화는 서버가 담당한다(FE 는 파싱만 필요).
// 다만 왕복 테스트 및 안전한 복원을 위해 양방향 유틸을 모두 제공한다.

import type {
  EndpointFormState,
  ParameterRow,
  FieldRow,
  HeaderRow,
  ResponseRow,
} from "./endpointFormTypes";

/** 서버로 보낼 정규화된 파라미터 (in 별 분리는 요청 시 처리) */
export interface RoundTripOperation {
  summary: string | null;
  parameters: {
    name: string;
    in: "path" | "query" | "header";
    required: boolean;
    description: string | null;
    schema: { type: string };
  }[];
  requestBody: {
    content: Record<
      string,
      { schema: { type: "object"; properties: Record<string, { type: string }>; required: string[] } }
    >;
  } | null;
  responses: Record<
    string,
    { description: string | null; headers: Record<string, { required: boolean; description: string | null }> }
  >;
}

let rowSeq = 0;
/** 폼 행 React key 용 안정 로컬 id */
export function nextRowUid(prefix = "row"): string {
  rowSeq += 1;
  return `${prefix}-${rowSeq}`;
}

/** 빈 문자열 → null 정규화 */
function emptyToNull(value: string | null | undefined): string | null {
  if (value == null) return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

/** GET/DELETE 는 요청 바디를 갖지 않는다 */
export function methodAllowsBody(method: string): boolean {
  const upper = method.toUpperCase();
  return upper !== "GET" && upper !== "DELETE";
}

// ---------------------------------------------------------------------------
// 역직렬화: operationJson(문자열) → 폼 모델
// ---------------------------------------------------------------------------

/** 안전한 문자열 추출 (없으면 빈 문자열) */
function asString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

/** schema.type 추출 (없으면 "string" 폴백) */
function schemaType(schema: unknown): string {
  if (schema && typeof schema === "object") {
    const type = (schema as { type?: unknown }).type;
    if (typeof type === "string" && type.length > 0) return type;
  }
  return "string";
}

/**
 * operationJson 문자열을 파싱해 파라미터/바디/헤더/응답 폼 행으로 복원한다.
 * 파싱 실패(잘못된 JSON 등)는 null 을 반환해 호출부가 안전 폴백(빈 폼 + 경고)하도록 한다.
 *
 * @param operationJson OpenAPI Operation JSON 문자열 (null/빈 문자열이면 빈 결과 반환)
 * @param method 현재 메서드(요청 바디 활성 여부 판정용). 바디 파싱은 method 무관하게 수행(보존)
 */
export function parseOperationJson(operationJson: string | null): {
  summary: string | null;
  pathParams: ParameterRow[];
  queryParams: ParameterRow[];
  requestBodyContentType: string;
  requestBodyFields: FieldRow[];
  requestHeaders: HeaderRow[];
  responses: ResponseRow[];
} | null {
  if (operationJson == null || operationJson.trim().length === 0) {
    return {
      summary: null,
      pathParams: [],
      queryParams: [],
      requestBodyContentType: "application/json",
      requestBodyFields: [],
      requestHeaders: [],
      responses: [],
    };
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(operationJson);
  } catch {
    return null;
  }
  if (!parsed || typeof parsed !== "object") return null;

  const op = parsed as Record<string, unknown>;

  const pathParams: ParameterRow[] = [];
  const queryParams: ParameterRow[] = [];
  const requestHeaders: HeaderRow[] = [];

  // parameters[]: in 값으로 경로/쿼리/헤더 분리
  const params = op.parameters;
  if (Array.isArray(params)) {
    for (const raw of params) {
      if (!raw || typeof raw !== "object") continue;
      const p = raw as Record<string, unknown>;
      const name = asString(p.name);
      const location = asString(p.in);
      const required = p.required === true;
      const description = emptyToNull(asString(p.description));
      const type = schemaType(p.schema);
      if (location === "path") {
        pathParams.push({ _uid: nextRowUid("pp"), name, type, required, description: description ?? "" });
      } else if (location === "query") {
        queryParams.push({ _uid: nextRowUid("qp"), name, type, required, description: description ?? "" });
      } else if (location === "header") {
        requestHeaders.push({ _uid: nextRowUid("rh"), name, required, description: description ?? "" });
      }
    }
  }

  // requestBody.content[contentType].schema.properties(+required[])
  let requestBodyContentType = "application/json";
  const requestBodyFields: FieldRow[] = [];
  const requestBody = op.requestBody;
  if (requestBody && typeof requestBody === "object") {
    const content = (requestBody as Record<string, unknown>).content;
    if (content && typeof content === "object") {
      const contentEntries = Object.entries(content as Record<string, unknown>);
      if (contentEntries.length > 0) {
        const [ct, media] = contentEntries[0];
        requestBodyContentType = ct;
        if (media && typeof media === "object") {
          const schema = (media as Record<string, unknown>).schema;
          if (schema && typeof schema === "object") {
            const properties = (schema as Record<string, unknown>).properties;
            const requiredList = (schema as Record<string, unknown>).required;
            const requiredSet = new Set<string>(
              Array.isArray(requiredList) ? requiredList.filter((r): r is string => typeof r === "string") : [],
            );
            if (properties && typeof properties === "object") {
              for (const [fieldName, fieldSchema] of Object.entries(properties as Record<string, unknown>)) {
                const propDesc =
                  fieldSchema && typeof fieldSchema === "object"
                    ? emptyToNull(asString((fieldSchema as Record<string, unknown>).description))
                    : null;
                requestBodyFields.push({
                  _uid: nextRowUid("bf"),
                  name: fieldName,
                  type: schemaType(fieldSchema),
                  required: requiredSet.has(fieldName),
                  description: propDesc ?? "",
                });
              }
            }
          }
        }
      }
    }
  }

  // responses[code].description / headers[name]
  const responses: ResponseRow[] = [];
  const responsesObj = op.responses;
  if (responsesObj && typeof responsesObj === "object") {
    for (const [code, raw] of Object.entries(responsesObj as Record<string, unknown>)) {
      const description = raw && typeof raw === "object" ? emptyToNull(asString((raw as Record<string, unknown>).description)) : null;
      const headerRows: HeaderRow[] = [];
      if (raw && typeof raw === "object") {
        const headers = (raw as Record<string, unknown>).headers;
        if (headers && typeof headers === "object") {
          for (const [headerName, headerRaw] of Object.entries(headers as Record<string, unknown>)) {
            const hRequired = headerRaw && typeof headerRaw === "object" ? (headerRaw as Record<string, unknown>).required === true : false;
            const hDesc = headerRaw && typeof headerRaw === "object" ? emptyToNull(asString((headerRaw as Record<string, unknown>).description)) : null;
            headerRows.push({ _uid: nextRowUid("resph"), name: headerName, required: hRequired, description: hDesc ?? "" });
          }
        }
      }
      responses.push({ _uid: nextRowUid("resp"), statusCode: code, description: description ?? "", headers: headerRows });
    }
  }

  return {
    summary: emptyToNull(asString(op.summary)),
    pathParams,
    queryParams,
    requestBodyContentType,
    requestBodyFields,
    requestHeaders,
    responses,
  };
}

// ---------------------------------------------------------------------------
// 직렬화: 폼 모델 → RoundTripOperation (왕복 검증/디버그용) & 요청 파라미터 정규화
// ---------------------------------------------------------------------------

/**
 * 폼 상태를 OpenAPI Operation 형태(RoundTripOperation)로 직렬화한다.
 * 서버 요청 자체는 ManualEndpointRequest(구조화 필드)로 보내지만, 왕복 테스트와
 * operationJson 이해를 위해 동일 구조를 제공한다. GET/DELETE 는 requestBody 를 null 로 만든다.
 */
export function formToOperation(form: EndpointFormState): RoundTripOperation {
  const parameters: RoundTripOperation["parameters"] = [];
  for (const p of form.pathParams) {
    if (p.name.trim().length === 0) continue;
    parameters.push({
      name: p.name.trim(),
      in: "path",
      required: p.required,
      description: emptyToNull(p.description),
      schema: { type: p.type.trim() || "string" },
    });
  }
  for (const q of form.queryParams) {
    if (q.name.trim().length === 0) continue;
    parameters.push({
      name: q.name.trim(),
      in: "query",
      required: q.required,
      description: emptyToNull(q.description),
      schema: { type: q.type.trim() || "string" },
    });
  }
  for (const h of form.requestHeaders) {
    if (h.name.trim().length === 0) continue;
    parameters.push({
      name: h.name.trim(),
      in: "header",
      required: h.required,
      description: emptyToNull(h.description),
      schema: { type: "string" },
    });
  }

  let requestBody: RoundTripOperation["requestBody"] = null;
  if (methodAllowsBody(form.method)) {
    const properties: Record<string, { type: string }> = {};
    const required: string[] = [];
    for (const f of form.requestBodyFields) {
      const name = f.name.trim();
      if (name.length === 0) continue;
      properties[name] = { type: f.type.trim() || "string" };
      if (f.required) required.push(name);
    }
    if (Object.keys(properties).length > 0) {
      requestBody = {
        content: {
          [form.requestBodyContentType.trim() || "application/json"]: {
            schema: { type: "object", properties, required },
          },
        },
      };
    }
  }

  const responses: RoundTripOperation["responses"] = {};
  for (const r of form.responses) {
    const code = r.statusCode.trim();
    if (code.length === 0) continue;
    const headers: Record<string, { required: boolean; description: string | null }> = {};
    for (const h of r.headers) {
      const name = h.name.trim();
      if (name.length === 0) continue;
      headers[name] = { required: h.required, description: emptyToNull(h.description) };
    }
    responses[code] = { description: emptyToNull(r.description), headers };
  }

  return {
    summary: emptyToNull(form.summary),
    parameters,
    requestBody,
    responses,
  };
}
