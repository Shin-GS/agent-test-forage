// 관리자 — API 편집 페이지 (라우트 "/admin/specs/:id/endpoints/new" 등록,
// "/admin/specs/:id/endpoints/:endpointId/edit" 수정, 관리자 전용).
// 디자인 명세: docs/design/web/admin.html Case 9, admin.cases.md "API 편집 페이지 (Case 9)".
// 기획: docs/specs/pages/admin.md, docs/specs/spec/registration.md#관리자-수동-등록.
//
// - 라이브러리를 못 붙이는 외부 서버의 API 를 관리자가 직접 등록/편집한다.
// - 등록/수정 겸용: :endpointId 유무로 모드를 판별한다.
//   · 등록 모드: 빈 폼. 항상 source=MANUAL 로 생성. (method,path) 중복이면 서버 400 → 인라인 에러.
//   · 수정 모드: getEndpoint 로 상세 로드 후 operationJson 을 파싱해 폼 복원(역직렬화, endpointOperation.ts).
//     파싱 실패 시 안전 폴백(메타만 채운 빈 스키마 폼 + 경고 토스트).
// - 폼 구성(Case 9): ①기본정보 ②경로 파라미터 ③쿼리 파라미터 ④요청 바디(펼침) / ⑤요청 헤더 ⑥응답(접힘 accordion).
// - 경로에 {변수} 가 있으면 ② 경로 파라미터에 없는 변수를 자동 행으로 제안(추가).
// - GET/DELETE 선택 시 ④ 요청 바디 숨김. 입력값은 상태로 보존하되 저장 payload 에는 미포함(다시 POST 로 바꾸면 복원).
// - LIBRARY/DEPRECATED 는 스키마 섹션 읽기 전용(disabled), 메타(실행전확인/AI제외/confirmMessage)만 활성.
// - 검증 에러가 있는 접힘 accordion(⑤⑥)은 자동 펼침.
// - 저장 성공 → 스펙 상세(/admin/specs/{id})로 복귀 + 토스트. 저장중 버튼 disabled + 스피너. 네트워크/5xx 토스트.
// - 저장 안 한 변경 이탈 가드: AdminSpecFormPage 와 동일 패턴(dirty 스냅샷 + beforeunload + window.confirm).
//
// 데이터: POST /specs/{id}/endpoints (등록), PATCH .../{endpointId} (수정), GET .../{endpointId} (수정 초기 로드).

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, specsApi } from "../../api";
import type {
  EndpointDetail,
  HeaderInput,
  ManualEndpointRequest,
  ParameterInput,
  ResponseInput,
} from "../../api/types";
import { PageShell } from "../../components/layout/PageShell";
import { PageActionBar } from "../../components/layout/PageActionBar";
import { useToastStore } from "../../store/toastStore";
import type {
  EndpointFormState,
  FieldRow,
  HeaderRow,
  ParameterRow,
  ResponseRow,
} from "./endpointFormTypes";
import { methodAllowsBody, nextRowUid, parseOperationJson } from "./endpointOperation";

const METHODS = ["GET", "POST", "PUT", "PATCH", "DELETE"] as const;

function emptyForm(): EndpointFormState {
  return {
    method: "POST",
    path: "",
    summary: "",
    confirmRequired: false,
    confirmMessage: "",
    excluded: false,
    pathParams: [],
    queryParams: [],
    requestBodyContentType: "application/json",
    requestBodyFields: [],
    requestHeaders: [],
    responses: [],
  };
}

/** 경로 문자열에서 {변수} 목록 추출 (중복 제거, 등장 순서 유지) */
function extractPathVars(path: string): string[] {
  const matches = path.match(/\{([^}]+)\}/g) ?? [];
  const seen = new Set<string>();
  const result: string[] = [];
  for (const m of matches) {
    const name = m.slice(1, -1).trim();
    if (name.length > 0 && !seen.has(name)) {
      seen.add(name);
      result.push(name);
    }
  }
  return result;
}

/** EndpointDetail → 폼 상태 (수정 모드 초기화). operationJson 파싱 결과를 병합. 파싱 실패면 null 반환 */
function detailToForm(detail: EndpointDetail): EndpointFormState | null {
  const parsed = parseOperationJson(detail.operationJson);
  if (parsed === null) return null;
  return {
    method: detail.method || "GET",
    path: detail.path ?? "",
    summary: detail.summary ?? parsed.summary ?? "",
    confirmRequired: detail.confirmRequired,
    confirmMessage: detail.confirmMessage ?? "",
    excluded: detail.excluded,
    pathParams: parsed.pathParams,
    queryParams: parsed.queryParams,
    requestBodyContentType: parsed.requestBodyContentType,
    requestBodyFields: parsed.requestBodyFields,
    requestHeaders: parsed.requestHeaders,
    responses: parsed.responses,
  };
}

/** 메타만 채운 폴백 폼 (operationJson 파싱 실패 시) */
function detailToFallbackForm(detail: EndpointDetail): EndpointFormState {
  return {
    ...emptyForm(),
    method: detail.method || "GET",
    path: detail.path ?? "",
    summary: detail.summary ?? "",
    confirmRequired: detail.confirmRequired,
    confirmMessage: detail.confirmMessage ?? "",
    excluded: detail.excluded,
  };
}

/** dirty 비교용 스냅샷 (직렬화 가능한 폼 내용, _uid 제외) */
function formSnapshot(form: EndpointFormState): string {
  return JSON.stringify({
    method: form.method,
    path: form.path,
    summary: form.summary,
    confirmRequired: form.confirmRequired,
    confirmMessage: form.confirmMessage,
    excluded: form.excluded,
    pathParams: form.pathParams.map((p) => ({ name: p.name, type: p.type, required: p.required, description: p.description })),
    queryParams: form.queryParams.map((p) => ({ name: p.name, type: p.type, required: p.required, description: p.description })),
    requestBodyContentType: form.requestBodyContentType,
    requestBodyFields: form.requestBodyFields.map((f) => ({ name: f.name, type: f.type, required: f.required, description: f.description })),
    requestHeaders: form.requestHeaders.map((h) => ({ name: h.name, required: h.required, description: h.description })),
    responses: form.responses.map((r) => ({
      statusCode: r.statusCode,
      description: r.description,
      headers: r.headers.map((h) => ({ name: h.name, required: h.required, description: h.description })),
    })),
  });
}

/** 빈 문자열 → null */
function emptyToNull(value: string): string | null {
  const t = value.trim();
  return t.length > 0 ? t : null;
}

/** 폼 → 서버 요청. GET/DELETE 면 requestBody 미포함. 빈 name 행은 제외 */
function formToRequest(form: EndpointFormState): ManualEndpointRequest {
  const parameters: ParameterInput[] = [];
  for (const p of form.pathParams) {
    if (p.name.trim().length === 0) continue;
    parameters.push({ name: p.name.trim(), in: "path", type: p.type.trim() || "string", required: p.required, description: emptyToNull(p.description) });
  }
  for (const q of form.queryParams) {
    if (q.name.trim().length === 0) continue;
    parameters.push({ name: q.name.trim(), in: "query", type: q.type.trim() || "string", required: q.required, description: emptyToNull(q.description) });
  }

  let requestBody: ManualEndpointRequest["requestBody"] = null;
  if (methodAllowsBody(form.method)) {
    const fields = form.requestBodyFields
      .filter((f) => f.name.trim().length > 0)
      .map((f) => ({ name: f.name.trim(), type: f.type.trim() || "string", required: f.required, description: emptyToNull(f.description) }));
    if (fields.length > 0) {
      requestBody = { contentType: form.requestBodyContentType.trim() || "application/json", fields };
    }
  }

  const headers: HeaderInput[] = form.requestHeaders
    .filter((h) => h.name.trim().length > 0)
    .map((h) => ({ name: h.name.trim(), required: h.required, description: emptyToNull(h.description) }));

  const responses: ResponseInput[] = form.responses
    .filter((r) => r.statusCode.trim().length > 0)
    .map((r) => ({
      statusCode: r.statusCode.trim(),
      description: emptyToNull(r.description),
      headers: r.headers
        .filter((h) => h.name.trim().length > 0)
        .map((h) => ({ name: h.name.trim(), required: h.required, description: emptyToNull(h.description) })),
    }));

  return {
    method: form.method,
    path: form.path.trim(),
    summary: emptyToNull(form.summary),
    parameters,
    requestBody,
    headers,
    responses,
    excluded: form.excluded,
    confirmRequired: form.confirmRequired,
    confirmMessage: form.confirmRequired ? emptyToNull(form.confirmMessage) : null,
  };
}

interface FieldErrors {
  method?: string;
  path?: string;
}

export function AdminEndpointFormPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const showToast = useToastStore((s) => s.show);

  const { id, endpointId } = useParams<{ id?: string; endpointId?: string }>();
  const specId = id ? Number(id) : null;
  const endpointIdNum = endpointId ? Number(endpointId) : null;
  const isEdit = endpointIdNum != null;
  const specValid = specId != null && Number.isFinite(specId);

  const [form, setForm] = useState<EndpointFormState>(emptyForm());
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<string | null>(null);
  // 출처(로드된 엔드포인트 기준). MANUAL 만 스키마 편집 가능. 등록 모드는 항상 MANUAL.
  const [sourceCode, setSourceCode] = useState<string>("MANUAL");
  const [deprecated, setDeprecated] = useState(false);
  // 검증 에러로 accordion(⑤⑥) 강제 펼침
  const [forceOpenHeaders, setForceOpenHeaders] = useState(false);
  const [forceOpenResponses, setForceOpenResponses] = useState(false);

  const initialSnapshotRef = useRef<string>(formSnapshot(emptyForm()));
  const [saved, setSaved] = useState(false);
  const isDirty = !saved && formSnapshot(form) !== initialSnapshotRef.current;

  // 스키마 편집 가능 여부: 등록 모드이거나 MANUAL(비-DEPRECATED). LIBRARY/DEPRECATED 는 메타만.
  const schemaEditable = !isEdit || (sourceCode === "MANUAL" && !deprecated);

  // 수정 모드: 엔드포인트 상세 로드
  const {
    data: detail,
    isLoading: isDetailLoading,
    isError: isDetailError,
    error: detailError,
  } = useQuery({
    queryKey: ["admin-endpoint", specId, endpointIdNum],
    queryFn: () => specsApi.getEndpoint(specId as number, endpointIdNum as number),
    enabled: isEdit && specValid && Number.isFinite(endpointIdNum),
    retry: (count, err) => !(err instanceof ApiError && err.status === 404) && count < 2,
  });

  const applyDetail = useCallback(
    (loaded: EndpointDetail) => {
      setSourceCode(loaded.source?.code ?? "MANUAL");
      setDeprecated(loaded.status?.code === "DEPRECATED");
      const next = detailToForm(loaded);
      if (next === null) {
        // operationJson 파싱 실패 → 메타만 복원 + 경고
        const fallback = detailToFallbackForm(loaded);
        setForm(fallback);
        initialSnapshotRef.current = formSnapshot(fallback);
        showToast("API 스키마를 불러오지 못해 메타 정보만 복원했습니다", "warning");
      } else {
        setForm(next);
        initialSnapshotRef.current = formSnapshot(next);
      }
    },
    [showToast],
  );

  useEffect(() => {
    if (detail) applyDetail(detail);
  }, [detail, applyDetail]);

  // 브라우저 새로고침/닫기 시 dirty 경고
  useEffect(() => {
    if (!isDirty) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [isDirty]);

  const backToSpec = useCallback(() => {
    if (specValid) {
      navigate(`/admin/specs/${specId}`);
    } else {
      navigate("/admin/specs");
    }
  }, [navigate, specId, specValid]);

  // [← 스펙으로]/[취소] 시 dirty 확인 후 이동
  const navigateAway = useCallback(() => {
    if (isDirty && !window.confirm("저장하지 않은 변경사항이 있습니다. 스펙으로 이동할까요?")) {
      return;
    }
    backToSpec();
  }, [isDirty, backToSpec]);

  function patchForm(patch: Partial<EndpointFormState>) {
    setForm((prev) => ({ ...prev, ...patch }));
  }

  // ── 경로 파라미터 자동 제안: path 의 {변수} 중 ② 테이블에 없는 이름을 추가 ──
  const pathVars = useMemo(() => extractPathVars(form.path), [form.path]);
  const missingPathVars = useMemo(
    () => pathVars.filter((v) => !form.pathParams.some((p) => p.name.trim() === v)),
    [pathVars, form.pathParams],
  );

  function suggestPathParams() {
    if (missingPathVars.length === 0) return;
    const added: ParameterRow[] = missingPathVars.map((name) => ({
      _uid: nextRowUid("pp"),
      name,
      type: "string",
      required: true,
      description: "",
    }));
    patchForm({ pathParams: [...form.pathParams, ...added] });
  }

  // ── 행 편집 헬퍼 (경로/쿼리 파라미터) ──
  function addParam(kind: "pathParams" | "queryParams") {
    const row: ParameterRow = { _uid: nextRowUid(kind === "pathParams" ? "pp" : "qp"), name: "", type: kind === "pathParams" ? "string" : "", required: false, description: "" };
    patchForm({ [kind]: [...form[kind], row] } as Partial<EndpointFormState>);
  }
  function updateParam(kind: "pathParams" | "queryParams", uid: string, patch: Partial<ParameterRow>) {
    patchForm({ [kind]: form[kind].map((p) => (p._uid === uid ? { ...p, ...patch } : p)) } as Partial<EndpointFormState>);
  }
  function removeParam(kind: "pathParams" | "queryParams", uid: string) {
    patchForm({ [kind]: form[kind].filter((p) => p._uid !== uid) } as Partial<EndpointFormState>);
  }

  // ── 바디 필드 ──
  function addBodyField() {
    const row: FieldRow = { _uid: nextRowUid("bf"), name: "", type: "", required: false, description: "" };
    patchForm({ requestBodyFields: [...form.requestBodyFields, row] });
  }
  function updateBodyField(uid: string, patch: Partial<FieldRow>) {
    patchForm({ requestBodyFields: form.requestBodyFields.map((f) => (f._uid === uid ? { ...f, ...patch } : f)) });
  }
  function removeBodyField(uid: string) {
    patchForm({ requestBodyFields: form.requestBodyFields.filter((f) => f._uid !== uid) });
  }

  // ── 요청 헤더 ──
  function addRequestHeader() {
    const row: HeaderRow = { _uid: nextRowUid("rh"), name: "", required: false, description: "" };
    patchForm({ requestHeaders: [...form.requestHeaders, row] });
  }
  function updateRequestHeader(uid: string, patch: Partial<HeaderRow>) {
    patchForm({ requestHeaders: form.requestHeaders.map((h) => (h._uid === uid ? { ...h, ...patch } : h)) });
  }
  function removeRequestHeader(uid: string) {
    patchForm({ requestHeaders: form.requestHeaders.filter((h) => h._uid !== uid) });
  }

  // ── 응답 ──
  function addResponse() {
    const row: ResponseRow = { _uid: nextRowUid("resp"), statusCode: "", description: "", headers: [] };
    patchForm({ responses: [...form.responses, row] });
  }
  function updateResponse(uid: string, patch: Partial<ResponseRow>) {
    patchForm({ responses: form.responses.map((r) => (r._uid === uid ? { ...r, ...patch } : r)) });
  }
  function removeResponse(uid: string) {
    patchForm({ responses: form.responses.filter((r) => r._uid !== uid) });
  }
  function addResponseHeader(respUid: string) {
    patchForm({
      responses: form.responses.map((r) =>
        r._uid === respUid ? { ...r, headers: [...r.headers, { _uid: nextRowUid("resph"), name: "", required: false, description: "" }] } : r,
      ),
    });
  }
  function updateResponseHeader(respUid: string, headerUid: string, patch: Partial<HeaderRow>) {
    patchForm({
      responses: form.responses.map((r) =>
        r._uid === respUid ? { ...r, headers: r.headers.map((h) => (h._uid === headerUid ? { ...h, ...patch } : h)) } : r,
      ),
    });
  }
  function removeResponseHeader(respUid: string, headerUid: string) {
    patchForm({
      responses: form.responses.map((r) =>
        r._uid === respUid ? { ...r, headers: r.headers.filter((h) => h._uid !== headerUid) } : r,
      ),
    });
  }

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (form.method.trim().length === 0) errors.method = "메서드를 선택하세요.";
    if (form.path.trim().length === 0) {
      errors.path = "경로를 입력하세요.";
    } else if (!form.path.trim().startsWith("/")) {
      errors.path = "경로는 /로 시작해야 합니다.";
    }
    return errors;
  }

  const saveMutation = useMutation({
    // 생성은 { id }, 수정은 204(void). 반환값을 쓰지 않으므로(저장 후 스펙 상세로 복귀) 공용 타입만 맞춘다.
    mutationFn: async (body: ManualEndpointRequest): Promise<void> => {
      if (isEdit) {
        await specsApi.updateEndpoint(specId as number, endpointIdNum as number, body);
      } else {
        await specsApi.createEndpoint(specId as number, body);
      }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-spec", specId] });
      void queryClient.invalidateQueries({ queryKey: ["specs"] });
      if (isEdit) {
        void queryClient.invalidateQueries({ queryKey: ["admin-endpoint", specId, endpointIdNum] });
      }
      setSaved(true);
      showToast(isEdit ? "API를 저장했습니다" : "API를 등록했습니다", "success");
      backToSpec();
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        // (method,path) 중복 등은 path 인라인 에러로도 노출
        setServerError(err.message);
        if (err.status === 400) {
          setFieldErrors((prev) => ({ ...prev, path: err.message }));
        }
      } else {
        setServerError(err instanceof Error ? err.message : "저장에 실패했습니다");
      }
    },
  });

  function handleSave() {
    setServerError(null);
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;
    saveMutation.mutate(formToRequest(form));
  }

  const pageTitle = isEdit ? "API 편집" : "API 추가";
  const notFound = isEdit && isDetailError && detailError instanceof ApiError && detailError.status === 404;
  const bodyVisible = methodAllowsBody(form.method);

  // 잘못된 specId (딥링크 방어)
  if (!specValid) {
    return (
      <PageShell title={pageTitle}>
        <div className="page-body">
          <div className="empty-state">
            <div className="empty-state__icon">🗑️</div>
            <div className="empty-state__title">잘못된 경로입니다</div>
            <div className="empty-state__desc">스펙 정보를 찾을 수 없습니다.</div>
            <button type="button" className="btn btn--primary" onClick={() => navigate("/admin/specs")}>
              목록으로
            </button>
          </div>
        </div>
      </PageShell>
    );
  }

  // 수정 모드 로딩
  if (isEdit && isDetailLoading) {
    return (
      <PageShell title={pageTitle}>
        <div className="page-body">
          <div className="recipe-state" role="status" aria-live="polite">
            API 정보를 불러오는 중입니다…
          </div>
        </div>
      </PageShell>
    );
  }

  // 수정 모드 대상 없음
  if (notFound) {
    return (
      <PageShell title={pageTitle}>
        <div className="page-body">
          <div className="empty-state">
            <div className="empty-state__icon">🗑️</div>
            <div className="empty-state__title">존재하지 않는 API입니다</div>
            <div className="empty-state__desc">삭제되었거나 잘못된 경로일 수 있습니다.</div>
            <button type="button" className="btn btn--primary" onClick={backToSpec}>
              스펙으로
            </button>
          </div>
        </div>
      </PageShell>
    );
  }

  // 수정 모드 그 외 로드 에러
  if (isEdit && isDetailError) {
    return (
      <PageShell title={pageTitle}>
        <div className="page-body">
          <div className="recipe-state recipe-state--error" role="alert">
            <div>
              API 정보를 불러오지 못했습니다
              {detailError instanceof Error ? `: ${detailError.message}` : ""}
            </div>
            <button type="button" className="btn btn--secondary btn--sm" onClick={backToSpec}>
              스펙으로
            </button>
          </div>
        </div>
      </PageShell>
    );
  }

  const schemaDisabled = !schemaEditable;

  return (
    <PageShell
      title={pageTitle}
      subject={isEdit ? `${form.method} ${form.path}` : undefined}
      actionBar={
        <PageActionBar
          onBack={navigateAway}
          backLabel="스펙"
          title={pageTitle}
          actions={
            <>
              <button
                type="button"
                className="btn btn--secondary btn--sm"
                disabled={saveMutation.isPending}
                onClick={navigateAway}
              >
                취소
              </button>
              <button
                type="button"
                className="btn btn--primary btn--sm"
                disabled={saveMutation.isPending}
                onClick={handleSave}
              >
                {saveMutation.isPending ? (isEdit ? "저장 중…" : "등록 중…") : isEdit ? "저장" : "등록"}
              </button>
            </>
          }
        />
      }
    >
      <div className="page-body">
        {/* 읽기 전용 안내 (LIBRARY/DEPRECATED) */}
        {isEdit && !schemaEditable && (
          <div className="alert alert--info" role="status">
            <span>🔒</span>
            <span>
              {deprecated
                ? "지원 종료된 API입니다. 스키마는 수정할 수 없고 메타(실행 전 확인 / AI 제외)만 수정할 수 있습니다."
                : "스키마는 라이브러리가 관리합니다. 메타(실행 전 확인 / AI 제외)만 수정할 수 있습니다."}
            </span>
          </div>
        )}

        {/* 서버 에러 요약 */}
        {serverError && (
          <div className="alert alert--error" role="alert">
            ✕ 저장할 수 없습니다 — {serverError}
          </div>
        )}

        <form
          className="form-page"
          onSubmit={(e) => {
            e.preventDefault();
            handleSave();
          }}
        >
          {/* ① 기본 정보 */}
          <div className="form-section">
            <div className="form-section__title">① 기본 정보</div>
            <div className="form-row">
              <div className="form-group" style={{ flex: "0 0 160px", minWidth: 140 }}>
                <label className="form-label" htmlFor="ep-method">
                  메서드
                  <span className="required-mark" aria-hidden="true">*</span>
                </label>
                <select
                  className="input"
                  id="ep-method"
                  disabled={schemaDisabled}
                  value={form.method}
                  onChange={(e) => patchForm({ method: e.target.value })}
                >
                  {METHODS.map((m) => (
                    <option key={m} value={m}>
                      {m}
                    </option>
                  ))}
                </select>
                {fieldErrors.method && (
                  <span className="form-error" role="alert">
                    {fieldErrors.method}
                  </span>
                )}
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="ep-path">
                  경로
                  <span className="required-mark" aria-hidden="true">*</span>
                </label>
                <input
                  className={`input${fieldErrors.path ? " input--error" : ""}`}
                  type="text"
                  id="ep-path"
                  autoComplete="off"
                  placeholder="예: /orders 또는 /users/{id}"
                  readOnly={schemaDisabled}
                  aria-invalid={fieldErrors.path ? true : undefined}
                  value={form.path}
                  onChange={(e) => patchForm({ path: e.target.value })}
                />
                {fieldErrors.path && (
                  <span className="form-error" role="alert">
                    {fieldErrors.path}
                  </span>
                )}
              </div>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="ep-summary">
                설명
              </label>
              <input
                className="input"
                type="text"
                id="ep-summary"
                autoComplete="off"
                placeholder="이 API가 하는 일"
                readOnly={schemaDisabled}
                value={form.summary}
                onChange={(e) => patchForm({ summary: e.target.value })}
              />
            </div>

            {/* 출처 배지 */}
            <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
              <span style={{ fontSize: "var(--font-size-sm)", color: "var(--color-text-tertiary)" }}>출처</span>
              {deprecated ? (
                <span className="badge badge--warning" style={{ fontSize: "var(--font-size-2xs)" }}>
                  DEPRECATED
                </span>
              ) : sourceCode === "LIBRARY" ? (
                <span className="api-source api-source--library">LIBRARY</span>
              ) : (
                <span className="api-source api-source--manual">🖉 수동</span>
              )}
            </div>

            {/* 메타: 실행 전 확인 + confirmMessage (읽기전용 상태에서도 활성) */}
            <label className="check-option" htmlFor="ep-confirm">
              <input
                type="checkbox"
                id="ep-confirm"
                checked={form.confirmRequired}
                onChange={(e) => patchForm({ confirmRequired: e.target.checked })}
              />
              실행 전 확인 (isConfirmRequired)
            </label>
            {form.confirmRequired && (
              <div className="form-group">
                <label className="form-label" htmlFor="ep-confirm-msg">
                  확인 메시지 (confirmMessage)
                </label>
                <input
                  className="input"
                  type="text"
                  id="ep-confirm-msg"
                  autoComplete="off"
                  placeholder="예: 실 결제가 발생합니다. 계속할까요?"
                  value={form.confirmMessage}
                  onChange={(e) => patchForm({ confirmMessage: e.target.value })}
                />
              </div>
            )}
            <label className="check-option" htmlFor="ep-excluded">
              <input
                type="checkbox"
                id="ep-excluded"
                checked={form.excluded}
                onChange={(e) => patchForm({ excluded: e.target.checked })}
              />
              AI 목록에서 제외 (isExcluded)
            </label>
          </div>

          {/* ② 경로 파라미터 */}
          <div className="form-section">
            <div className="form-section__title">② 경로 파라미터</div>
            {form.pathParams.length > 0 && (
              <ParamTable
                rows={form.pathParams}
                disabled={schemaDisabled}
                namePlaceholder="예: id"
                onUpdate={(uid, patch) => updateParam("pathParams", uid, patch)}
                onRemove={(uid) => removeParam("pathParams", uid)}
              />
            )}
            {!schemaDisabled && (
              <div style={{ display: "flex", gap: "var(--space-2)", alignItems: "center", flexWrap: "wrap" }}>
                <button type="button" className="btn btn--secondary btn--sm repeat-table__add" onClick={() => addParam("pathParams")}>
                  + 파라미터 추가
                </button>
                {missingPathVars.length > 0 && (
                  <button type="button" className="btn btn--ghost btn--sm" onClick={suggestPathParams}>
                    경로 변수 자동 추가 ({missingPathVars.join(", ")})
                  </button>
                )}
              </div>
            )}
            <span className="form-hint">
              경로에 <code>{"{변수}"}</code>가 있으면 해당 행을 자동으로 제안합니다.
            </span>
          </div>

          {/* ③ 쿼리 파라미터 */}
          <div className="form-section">
            <div className="form-section__title">③ 쿼리 파라미터</div>
            {form.queryParams.length > 0 && (
              <ParamTable
                rows={form.queryParams}
                disabled={schemaDisabled}
                namePlaceholder="예: page"
                typePlaceholder="integer"
                onUpdate={(uid, patch) => updateParam("queryParams", uid, patch)}
                onRemove={(uid) => removeParam("queryParams", uid)}
              />
            )}
            {!schemaDisabled && (
              <button type="button" className="btn btn--secondary btn--sm repeat-table__add" onClick={() => addParam("queryParams")}>
                + 파라미터 추가
              </button>
            )}
          </div>

          {/* ④ 요청 바디 (GET/DELETE 면 숨김. 입력값은 상태로 보존) */}
          {bodyVisible ? (
            <div className="form-section" id="ep-body-section">
              <div className="form-section__title">④ 요청 바디</div>
              <div className="form-group" style={{ maxWidth: 320 }}>
                <label className="form-label" htmlFor="ep-content-type">
                  Content-Type
                </label>
                <input
                  className="input"
                  type="text"
                  id="ep-content-type"
                  readOnly={schemaDisabled}
                  value={form.requestBodyContentType}
                  onChange={(e) => patchForm({ requestBodyContentType: e.target.value })}
                />
              </div>
              {form.requestBodyFields.length > 0 && (
                <FieldTable
                  rows={form.requestBodyFields}
                  disabled={schemaDisabled}
                  onUpdate={updateBodyField}
                  onRemove={removeBodyField}
                />
              )}
              {!schemaDisabled && (
                <button type="button" className="btn btn--secondary btn--sm repeat-table__add" onClick={addBodyField}>
                  + 필드 추가
                </button>
              )}
            </div>
          ) : (
            <div className="form-section">
              <div className="form-section__title">④ 요청 바디</div>
              <span className="form-hint">
                {form.method}은(는) 요청 바디를 사용하지 않아 숨겨집니다. 입력한 바디 필드는 보존되며, 다시 POST/PUT/PATCH로 바꾸면 나타납니다.
              </span>
            </div>
          )}

          {/* ⑤ 요청 헤더 (접힘 accordion, 검증 에러 시 자동 펼침) */}
          <details className="accordion" open={forceOpenHeaders}>
            <summary
              className="accordion__summary"
              aria-controls="ep-headers-body"
              onClick={() => setForceOpenHeaders((v) => !v)}
            >
              ⑤ 요청 헤더
            </summary>
            <div className="accordion__body" id="ep-headers-body">
              <div className="alert alert--info" style={{ marginBottom: "var(--space-3)" }}>
                <span>🔒</span>
                <span>정의만 저장됩니다. 실제 요청 주입은 추후 지원됩니다.</span>
              </div>
              {form.requestHeaders.length > 0 && (
                <HeaderTable
                  rows={form.requestHeaders}
                  disabled={schemaDisabled}
                  onUpdate={updateRequestHeader}
                  onRemove={removeRequestHeader}
                />
              )}
              {!schemaDisabled && (
                <button type="button" className="btn btn--secondary btn--sm repeat-table__add" onClick={addRequestHeader}>
                  + 헤더 추가
                </button>
              )}
            </div>
          </details>

          {/* ⑥ 응답 (접힘 accordion) */}
          <details className="accordion" open={forceOpenResponses}>
            <summary
              className="accordion__summary"
              aria-controls="ep-response-body"
              onClick={() => setForceOpenResponses((v) => !v)}
            >
              ⑥ 응답
            </summary>
            <div className="accordion__body" id="ep-response-body">
              {form.responses.map((resp) => (
                <div key={resp._uid} className="card" style={{ padding: "var(--space-3)", marginBottom: "var(--space-2)", display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
                  <div className="form-row">
                    <div className="form-group" style={{ flex: "0 0 140px", minWidth: 120 }}>
                      <label className="form-label" htmlFor={`ep-resp-code-${resp._uid}`}>
                        상태 코드
                      </label>
                      <input
                        className="input"
                        type="text"
                        id={`ep-resp-code-${resp._uid}`}
                        placeholder="예: 200"
                        readOnly={schemaDisabled}
                        value={resp.statusCode}
                        onChange={(e) => updateResponse(resp._uid, { statusCode: e.target.value })}
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label" htmlFor={`ep-resp-desc-${resp._uid}`}>
                        설명
                      </label>
                      <input
                        className="input"
                        type="text"
                        id={`ep-resp-desc-${resp._uid}`}
                        placeholder="예: 성공"
                        readOnly={schemaDisabled}
                        value={resp.description}
                        onChange={(e) => updateResponse(resp._uid, { description: e.target.value })}
                      />
                    </div>
                    {!schemaDisabled && (
                      <div style={{ display: "flex", alignItems: "flex-end" }}>
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm"
                          aria-label="이 응답 정의 삭제"
                          title="삭제"
                          style={{ color: "var(--color-error)" }}
                          onClick={() => removeResponse(resp._uid)}
                        >
                          🗑
                        </button>
                      </div>
                    )}
                  </div>
                  <div className="form-section__title">응답 헤더</div>
                  {resp.headers.length > 0 && (
                    <HeaderTable
                      rows={resp.headers}
                      disabled={schemaDisabled}
                      onUpdate={(uid, patch) => updateResponseHeader(resp._uid, uid, patch)}
                      onRemove={(uid) => removeResponseHeader(resp._uid, uid)}
                    />
                  )}
                  {!schemaDisabled && (
                    <button type="button" className="btn btn--secondary btn--sm repeat-table__add" onClick={() => addResponseHeader(resp._uid)}>
                      + 응답 헤더 추가
                    </button>
                  )}
                </div>
              ))}
              {!schemaDisabled && (
                <button type="button" className="btn btn--secondary btn--sm repeat-table__add" onClick={addResponse}>
                  + 응답 추가
                </button>
              )}
            </div>
          </details>

          {isEdit && sourceCode === "MANUAL" && !deprecated && (
            <p style={{ fontSize: "var(--font-size-2xs)", color: "var(--color-text-tertiary)" }}>
              수동(MANUAL) API입니다. 라이브러리가 같은 method+path로 등록하면 자동 등록으로 갱신될 수 있습니다.
            </p>
          )}
        </form>
      </div>
    </PageShell>
  );
}

// ---------------------------------------------------------------------------
// 하위 테이블 컴포넌트 (repeat-table 톤)
// ---------------------------------------------------------------------------

interface ParamTableProps {
  rows: ParameterRow[];
  disabled: boolean;
  namePlaceholder?: string;
  typePlaceholder?: string;
  onUpdate: (uid: string, patch: Partial<ParameterRow>) => void;
  onRemove: (uid: string) => void;
}

function ParamTable({ rows, disabled, namePlaceholder, typePlaceholder, onUpdate, onRemove }: ParamTableProps) {
  return (
    <table className="repeat-table">
      <thead>
        <tr>
          <th scope="col">이름</th>
          <th scope="col">타입</th>
          <th scope="col">필수</th>
          <th scope="col">설명</th>
          <th scope="col" className="repeat-table__actions">
            <span className="sr-only">삭제</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row._uid}>
            <td>
              <input className="input" type="text" aria-label="파라미터 이름" placeholder={namePlaceholder} readOnly={disabled} value={row.name} onChange={(e) => onUpdate(row._uid, { name: e.target.value })} />
            </td>
            <td>
              <input className="input" type="text" aria-label="파라미터 타입" placeholder={typePlaceholder ?? "string"} readOnly={disabled} value={row.type} onChange={(e) => onUpdate(row._uid, { type: e.target.value })} />
            </td>
            <td style={{ textAlign: "center" }}>
              <input type="checkbox" aria-label="파라미터 필수" disabled={disabled} checked={row.required} onChange={(e) => onUpdate(row._uid, { required: e.target.checked })} />
            </td>
            <td>
              <input className="input" type="text" aria-label="파라미터 설명" placeholder="설명" readOnly={disabled} value={row.description} onChange={(e) => onUpdate(row._uid, { description: e.target.value })} />
            </td>
            <td className="repeat-table__actions">
              {!disabled && (
                <button type="button" className="btn btn--ghost btn--sm" aria-label="이 파라미터 삭제" title="삭제" style={{ color: "var(--color-error)" }} onClick={() => onRemove(row._uid)}>
                  🗑
                </button>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

interface FieldTableProps {
  rows: FieldRow[];
  disabled: boolean;
  onUpdate: (uid: string, patch: Partial<FieldRow>) => void;
  onRemove: (uid: string) => void;
}

function FieldTable({ rows, disabled, onUpdate, onRemove }: FieldTableProps) {
  return (
    <table className="repeat-table">
      <thead>
        <tr>
          <th scope="col">필드</th>
          <th scope="col">타입</th>
          <th scope="col">필수</th>
          <th scope="col">설명</th>
          <th scope="col" className="repeat-table__actions">
            <span className="sr-only">삭제</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row._uid}>
            <td>
              <input className="input" type="text" aria-label="바디 필드 이름" placeholder="예: email" readOnly={disabled} value={row.name} onChange={(e) => onUpdate(row._uid, { name: e.target.value })} />
            </td>
            <td>
              <input className="input" type="text" aria-label="바디 필드 타입" placeholder="string" readOnly={disabled} value={row.type} onChange={(e) => onUpdate(row._uid, { type: e.target.value })} />
            </td>
            <td style={{ textAlign: "center" }}>
              <input type="checkbox" aria-label="바디 필드 필수" disabled={disabled} checked={row.required} onChange={(e) => onUpdate(row._uid, { required: e.target.checked })} />
            </td>
            <td>
              <input className="input" type="text" aria-label="바디 필드 설명" placeholder="설명" readOnly={disabled} value={row.description} onChange={(e) => onUpdate(row._uid, { description: e.target.value })} />
            </td>
            <td className="repeat-table__actions">
              {!disabled && (
                <button type="button" className="btn btn--ghost btn--sm" aria-label="이 바디 필드 삭제" title="삭제" style={{ color: "var(--color-error)" }} onClick={() => onRemove(row._uid)}>
                  🗑
                </button>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

interface HeaderTableProps {
  rows: HeaderRow[];
  disabled: boolean;
  onUpdate: (uid: string, patch: Partial<HeaderRow>) => void;
  onRemove: (uid: string) => void;
}

function HeaderTable({ rows, disabled, onUpdate, onRemove }: HeaderTableProps) {
  return (
    <table className="repeat-table">
      <thead>
        <tr>
          <th scope="col">이름</th>
          <th scope="col">필수</th>
          <th scope="col">설명</th>
          <th scope="col" className="repeat-table__actions">
            <span className="sr-only">삭제</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row._uid}>
            <td>
              <input className="input" type="text" aria-label="헤더 이름" placeholder="예: X-Api-Key" readOnly={disabled} value={row.name} onChange={(e) => onUpdate(row._uid, { name: e.target.value })} />
            </td>
            <td style={{ textAlign: "center" }}>
              <input type="checkbox" aria-label="헤더 필수" disabled={disabled} checked={row.required} onChange={(e) => onUpdate(row._uid, { required: e.target.checked })} />
            </td>
            <td>
              <input className="input" type="text" aria-label="헤더 설명" placeholder="설명" readOnly={disabled} value={row.description} onChange={(e) => onUpdate(row._uid, { description: e.target.value })} />
            </td>
            <td className="repeat-table__actions">
              {!disabled && (
                <button type="button" className="btn btn--ghost btn--sm" aria-label="이 헤더 삭제" title="삭제" style={{ color: "var(--color-error)" }} onClick={() => onRemove(row._uid)}>
                  🗑
                </button>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
