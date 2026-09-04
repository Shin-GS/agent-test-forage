// 라우트 가드. authStore.status 로 접근을 제어한다.
//  - loading: 세션 확인 중 → 빈 스플래시(깜빡임/오탐 방지)
//  - anonymous: /login 으로 리다이렉트(원래 경로를 state.from 에 보존)
//  - authenticated: 자식(<Outlet>) 통과
// 앱 시작 시 1회 initialize() 로 세션을 확인한다.

import { useEffect } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";

export function RequireAuth() {
  const status = useAuthStore((s) => s.status);
  const initialize = useAuthStore((s) => s.initialize);
  const location = useLocation();

  useEffect(() => {
    // status 가 loading(초기값)일 때만 세션 확인. 재렌더로 중복 호출되지 않도록 방어.
    if (status === "loading") {
      void initialize();
    }
  }, [status, initialize]);

  if (status === "loading") {
    return (
      <div className="auth-splash" role="status" aria-live="polite" aria-label="확인 중">
        <span className="spinner spinner--lg" />
      </div>
    );
  }

  if (status === "anonymous") {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }

  return <Outlet />;
}
