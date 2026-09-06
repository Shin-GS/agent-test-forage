// 관리자 — 스펙 상세 (라우트 "/admin/specs/:id", 관리자 전용).
// 디자인 명세: docs/design/web/admin.html Case 2, admin.cases.md. 기획: docs/specs/pages/admin.md.
// - 엔드포인트가 많아 모달이 아닌 별도 페이지.
// - 헤더: ← 목록으로 + 서비스명 + [비활성화]/[활성화] + [삭제].
// - 기본 정보(baseUrl / 상태 배지) + 서비스 설명(읽기 전용, 편집은 별도 작업 → 비활성 버튼)
//   + API 엔드포인트 목록(method/path/summary + DEPRECATED 뱃지) + 인증 프로필(name/loginPageUrl).
// - 비활성/삭제는 ConfirmModal, 활성화는 즉시. 삭제 성공 시 목록으로 이동.
// - 404(삭제됨/없음) → "존재하지 않는 스펙" 안내 + 목록 이동.
//
// 데이터: GET /specs/{id} (specsApi.getSpec).

import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, specsApi } from "../../api";
import type { SpecDetail, SpecEndpointItem } from "../../api/types";
import { ConfirmModal } from "../../components/common/ConfirmModal";
import { useToastStore } from "../../store/toastStore";

/** StatusView → 대문자 코드 */
function statusCodeOf(status: { code: string } | undefined | null): string {
  return (status?.code ?? "").toUpperCase();
}

function isSpecActive(spec: SpecDetail): boolean {
  return statusCodeOf(spec.status) === "ACTIVE";
}

/** 엔드포인트가 스펙에서 사라진(DEPRECATED) 상태인지 */
function isDeprecated(ep: SpecEndpointItem): boolean {
  return (ep.status?.code ?? "").toUpperCase() === "DEPRECATED";
}

/** HTTP 메서드 → 색상 클래스 (디자인 api-item__method--*) */
function methodClass(method: string): string {
  switch (method.toUpperCase()) {
    case "GET":
      return "api-item__method--get";
    case "POST":
      return "api-item__method--post";
    case "PUT":
    case "PATCH":
      return "api-item__method--put";
    case "DELETE":
      return "api-item__method--delete";
    default:
      return "";
  }
}

type ConfirmAction = "deactivate" | "delete";

export function AdminSpecDetailPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const showToast = useToastStore((s) => s.show);
  const { id } = useParams<{ id: string }>();
  const specId = Number(id);

  const {
    data: spec,
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ["admin-spec", specId],
    queryFn: () => specsApi.getSpec(specId),
    enabled: Number.isFinite(specId),
    retry: (count, err) => !(err instanceof ApiError && err.status === 404) && count < 2,
  });

  function invalidateSpecs() {
    void queryClient.invalidateQueries({ queryKey: ["admin-specs"] });
    void queryClient.invalidateQueries({ queryKey: ["admin-spec", specId] });
    void queryClient.invalidateQueries({ queryKey: ["specs"] });
  }

  const deactivateMutation = useMutation({
    mutationFn: () => specsApi.deactivate(specId),
    onSuccess: () => {
      invalidateSpecs();
      showToast("스펙을 비활성화했습니다", "success");
      setConfirm(null);
    },
    onError: (err) => {
      setConfirm(null);
      showToast(errorMessage(err, "비활성화에 실패했습니다"), "error");
    },
  });

  const activateMutation = useMutation({
    mutationFn: () => specsApi.activate(specId),
    onSuccess: () => {
      invalidateSpecs();
      showToast("스펙을 활성화했습니다", "success");
    },
    onError: (err) => showToast(errorMessage(err, "활성화에 실패했습니다"), "error"),
  });

  const deleteMutation = useMutation({
    mutationFn: () => specsApi.remove(specId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-specs"] });
      void queryClient.invalidateQueries({ queryKey: ["specs"] });
      showToast("스펙을 삭제했습니다", "success");
      navigate("/admin/specs");
    },
    onError: (err) => {
      setConfirm(null);
      showToast(errorMessage(err, "삭제에 실패했습니다"), "error");
    },
  });

  const [confirm, setConfirm] = useState<ConfirmAction | null>(null);

  const notFound = isError && error instanceof ApiError && error.status === 404;

  return (
    <div className="recipe-page">
      <div className="page-header">
        <span className="page-header__title">관리자 · 스펙 상세</span>
        <span className="badge badge--info" title="관리자 전용 화면">
          🔒 관리자 전용
        </span>
      </div>

      <div className="page-body">
        {isLoading && (
          <div className="recipe-state" role="status" aria-live="polite">
            스펙 정보를 불러오는 중입니다…
          </div>
        )}

        {/* 404: 삭제되었거나 존재하지 않는 스펙 */}
        {notFound && (
          <div className="empty-state">
            <div className="empty-state__icon">🗑️</div>
            <div className="empty-state__title">존재하지 않는 스펙입니다</div>
            <div className="empty-state__desc">삭제되었거나 잘못된 경로일 수 있습니다.</div>
            <button type="button" className="btn btn--primary" onClick={() => navigate("/admin/specs")}>
              목록으로
            </button>
          </div>
        )}

        {/* 그 외 에러 */}
        {isError && !notFound && (
          <div className="recipe-state recipe-state--error" role="alert">
            <div>스펙 정보를 불러오지 못했습니다{error instanceof Error ? `: ${error.message}` : ""}</div>
            <button type="button" className="btn btn--secondary btn--sm" onClick={() => navigate("/admin/specs")}>
              목록으로
            </button>
          </div>
        )}

        {spec && (
          <div className="spec-detail">
            <div className="spec-detail__header">
              <button
                type="button"
                className="btn btn--ghost btn--sm"
                onClick={() => navigate("/admin/specs")}
                aria-label="목록으로 돌아가기"
              >
                ← 목록으로
              </button>
              <span className="spec-detail__title">{spec.name}</span>
              {isSpecActive(spec) ? (
                <button
                  type="button"
                  className="btn btn--secondary btn--sm"
                  disabled={deactivateMutation.isPending}
                  onClick={() => setConfirm("deactivate")}
                >
                  ⏸️ 비활성화
                </button>
              ) : (
                <button
                  type="button"
                  className="btn btn--secondary btn--sm"
                  disabled={activateMutation.isPending}
                  onClick={() => activateMutation.mutate()}
                >
                  ▶️ 활성화
                </button>
              )}
              <button
                type="button"
                className="btn btn--danger btn--sm"
                disabled={deleteMutation.isPending}
                onClick={() => setConfirm("delete")}
              >
                🗑️ 삭제
              </button>
            </div>

            {/* 기본 정보 */}
            <div className="spec-section">
              <div className="spec-meta">
                <span>
                  baseUrl: <code>{spec.baseUrl ?? "-"}</code>
                </span>
                <span>
                  상태:{" "}
                  {isSpecActive(spec) ? (
                    <span className="badge badge--success">
                      <span className="status-dot status-dot--active" aria-hidden />
                      ACTIVE
                    </span>
                  ) : (
                    <span className="badge badge--neutral">
                      <span className="status-dot status-dot--inactive" aria-hidden />
                      {spec.status?.description || "INACTIVE"}
                    </span>
                  )}
                </span>
              </div>
            </div>

            {/* 서비스 설명 (읽기 전용) */}
            <div className="spec-section">
              <div className="spec-section__head">
                <h4 className="spec-section__title">서비스 설명</h4>
                {spec.serviceInfo?.adminEdited && (
                  <span className="badge badge--info spec-section__flag">✎ 관리자 수정본</span>
                )}
                <button
                  type="button"
                  className="btn btn--ghost btn--sm spec-section__edit"
                  disabled
                  title="편집은 별도 작업에서 제공됩니다"
                >
                  편집 (별도 작업)
                </button>
              </div>
              <div className="card spec-info-card">
                <div className="spec-info-card__row">
                  <span className="spec-info-card__key">설명:</span>{" "}
                  {spec.serviceInfo?.description || "(없음)"}
                </div>
                {spec.serviceInfo?.domain && (
                  <div className="spec-info-card__row">
                    <span className="spec-info-card__key">도메인:</span>{" "}
                    <span className="badge badge--neutral">{spec.serviceInfo.domain}</span>
                  </div>
                )}
                {spec.serviceInfo?.capabilities && spec.serviceInfo.capabilities.length > 0 && (
                  <div className="spec-info-card__row spec-info-card__caps">
                    <span className="spec-info-card__key">기능:</span>
                    {spec.serviceInfo.capabilities.map((cap) => (
                      <span key={cap} className="badge badge--neutral">
                        {cap}
                      </span>
                    ))}
                  </div>
                )}
                {spec.serviceInfo?.notes && (
                  <div className="spec-info-card__row">
                    <span className="spec-info-card__key">비고:</span> {spec.serviceInfo.notes}
                  </div>
                )}
              </div>
              <p className="spec-section__hint">
                읽기 전용 — 관리자 수정본이 있으면 그것을, 없으면 yml 원본을 표시합니다. 편집 기능은 별도
                작업에서 제공됩니다.
              </p>
            </div>

            {/* 인증 프로필 */}
            {spec.authProfiles.length > 0 && (
              <div className="spec-section">
                <h4 className="spec-section__title">인증 프로필</h4>
                <div className="card spec-info-card">
                  {spec.authProfiles.map((profile) => (
                    <div key={profile.name} className="spec-auth-row">
                      <span>
                        Name: <code className="spec-auth-row__name">{profile.name}</code>
                      </span>
                      {profile.loginPageUrl && (
                        <span>
                          Login URL: <code className="spec-auth-row__url">{profile.loginPageUrl}</code>
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* API 엔드포인트 목록 */}
            <div className="spec-section spec-section--last">
              <h4 className="spec-section__title">API 엔드포인트 ({spec.endpoints.length}개)</h4>
              {spec.endpoints.length === 0 ? (
                <p className="spec-section__hint">등록된 엔드포인트가 없습니다.</p>
              ) : (
                <div className="api-list">
                  {spec.endpoints.map((ep) => {
                    const deprecated = isDeprecated(ep);
                    return (
                      <div
                        key={ep.id}
                        className={`api-item${deprecated ? " api-item--deprecated" : ""}`}
                      >
                        <span className={`api-item__method ${methodClass(ep.method)}`}>
                          {ep.method.toUpperCase()}
                        </span>
                        <span className="api-item__path">{ep.path}</span>
                        {ep.summary && <span className="api-item__summary">{ep.summary}</span>}
                        {deprecated && (
                          <span className="api-item__tail">
                            <span className="badge badge--warning api-item__deprecated-badge">
                              DEPRECATED
                            </span>
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
              <p className="spec-section__hint">
                DEPRECATED는 재등록 시 스펙에서 사라진 API입니다. 이를 참조하는 레시피는 실행 전 유효성
                검증에서 경고합니다.
              </p>
            </div>
          </div>
        )}
      </div>

      {/* 비활성/삭제 확인 모달 (활성화는 즉시) */}
      <ConfirmModal
        open={confirm != null}
        title={confirm === "delete" ? "스펙 삭제" : "스펙 비활성화"}
        description={
          !spec
            ? undefined
            : confirm === "delete"
              ? `'${spec.name}' 스펙을 삭제할까요? 소프트 삭제되며, 이 스펙을 참조하는 레시피는 실행 전 경고됩니다.`
              : `'${spec.name}' 스펙을 비활성화할까요? AI 매칭/실행 대상에서 제외됩니다. (활성화로 되돌릴 수 있습니다)`
        }
        confirmLabel={confirm === "delete" ? "삭제" : "비활성화"}
        danger={confirm === "delete"}
        onConfirm={() => {
          if (confirm === "delete") deleteMutation.mutate();
          else if (confirm === "deactivate") deactivateMutation.mutate();
        }}
        onCancel={() => setConfirm(null)}
      />
    </div>
  );
}

/** ApiError 면 서버 메시지, 아니면 기본 메시지 */
function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return fallback;
}
