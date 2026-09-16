// 관리자 — 서버 수동 등록 / 메타 편집 (라우트 "/admin/specs/new" 등록, "/admin/specs/:id/edit" 수정, 관리자 전용).
// 디자인 명세: docs/design/web/admin.html Case 8, admin.cases.md "서버 수동 등록/메타 편집 (Case 8)".
// 기획: docs/specs/pages/admin.md, docs/specs/spec/registration.md#관리자-수동-등록.
//
// - 라이브러리를 못 붙이는 외부 서버(정부 API 등)를 관리자가 직접 등록/편집한다.
// - 등록/수정 겸용: :id 유무로 모드를 판별한다.
//   · 등록 모드: baseUrl 입력 + 형식 검증(http/https 스킴). 성공 → 반환 스펙 상세로 이동.
//     baseUrl 중복이면 서버가 기존 스펙에 병합하고 그 스펙을 반환 → 같은 흐름으로 이동 + "병합" 토스트.
//   · 수정 모드: 상세를 로드해 폼 초기화. baseUrl 은 읽기 전용(식별 키). 성공 → 상세로 복귀.
// - 폼: 서비스명* / baseUrl* / 설명 / 도메인 / capabilities(콤마 구분) / notes / 인증 프로필(name+loginPageUrl 다중, 0개 허용).
// - 저장 중 버튼 disabled + 스피너. 서버 400 → 필드 인라인/요약 에러. 네트워크/5xx → 토스트.
// - 저장 안 한 변경 이탈 가드: RecipeEditPage 와 동일한 dirty 스냅샷 + beforeunload + [← 목록으로] window.confirm.
//
// 데이터: POST /specs/manual (등록), PATCH /specs/{id} (수정), GET /specs/{id} (수정 모드 초기 로드).

import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, specsApi } from "../../api";
import type { ManualSpecRequest, SpecAuthProfileInput, SpecDetail } from "../../api/types";
import { PageShell } from "../../components/layout/PageShell";
import { PageActionBar } from "../../components/layout/PageActionBar";
import { useBackToList } from "../../hooks/useListNavigation";
import { useToastStore } from "../../store/toastStore";

/** 인증 프로필 행 (FE 로컬 — React key 용 _uid 포함) */
interface AuthProfileRow {
  _uid: string;
  name: string;
  loginPageUrl: string;
}

/** 폼 상태 (직렬화 가능 — dirty 스냅샷 대상) */
interface SpecFormState {
  name: string;
  baseUrl: string;
  description: string;
  domain: string;
  /** capabilities 는 화면상 콤마 구분 문자열로 편집, 저장 시 배열로 분해 */
  capabilities: string;
  notes: string;
  authProfiles: AuthProfileRow[];
}

let uidSeq = 0;
function nextUid(): string {
  uidSeq += 1;
  return `ap-${uidSeq}`;
}

function emptyForm(): SpecFormState {
  return {
    name: "",
    baseUrl: "",
    description: "",
    domain: "",
    capabilities: "",
    notes: "",
    authProfiles: [],
  };
}

/** SpecDetail → 폼 상태 (수정 모드 초기화). serviceInfo/authProfiles 를 폼 필드로 매핑 */
function detailToForm(spec: SpecDetail): SpecFormState {
  const info = spec.serviceInfo;
  return {
    name: spec.name ?? "",
    baseUrl: spec.baseUrl ?? "",
    description: info?.description ?? "",
    domain: info?.domain ?? "",
    capabilities: (info?.capabilities ?? []).join(", "),
    notes: info?.notes ?? "",
    authProfiles: spec.authProfiles.map((p) => ({
      _uid: nextUid(),
      name: p.name,
      loginPageUrl: p.loginPageUrl ?? "",
    })),
  };
}

/** dirty 비교용 스냅샷 (직렬화 가능한 폼 내용, _uid 제외) */
function formSnapshot(form: SpecFormState): string {
  return JSON.stringify({
    name: form.name,
    baseUrl: form.baseUrl,
    description: form.description,
    domain: form.domain,
    capabilities: form.capabilities,
    notes: form.notes,
    authProfiles: form.authProfiles.map((p) => ({ name: p.name, loginPageUrl: p.loginPageUrl })),
  });
}

/** 폼 → 요청 바디. 빈 문자열은 null, capabilities 는 콤마 분해, 인증 프로필은 name 빈 행 제외 */
function formToRequest(form: SpecFormState): ManualSpecRequest {
  const trimmedName = form.name.trim();
  const caps = form.capabilities
    .split(",")
    .map((c) => c.trim())
    .filter((c) => c.length > 0);
  const profiles: SpecAuthProfileInput[] = form.authProfiles
    .filter((p) => p.name.trim().length > 0)
    .map((p) => ({
      name: p.name.trim(),
      loginPageUrl: p.loginPageUrl.trim().length > 0 ? p.loginPageUrl.trim() : null,
    }));
  return {
    name: trimmedName,
    baseUrl: form.baseUrl.trim(),
    description: form.description.trim() || null,
    domain: form.domain.trim() || null,
    capabilities: caps,
    notes: form.notes.trim() || null,
    authProfiles: profiles,
  };
}

/** http/https 스킴을 포함한 URL 형식인지 (등록 모드 baseUrl 검증) */
function isValidHttpUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

/** loginPageUrl 형식 검증 (비어 있으면 통과 — 이름만 저장 허용) */
function isValidOptionalUrl(value: string): boolean {
  if (value.trim().length === 0) return true;
  return isValidHttpUrl(value.trim());
}

interface FieldErrors {
  name?: string;
  baseUrl?: string;
  authProfiles?: string;
}

export function AdminSpecFormPage() {
  // [← 목록으로]: 직전 스펙 목록 URL로 복귀(없으면 /admin/specs 폴백).
  const backToList = useBackToList("/admin/specs");
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const showToast = useToastStore((s) => s.show);

  const { id } = useParams<{ id?: string }>();
  const specId = id ? Number(id) : null;
  const isEdit = specId != null;

  const [form, setForm] = useState<SpecFormState>(emptyForm());
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<string | null>(null);

  // dirty 추적: 초기 스냅샷과 현재 폼 비교 (저장 성공 시 이탈 경고 해제)
  const initialSnapshotRef = useRef<string>(formSnapshot(emptyForm()));
  const [saved, setSaved] = useState(false);
  const isDirty = !saved && formSnapshot(form) !== initialSnapshotRef.current;

  // 수정 모드: 상세 로드
  const {
    data: detail,
    isLoading: isDetailLoading,
    isError: isDetailError,
    error: detailError,
  } = useQuery({
    queryKey: ["admin-spec", specId],
    queryFn: () => specsApi.getSpec(specId as number),
    enabled: isEdit && Number.isFinite(specId),
    retry: (count, err) => !(err instanceof ApiError && err.status === 404) && count < 2,
  });

  const applyDetail = useCallback((loaded: SpecDetail) => {
    const next = detailToForm(loaded);
    setForm(next);
    initialSnapshotRef.current = formSnapshot(next);
  }, []);

  useEffect(() => {
    if (detail) applyDetail(detail);
  }, [detail, applyDetail]);

  // 브라우저 새로고침/닫기/뒤로가기 시 dirty 경고
  useEffect(() => {
    if (!isDirty) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [isDirty]);

  // [← 목록으로]/[취소] 시 dirty 확인 후 이동
  const navigateAway = useCallback(() => {
    if (isDirty && !window.confirm("저장하지 않은 변경사항이 있습니다. 목록으로 이동할까요?")) {
      return;
    }
    backToList();
  }, [isDirty, backToList]);

  function patchForm(patch: Partial<SpecFormState>) {
    setForm((prev) => ({ ...prev, ...patch }));
  }

  function addAuthProfile() {
    patchForm({
      authProfiles: [...form.authProfiles, { _uid: nextUid(), name: "", loginPageUrl: "" }],
    });
  }

  function updateAuthProfile(uid: string, patch: Partial<AuthProfileRow>) {
    patchForm({
      authProfiles: form.authProfiles.map((p) => (p._uid === uid ? { ...p, ...patch } : p)),
    });
  }

  function removeAuthProfile(uid: string) {
    patchForm({ authProfiles: form.authProfiles.filter((p) => p._uid !== uid) });
  }

  /** 클라이언트 검증. 통과 시 빈 객체 반환 */
  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (form.name.trim().length === 0) {
      errors.name = "서비스명을 입력하세요.";
    }
    // baseUrl 은 등록 모드에서만 검증(수정 모드는 읽기 전용 식별 키)
    if (!isEdit) {
      if (form.baseUrl.trim().length === 0) {
        errors.baseUrl = "baseUrl을 입력하세요.";
      } else if (!isValidHttpUrl(form.baseUrl.trim())) {
        errors.baseUrl = "https:// 등 스킴을 포함한 URL 형식으로 입력하세요.";
      }
    }
    // 인증 프로필: 이름 있는 행의 loginPageUrl 형식(비우면 통과)
    const hasInvalidUrl = form.authProfiles.some(
      (p) => p.name.trim().length > 0 && !isValidOptionalUrl(p.loginPageUrl),
    );
    if (hasInvalidUrl) {
      errors.authProfiles = "인증 프로필의 Login Page URL은 http(s) URL 형식이어야 합니다(비우면 이름만 저장).";
    }
    return errors;
  }

  const saveMutation = useMutation({
    // 등록(createManual)은 { id } 를 반환하고, 수정(updateMeta)은 204(void). 반환 형태가 달라
    // 결과를 { id } 로 정규화한다: 등록은 반환 id(병합 시 기존 스펙 id 포함), 수정은 현재 specId.
    mutationFn: async (body: ManualSpecRequest): Promise<{ id: number }> => {
      if (isEdit) {
        await specsApi.updateMeta(specId as number, body);
        return { id: specId as number };
      }
      return specsApi.createManual(body);
    },
    onSuccess: (result) => {
      // 목록/상세/일반 스펙 목록 최신화
      void queryClient.invalidateQueries({ queryKey: ["admin-specs"] });
      void queryClient.invalidateQueries({ queryKey: ["specs"] });
      void queryClient.invalidateQueries({ queryKey: ["admin-spec", result.id] });
      setSaved(true); // 이탈 경고 해제 후 이동

      showToast(isEdit ? "서버 메타를 저장했습니다" : "서버를 등록했습니다", "success");
      // 등록/수정 모두 해당 스펙 상세로 이동.
      // 등록 시 baseUrl 중복이면 서버가 기존 스펙 id 를 반환하므로 그 상세로 자연스럽게 이동(병합 흐름).
      navigate(`/admin/specs/${result.id}`, { replace: true });
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        setServerError(err.message);
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

  const pageTitle = isEdit ? "서버 메타 편집" : "서버 등록";
  const notFound = isEdit && isDetailError && detailError instanceof ApiError && detailError.status === 404;

  // 수정 모드 로딩
  if (isEdit && isDetailLoading) {
    return (
      <PageShell title={pageTitle}>
        <div className="page-body">
          <div className="recipe-state" role="status" aria-live="polite">
            스펙 정보를 불러오는 중입니다…
          </div>
        </div>
      </PageShell>
    );
  }

  // 수정 모드 대상 없음(삭제됨/없음)
  if (notFound) {
    return (
      <PageShell title={pageTitle}>
        <div className="page-body">
          <div className="empty-state">
            <div className="empty-state__icon">🗑️</div>
            <div className="empty-state__title">존재하지 않는 스펙입니다</div>
            <div className="empty-state__desc">삭제되었거나 잘못된 경로일 수 있습니다.</div>
            <button type="button" className="btn btn--primary" onClick={backToList}>
              목록으로
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
              스펙 정보를 불러오지 못했습니다
              {detailError instanceof Error ? `: ${detailError.message}` : ""}
            </div>
            <button type="button" className="btn btn--secondary btn--sm" onClick={backToList}>
              목록으로
            </button>
          </div>
        </div>
      </PageShell>
    );
  }

  return (
    <PageShell
      title={pageTitle}
      subject={isEdit ? form.name || undefined : undefined}
      actionBar={
        <PageActionBar
          onBack={navigateAway}
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
        {/* 서버 에러 요약 (400/네트워크 등 — 필드 에러는 각 입력 하단에도 표기) */}
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
          {/* 기본 정보 */}
          <div className="form-section">
            <div className="form-group">
              <label className="form-label" htmlFor="spec-name">
                서비스명
                <span className="required-mark" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                className={`input${fieldErrors.name ? " input--error" : ""}`}
                type="text"
                id="spec-name"
                autoComplete="off"
                placeholder="예: 국세청 공개 API"
                aria-invalid={fieldErrors.name ? true : undefined}
                value={form.name}
                onChange={(e) => patchForm({ name: e.target.value })}
              />
              {fieldErrors.name && (
                <span className="form-error" role="alert">
                  {fieldErrors.name}
                </span>
              )}
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="spec-baseurl">
                baseUrl
                <span className="required-mark" aria-hidden="true">
                  *
                </span>
              </label>
              <input
                className={`input${fieldErrors.baseUrl ? " input--error" : ""}`}
                type="url"
                id="spec-baseurl"
                autoComplete="off"
                placeholder="https://api.example.go.kr"
                readOnly={isEdit}
                aria-invalid={fieldErrors.baseUrl ? true : undefined}
                value={form.baseUrl}
                onChange={(e) => patchForm({ baseUrl: e.target.value })}
              />
              {isEdit ? (
                <span className="form-hint">baseUrl은 서버 식별 키라 수정할 수 없습니다.</span>
              ) : (
                <span className="form-hint">
                  https:// 등 스킴을 포함한 형식으로 입력하세요. 이미 등록된 baseUrl이면 기존 서버에 병합됩니다.
                </span>
              )}
              {fieldErrors.baseUrl && (
                <span className="form-error" role="alert">
                  {fieldErrors.baseUrl}
                </span>
              )}
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="spec-desc">
                설명
              </label>
              <input
                className="input"
                type="text"
                id="spec-desc"
                autoComplete="off"
                placeholder="서비스에 대한 간단한 설명"
                value={form.description}
                onChange={(e) => patchForm({ description: e.target.value })}
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="spec-domain">
                도메인
              </label>
              <input
                className="input"
                type="text"
                id="spec-domain"
                autoComplete="off"
                placeholder="예: 공공, 채용, 커머스"
                value={form.domain}
                onChange={(e) => patchForm({ domain: e.target.value })}
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="spec-caps">
                기능(capabilities)
              </label>
              <input
                className="input"
                type="text"
                id="spec-caps"
                autoComplete="off"
                placeholder="콤마로 구분 (예: 조회, 등록, 결제)"
                value={form.capabilities}
                onChange={(e) => patchForm({ capabilities: e.target.value })}
              />
              <span className="form-hint">콤마(,)로 구분해 여러 개 입력할 수 있습니다.</span>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="spec-notes">
                비고
              </label>
              <textarea
                className="textarea"
                id="spec-notes"
                placeholder="관리자 메모 (선택)"
                value={form.notes}
                onChange={(e) => patchForm({ notes: e.target.value })}
              />
            </div>
          </div>

          {/* 인증 프로필 (다중, 선택 — 0개 허용) */}
          <div className="form-section">
            <div className="form-section__title">
              인증 프로필{" "}
              <span
                style={{
                  color: "var(--color-text-tertiary)",
                  fontWeight: "var(--font-weight-normal)",
                  fontSize: "var(--font-size-xs)",
                }}
              >
                (선택 · 0개 허용)
              </span>
            </div>
            {form.authProfiles.length > 0 && (
              <table className="repeat-table">
                <thead>
                  <tr>
                    <th scope="col">Name</th>
                    <th scope="col">Login Page URL</th>
                    <th scope="col" className="repeat-table__actions">
                      <span className="sr-only">삭제</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {form.authProfiles.map((profile) => (
                    <tr key={profile._uid}>
                      <td>
                        <input
                          className="input"
                          type="text"
                          aria-label="인증 프로필 이름"
                          placeholder="예: default-auth"
                          value={profile.name}
                          onChange={(e) => updateAuthProfile(profile._uid, { name: e.target.value })}
                        />
                      </td>
                      <td>
                        <input
                          className="input"
                          type="url"
                          aria-label="로그인 페이지 URL"
                          placeholder="https://example.go.kr/login"
                          value={profile.loginPageUrl}
                          onChange={(e) =>
                            updateAuthProfile(profile._uid, { loginPageUrl: e.target.value })
                          }
                        />
                      </td>
                      <td className="repeat-table__actions">
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm"
                          aria-label="이 인증 프로필 삭제"
                          title="삭제"
                          style={{ color: "var(--color-error)" }}
                          onClick={() => removeAuthProfile(profile._uid)}
                        >
                          🗑
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <button
              type="button"
              className="btn btn--secondary btn--sm repeat-table__add"
              onClick={addAuthProfile}
            >
              + 인증 프로필 추가
            </button>
            {fieldErrors.authProfiles && (
              <span className="form-error" role="alert">
                {fieldErrors.authProfiles}
              </span>
            )}
            <span className="form-hint">
              loginPageUrl은 URL 형식으로 검증합니다. 비우면 이름만 저장됩니다.
            </span>
          </div>

          <p
            style={{
              fontSize: "var(--font-size-2xs)",
              color: "var(--color-text-tertiary)",
            }}
          >
            수동 등록 메타는 관리자 수정본(adminEdited)으로 저장되어 라이브러리 재등록이 덮어쓰지 않습니다.
            ADMIN 세션(RBAC)으로 보호됩니다.
          </p>
        </form>
      </div>
    </PageShell>
  );
}
