// 관리자 — 스펙 관리 목록 (라우트 "/admin/specs", 관리자 전용).
// 디자인 명세: docs/design/web/admin.html Case 1/1e, admin.cases.md. 기획: docs/specs/pages/admin.md.
// - 관리자 목록은 전체(INACTIVE 포함) 조회: specsApi.list({ includeInactive: true }).
//   비-admin 은 RequireAdmin 가드가 차단하고, 서버도 includeInactive 를 조용히 무시한다.
// - 테이블: 서비스명(링크형 버튼 → 상세, 키보드 접근) / baseUrl / 상태(색상+텍스트 배지) /
//   API 수 / 액션(⏸️비활성 or ▶️활성 · 🗑️삭제). INACTIVE 행은 dimmed.
// - 액션: 비활성/삭제는 ConfirmModal, 활성화는 즉시(복귀 성격). 성공 시 목록 invalidate + 토스트.
//   BE 는 ADMIN 만 허용(비-admin 403) — 에러는 토스트로 안내.
// - 빈 상태(Case 1e): 등록된 스펙 0개.
//
// 데이터: GET /specs?includeInactive=true (React Query).

import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, specsApi } from "../../api";
import type { SpecListItem } from "../../api/types";
import { ConfirmModal } from "../../components/common/ConfirmModal";
import { useToastStore } from "../../store/toastStore";

/** status(StatusView | enum 문자열) → 대문자 코드 */
function statusCode(status: SpecListItem["status"]): string {
  if (!status) return "";
  if (typeof status === "string") return status.toUpperCase();
  return (status.code ?? "").toUpperCase();
}

function isActive(spec: SpecListItem): boolean {
  return statusCode(spec.status) === "ACTIVE";
}

/** 확인이 필요한 액션 종류 (활성화는 즉시라 대상 아님) */
type ConfirmAction = "deactivate" | "delete";

export function AdminSpecListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const showToast = useToastStore((s) => s.show);

  const {
    data: specs,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ["admin-specs", { includeInactive: true }],
    queryFn: () => specsApi.list({ includeInactive: true }),
  });

  // 상태 토글/삭제 후 관리자 목록 + 일반 목록(사이드패널/레시피 등) 모두 최신화
  function invalidateSpecs() {
    void queryClient.invalidateQueries({ queryKey: ["admin-specs"] });
    void queryClient.invalidateQueries({ queryKey: ["specs"] });
  }

  const deactivateMutation = useMutation({
    mutationFn: (id: number) => specsApi.deactivate(id),
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
    mutationFn: (id: number) => specsApi.activate(id),
    onSuccess: () => {
      invalidateSpecs();
      showToast("스펙을 활성화했습니다", "success");
    },
    onError: (err) => showToast(errorMessage(err, "활성화에 실패했습니다"), "error"),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => specsApi.remove(id),
    onSuccess: () => {
      invalidateSpecs();
      showToast("스펙을 삭제했습니다", "success");
      setConfirm(null);
    },
    onError: (err) => {
      setConfirm(null);
      showToast(errorMessage(err, "삭제에 실패했습니다"), "error");
    },
  });

  // ConfirmModal 대상 (비활성/삭제만). 활성화는 즉시 처리하므로 여기 들어오지 않는다.
  const [confirm, setConfirm] = useState<{ action: ConfirmAction; spec: SpecListItem } | null>(null);

  const hasAny = (specs?.length ?? 0) > 0;

  function openSpec(id: number) {
    navigate(`/admin/specs/${id}`);
  }

  return (
    <div className="recipe-page">
      <div className="page-header">
        <span className="page-header__title">관리자 · 스펙 관리</span>
        <span className="badge badge--info" title="관리자 전용 화면">
          🔒 관리자 전용
        </span>
      </div>

      <div className="page-body">
        {isLoading && (
          <div className="recipe-state" role="status" aria-live="polite">
            스펙 목록을 불러오는 중입니다…
          </div>
        )}

        {isError && (
          <div className="recipe-state recipe-state--error" role="alert">
            <div>스펙 목록을 불러오지 못했습니다{error instanceof Error ? `: ${error.message}` : ""}</div>
            <button type="button" className="btn btn--secondary btn--sm" onClick={() => void refetch()}>
              다시 시도
            </button>
          </div>
        )}

        {/* 빈 상태 (Case 1e) */}
        {specs && !hasAny && (
          <div className="empty-state">
            <div className="empty-state__icon">🖥️</div>
            <div className="empty-state__title">등록된 스펙이 없습니다</div>
            <div className="empty-state__desc">
              외부 서버가 client-spring 라이브러리로 스펙을 등록하면 이 목록에 자동으로 나타납니다.
            </div>
          </div>
        )}

        {specs && hasAny && (
          <>
            <div className="list-count" role="status" aria-live="polite">
              총 {specs.length}개
            </div>

            <table className="data-table admin-spec-table">
              <thead>
                <tr>
                  <th scope="col">서비스명</th>
                  <th scope="col">baseUrl</th>
                  <th scope="col">상태</th>
                  <th scope="col">API 수</th>
                  <th scope="col" style={{ width: "120px" }}>
                    액션
                  </th>
                </tr>
              </thead>
              <tbody>
                {specs.map((spec) => {
                  const active = isActive(spec);
                  return (
                    <tr
                      key={spec.id}
                      className={active ? undefined : "is-inactive"}
                      onClick={() => openSpec(spec.id)}
                      title="상세 보기"
                    >
                      <td className="cell-name">
                        <button
                          type="button"
                          className="cell-name__link"
                          onClick={(e) => {
                            e.stopPropagation();
                            openSpec(spec.id);
                          }}
                        >
                          {spec.name}
                        </button>
                      </td>
                      <td className="cell-mono">{spec.baseUrl ?? "-"}</td>
                      <td>
                        {active ? (
                          <span className="badge badge--success">
                            <span className="status-dot status-dot--active" aria-hidden />
                            ACTIVE
                          </span>
                        ) : (
                          <span className="badge badge--neutral">
                            <span className="status-dot status-dot--inactive" aria-hidden />
                            INACTIVE
                          </span>
                        )}
                      </td>
                      <td>{spec.apiCount ?? 0}개</td>
                      <td onClick={(e) => e.stopPropagation()}>
                        <div className="row-actions">
                          {active ? (
                            <button
                              type="button"
                              className="btn btn--ghost btn--sm"
                              aria-label={`${spec.name} 비활성화`}
                              title="비활성화"
                              disabled={deactivateMutation.isPending}
                              onClick={() => setConfirm({ action: "deactivate", spec })}
                            >
                              ⏸️
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="btn btn--ghost btn--sm"
                              style={{ color: "var(--color-success)" }}
                              aria-label={`${spec.name} 활성화`}
                              title="활성화 (즉시)"
                              disabled={activateMutation.isPending && activateMutation.variables === spec.id}
                              onClick={() => activateMutation.mutate(spec.id)}
                            >
                              ▶️
                            </button>
                          )}
                          <button
                            type="button"
                            className="btn btn--ghost btn--sm"
                            style={{ color: "var(--color-error)" }}
                            aria-label={`${spec.name} 삭제`}
                            title="삭제"
                            disabled={deleteMutation.isPending}
                            onClick={() => setConfirm({ action: "delete", spec })}
                          >
                            🗑️
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            <p className="admin-spec-note">
              관리자 목록은 전체(INACTIVE 포함)를 조회합니다. INACTIVE는 흐리게 표시되며, AI 매칭/실행
              대상에서 제외됩니다. 삭제(소프트)된 스펙은 목록에서 제외됩니다.
            </p>
          </>
        )}
      </div>

      {/* 비활성/삭제 확인 모달 (활성화는 즉시라 모달 없음) */}
      <ConfirmModal
        open={confirm != null}
        title={confirm?.action === "delete" ? "스펙 삭제" : "스펙 비활성화"}
        description={
          confirm
            ? confirm.action === "delete"
              ? `'${confirm.spec.name}' 스펙을 삭제할까요? 소프트 삭제되며, 이 스펙을 참조하는 레시피는 실행 전 경고됩니다.`
              : `'${confirm.spec.name}' 스펙을 비활성화할까요? AI 매칭/실행 대상에서 제외됩니다. (활성화로 되돌릴 수 있습니다)`
            : undefined
        }
        confirmLabel={confirm?.action === "delete" ? "삭제" : "비활성화"}
        danger={confirm?.action === "delete"}
        onConfirm={() => {
          if (!confirm) return;
          if (confirm.action === "delete") deleteMutation.mutate(confirm.spec.id);
          else deactivateMutation.mutate(confirm.spec.id);
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
