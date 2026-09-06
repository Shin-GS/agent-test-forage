// 관리자 전용 라우트 가드 (RBAC). RequireAuth 하위(AppLayout 안)에서 role 만 추가 검사한다.
//  - loading: 세션 확인 중 → 빈 스플래시(RequireAuth 와 동일, 깜빡임/오탐 방지)
//  - 미인증(anonymous)은 상위 RequireAuth 가 이미 /login 으로 보내므로 여기 도달하지 않는다.
//  - role !== ADMIN: "/" 로 리다이렉트(replace) — 비-admin 이 URL 직접 접근 시 차단.
//  - ADMIN: 자식(<Outlet>) 통과.
// FE 게이팅은 UX 힌트일 뿐이며, 실제 권한은 서버가 매 요청 재확인해 강제한다(API 403).

import { Navigate, Outlet } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";

export function RequireAdmin() {
  const status = useAuthStore((s) => s.status);
  const user = useAuthStore((s) => s.user);

  if (status === "loading") {
    return (
      <div className="auth-splash" role="status" aria-live="polite" aria-label="확인 중">
        <span className="spinner spinner--lg" />
      </div>
    );
  }

  if (user?.role !== "ADMIN") {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
