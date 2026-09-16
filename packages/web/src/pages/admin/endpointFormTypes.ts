// 관리자 API 편집(Case 9) 폼 로컬 모델 타입.
//
// 서버 계약 타입(ParameterInput/FieldInput/HeaderInput/ResponseInput, api/types.ts)과 별개로,
// 화면 편집을 위해 React key 용 안정 로컬 id(_uid)와 문자열 입력값(빈 문자열 허용)을 갖는 폼 행 모델을 둔다.
// 저장 시 formToRequest 로 서버 계약 형태로 정규화한다.

/** 파라미터 행 (경로/쿼리 공용). description 은 화면상 빈 문자열 허용 */
export interface ParameterRow {
  _uid: string;
  name: string;
  type: string;
  required: boolean;
  description: string;
}

/** 요청/응답 바디 필드 행 */
export interface FieldRow {
  _uid: string;
  name: string;
  type: string;
  required: boolean;
  description: string;
}

/** 요청/응답 헤더 행 */
export interface HeaderRow {
  _uid: string;
  name: string;
  required: boolean;
  description: string;
}

/** 응답 정의 행 (상태코드 + 설명 + 응답 헤더 목록) */
export interface ResponseRow {
  _uid: string;
  statusCode: string;
  description: string;
  headers: HeaderRow[];
}

/** API 편집 폼 전체 상태 (직렬화 가능 — dirty 스냅샷 대상. _uid 는 스냅샷에서 제외) */
export interface EndpointFormState {
  method: string;
  path: string;
  summary: string;
  confirmRequired: boolean;
  confirmMessage: string;
  excluded: boolean;
  pathParams: ParameterRow[];
  queryParams: ParameterRow[];
  requestBodyContentType: string;
  requestBodyFields: FieldRow[];
  requestHeaders: HeaderRow[];
  responses: ResponseRow[];
}
