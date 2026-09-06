// 관리자 — 사용자 관리 목록 (라우트 "/admin/users", 관리자 전용).
// 디자인 명세: docs/design/web/admin.html Case 3/3e/4/5/5b/6/7, admin.cases.md. 기획: docs/specs/pages/admin.md 사용자 관리(B).
// - 목록은 GET /admin/users?q= (React Query, queryKey 에 q 포함 → 서버 검색). 검색 결과 없음(3e)은 empty-state.
// - 테이블: 아이디 | 이름 | 역할(배지) | 상태(배지+dot) | 마지막 접속 | 액션. INACTIVE 행 dimmed.
// - 액션: 🔼 승격 / 🔽 강등(ConfirmModal) · 🔑 비밀번호(모달) · ⏸️ 비활성(ConfirmModal) · ▶️ 활성(즉시). 삭제 없음.
// - 위험 액션 게이팅(UX): 본인 계정 / 마지막 ACTIVE ADMIN 의 강등·비활성은 aria-disabled + 사유 툴팁.
//   aria-disabled 버튼은 클릭 no-op(포커스 유지). 실제 차단은 서버 400 → 토스트.
//
// 데이터: GET /admin/users (React Query).

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, adminUsersApi } from "../../api";
import type { AdminUserItem, StatusView } from "../../api/types";
import type { CreateUserBody } from "../../api/users";
import { ConfirmModal } from "../../components/common/ConfirmModal";
import { UserCreateModal } from "./UserCreateModal";
import { PasswordChangeModal } from "./PasswordChangeModal";
import { useToastStore } from "../../store/toastStore";
import { useAuthStore } from "../../store/authStore";

/** StatusView | 문자열 → 대문자 코드 */
function statusCode(status: StatusView | string | null | undefined): string {
  if (!status) return "";
  if (typeof status === "string") return status.toUpperCase();
  return (status.code ?? "").toUpperCase();
}

function isAdmin(user: AdminUserItem): boolean {
  return statusCode(user.role) === "ADMIN";
}
function isActiveUser(user: AdminUserItem): boolean {
  return statusCode(user.status) === "ACTIVE";
}

/** 마지막 접속 표시 (없으면 "-") */
function formatLastLogin(iso: string | null): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "-";
  return d.toLocaleString();
}

/** 확인이 필요한 액션 (활성화는 즉시라 대상 아님) */
type ConfirmAction = "promote" | "demote" | "deactivate";

export function AdminUserListPage() {
  const queryClient = useQueryClient();
  const showToast = useToastStore((s) => s.show);
  const me = useAuthStore((s) => s.user);

  // 검색어(디바운스 없이 소수 대상 — 입력 즉시 queryKey 반영). trim 은 API 레이어에서 처리.
  const [search, setSearch] = useState("");

  const {
    data: users,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ["admin-users", { q: search.trim() }],
    queryFn: () => adminUsersApi.list({ q: search.trim() }),
  });

  function invalidateUsers() {
    void queryClient.invalidateQueries({ queryKey: ["admin-users"] });
  }

  const createMutation = useMutation({
    mutationFn: (body: CreateUserBody) => adminUsersApi.create(body),
    onSuccess: () => {
      invalidateUsers();
      showToast("사용자를 생성했습니다", "success");
      setCreateOpen(false);
    },
    onError: (err) => showToast(errorMessage(err, "사용자 생성에 실패했습니다"), "error"),
  });

  const roleMutation = useMutation({
    mutationFn: (v: { id: number; role: "USER" | "ADMIN" }) => adminUsersApi.updateRole(v.id, v.role),
    onSuccess: () => {
      invalidateUsers();
      showToast("역할을 변경했습니다", "success");
      setConfirm(null);
    },
    onError: (err) => {
      setConfirm(null);
      showToast(errorMessage(err, "역할 변경에 실패했습니다"), "error");
    },
  });

  const statusMutation = useMutation({
    mutationFn: (v: { id: number; status: "ACTIVE" | "INACTIVE" }) =>
      adminUsersApi.updateStatus(v.id, v.status),
    onSuccess: (_data, v) => {
      invalidateUsers();
      showToast(v.status === "ACTIVE" ? "계정을 활성화했습니다" : "계정을 비활성화했습니다", "success");
      setConfirm(null);
    },
    onError: (err) => {
      setConfirm(null);
      showToast(errorMessage(err, "상태 변경에 실패했습니다"), "error");
    },
  });

  const passwordMutation = useMutation({
    mutationFn: (v: { id: number; password: string }) => adminUsersApi.updatePassword(v.id, v.password),
    onSuccess: () => {
      showToast("비밀번호를 변경했습니다", "success");
      setPasswordTarget(null);
    },
    onError: (err) => showToast(errorMessage(err, "비밀번호 변경에 실패했습니다"), "error"),
  });

  // ConfirmModal 대상 (승격/강등/비활성만). 활성화는 즉시.
  const [confirm, setConfirm] = useState<{ action: ConfirmAction; user: AdminUserItem } | null>(null);
  // 계정 생성 모달 열림
  const [createOpen, setCreateOpen] = useState(false);
  // 비밀번호 변경 대상 사용자
  const [passwordTarget, setPasswordTarget] = useState<AdminUserItem | null>(null);

  const hasAny = (users?.length ?? 0) > 0;

  /**
   * 위험 액션(강등/비활성) 게이팅 사유. 게이팅 없으면 null.
   * - 본인 계정: 항상 우선 노출.
   * - 마지막 ACTIVE ADMIN: 서버가 내려준 user.lastActiveAdmin 플래그로 판정(검색 필터와 무관하게 정확).
   * 본인이면서 유일 ACTIVE ADMIN 이면 본인 사유를 우선한다(명세).
   */
  function dangerGateReason(user: AdminUserItem): string | null {
    const isSelf = me != null && me.id === user.id;
    if (isSelf) return "본인 계정은 변경할 수 없습니다";
    if (user.lastActiveAdmin) return "마지막 관리자는 변경할 수 없습니다";
    return null;
  }

  return (
    <div className="recipe-page">
      <div className="page-header">
        <span className="page-header__title">관리자 · 사용자 관리</span>
        <span className="badge badge--info" title="관리자 전용 화면">
          🔒 관리자 전용
        </span>
      </div>

      <div className="page-body">
        {/* 툴바: 검색 + 카운트 + 추가 */}
        <div className="admin-user-toolbar">
          <div className="admin-user-search">
            <span className="admin-user-search__icon" aria-hidden>
              🔍
            </span>
            <input
              className="input admin-user-search__input"
              type="search"
              aria-label="아이디 검색"
              placeholder="아이디 검색"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <span className="list-count" role="status" aria-live="polite">
            총 {users?.length ?? 0}명
          </span>
          <span className="page-header__spacer" style={{ flex: 1 }} />
          <button
            type="button"
            className="btn btn--primary btn--sm"
            onClick={() => setCreateOpen(true)}
          >
            + 사용자 추가
          </button>
        </div>

        {isLoading && (
          <div className="recipe-state" role="status" aria-live="polite">
            사용자 목록을 불러오는 중입니다…
          </div>
        )}

        {isError && (
          <div className="recipe-state recipe-state--error" role="alert">
            <div>사용자 목록을 불러오지 못했습니다{error instanceof Error ? `: ${error.message}` : ""}</div>
            <button type="button" className="btn btn--secondary btn--sm" onClick={() => void refetch()}>
              다시 시도
            </button>
          </div>
        )}

        {/* 검색 결과 없음 (Case 3e) */}
        {users && !hasAny && (
          <div className="empty-state">
            <div className="empty-state__icon">🔍</div>
            <div className="empty-state__title">검색 결과가 없습니다</div>
            <div className="empty-state__desc">
              {search.trim()
                ? `"${search.trim()}"와 일치하는 아이디가 없습니다. 검색어를 지우면 전체 사용자 목록이 표시됩니다.`
                : "표시할 사용자가 없습니다."}
            </div>
          </div>
        )}

        {users && hasAny && (
          <>
            <table className="data-table admin-user-table">
              <thead>
                <tr>
                  <th scope="col">아이디</th>
                  <th scope="col">이름</th>
                  <th scope="col">역할</th>
                  <th scope="col">상태</th>
                  <th scope="col">마지막 접속</th>
                  <th scope="col" style={{ width: "140px" }}>
                    액션
                  </th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => {
                  const admin = isAdmin(user);
                  const active = isActiveUser(user);
                  const isSelf = me != null && me.id === user.id;
                  const gateReason = dangerGateReason(user);
                  return (
                    <tr key={user.id} className={active ? undefined : "is-inactive"}>
                      <td className="cell-name">
                        {user.username}
                        {isSelf && (
                          <span className="badge badge--info" style={{ fontSize: "var(--font-size-2xs)", marginLeft: "var(--space-1)" }}>
                            (나)
                          </span>
                        )}
                      </td>
                      <td>{user.name ?? "-"}</td>
                      <td>
                        {admin ? (
                          <span className="badge badge--info">관리자</span>
                        ) : (
                          <span className="badge badge--neutral">사용자</span>
                        )}
                      </td>
                      <td>
                        {active ? (
                          <span className="badge badge--success">
                            <span className="status-dot status-dot--active" aria-hidden />
                            활성
                          </span>
                        ) : (
                          <span className="badge badge--error">
                            <span className="status-dot status-dot--inactive" aria-hidden />
                            비활성
                          </span>
                        )}
                      </td>
                      <td className="cell-time">{formatLastLogin(user.lastLoginAt)}</td>
                      <td>
                        <div className="row-actions">
                          {/* 역할: ADMIN → 강등 / USER → 승격 */}
                          {admin ? (
                            <RoleButton
                              icon="🔽"
                              label={`${user.username} 사용자로 강등`}
                              gateReason={gateReason}
                              enabledTitle="사용자로 강등"
                              onClick={() => setConfirm({ action: "demote", user })}
                            />
                          ) : (
                            <button
                              type="button"
                              className="btn btn--ghost btn--sm"
                              aria-label={`${user.username} 관리자로 승격`}
                              title="관리자로 승격"
                              onClick={() => setConfirm({ action: "promote", user })}
                            >
                              🔼
                            </button>
                          )}

                          {/* 비밀번호 변경 (항상 가능) */}
                          <button
                            type="button"
                            className="btn btn--ghost btn--sm"
                            aria-label={`${user.username} 비밀번호 변경`}
                            title="비밀번호 변경"
                            onClick={() => setPasswordTarget(user)}
                          >
                            🔑
                          </button>

                          {/* 상태: ACTIVE → 비활성(게이팅) / INACTIVE → 활성(즉시) */}
                          {active ? (
                            <RoleButton
                              icon="⏸️"
                              label={`${user.username} 비활성화`}
                              gateReason={gateReason}
                              enabledTitle="비활성화"
                              danger
                              onClick={() => setConfirm({ action: "deactivate", user })}
                            />
                          ) : (
                            <button
                              type="button"
                              className="btn btn--ghost btn--sm"
                              style={{ color: "var(--color-success)" }}
                              aria-label={`${user.username} 활성화`}
                              title="활성화 (즉시)"
                              disabled={statusMutation.isPending && statusMutation.variables?.id === user.id}
                              onClick={() => statusMutation.mutate({ id: user.id, status: "ACTIVE" })}
                            >
                              ▶️
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            <p className="admin-spec-note">
              계정은 삭제하지 않고 비활성화로 정리합니다(감사/이력 보존). 본인 계정과 마지막 ACTIVE 관리자
              계정은 강등·비활성이 서버에서 차단됩니다(400). 역할/상태 변경은 다음 요청부터 반영됩니다.
            </p>
          </>
        )}
      </div>

      {/* 역할/상태 변경 확인 모달 (활성화는 즉시라 모달 없음) */}
      <ConfirmModal
        open={confirm != null}
        title={
          confirm?.action === "promote"
            ? "관리자로 승격"
            : confirm?.action === "demote"
              ? "사용자로 강등"
              : "계정 비활성화"
        }
        description={confirm ? confirmDescription(confirm.action, confirm.user.username) : undefined}
        confirmLabel={
          confirm?.action === "promote" ? "승격" : confirm?.action === "demote" ? "강등" : "비활성화"
        }
        danger={confirm?.action === "deactivate"}
        onConfirm={() => {
          if (!confirm) return;
          if (confirm.action === "promote") roleMutation.mutate({ id: confirm.user.id, role: "ADMIN" });
          else if (confirm.action === "demote") roleMutation.mutate({ id: confirm.user.id, role: "USER" });
          else statusMutation.mutate({ id: confirm.user.id, status: "INACTIVE" });
        }}
        onCancel={() => setConfirm(null)}
      />

      {/* 계정 생성 모달 */}
      <UserCreateModal
        open={createOpen}
        submitting={createMutation.isPending}
        onSubmit={(body) => createMutation.mutate(body)}
        onCancel={() => setCreateOpen(false)}
      />

      {/* 비밀번호 변경 모달 */}
      <PasswordChangeModal
        open={passwordTarget != null}
        username={passwordTarget?.username ?? null}
        submitting={passwordMutation.isPending}
        onSubmit={(password) => {
          if (passwordTarget) passwordMutation.mutate({ id: passwordTarget.id, password });
        }}
        onCancel={() => setPasswordTarget(null)}
      />
    </div>
  );
}

/**
 * 위험 액션(강등/비활성) 버튼. 게이팅 사유가 있으면 aria-disabled + 툴팁으로 노출하고
 * 클릭·Enter 는 no-op(포커스 유지). disabled 대신 aria-disabled 를 쓰는 이유는 스크린리더가
 * 버튼과 사유를 읽을 수 있게 하기 위함(admin.cases.md FE 구현 필수).
 */
function RoleButton({
  icon,
  label,
  gateReason,
  enabledTitle,
  danger,
  onClick,
}: {
  icon: string;
  label: string;
  gateReason: string | null;
  enabledTitle: string;
  danger?: boolean;
  onClick: () => void;
}) {
  const gated = gateReason != null;
  return (
    <button
      type="button"
      className="btn btn--ghost btn--sm"
      aria-disabled={gated || undefined}
      aria-label={gated ? `${label} (${gateReason})` : label}
      title={gated ? gateReason : enabledTitle}
      style={danger && !gated ? { color: "var(--color-error)" } : undefined}
      onClick={() => {
        if (gated) return; // no-op — 포커스는 유지
        onClick();
      }}
    >
      {icon}
    </button>
  );
}

/** ConfirmModal 설명 문구 (다음 요청부터 반영 안내 포함) */
function confirmDescription(action: ConfirmAction, username: string): string {
  switch (action) {
    case "promote":
      return `'${username}'을(를) 관리자로 승격할까요? 변경은 다음 요청부터 반영됩니다.`;
    case "demote":
      return `'${username}'를 일반 사용자로 강등할까요? 관리자 권한을 잃습니다. 변경은 다음 요청부터 반영됩니다.`;
    case "deactivate":
      return `'${username}' 계정을 비활성화할까요? 로그인이 차단되며, 진행 중인 세션은 다음 요청에서 만료됩니다.`;
  }
}

/** ApiError 면 서버 메시지, 아니면 기본 메시지 */
function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return fallback;
}
