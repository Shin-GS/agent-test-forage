// 앱 공통 레이아웃 (개편: ChatGPT식).
// - 상단 가로 탭 네비(.tab-nav)는 제거한다. 네비게이션은 좌측 통합 사이드바가 담당(ChatPage).
// - 헤더는 최소화: 로고 + 워크스페이스 + 사용자 배지 + 로그아웃.
// - 자식 라우트는 <Outlet> 위치에 렌더된다.
//
// SSE 구독 위치(회귀 방지 핵심):
//   useSse() 를 레이아웃(라우트 상위)에서 1회 호출한다. 레이아웃은 라우트 전환에도
//   언마운트되지 않으므로 EventSource 연결이 끊기거나 재구독되지 않는다.

import { useNavigate } from "react-router-dom";
import { Outlet } from "react-router-dom";
import { ToastContainer } from "../common/ToastContainer";
import { useSse } from "../../hooks/useSse";
import { authApi } from "../../api";
import { useAuthStore } from "../../store/authStore";

/** 사용자 표시 이니셜 (name 우선, 없으면 username 첫 글자) */
function userInitials(name: string | undefined, username: string | undefined): string {
  const base = (name && name.trim()) || (username && username.trim()) || "";
  if (!base) return "?";
  return base.slice(0, 2).toUpperCase();
}

export function AppLayout() {
  // 전역 SSE 구독 (앱 전역 1회, 라우트 전환에도 유지)
  useSse();

  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clear);

  // 로그아웃: 서버 세션 종료 후 스토어 초기화 + 로그인 화면 이동.
  const handleLogout = async () => {
    try {
      await authApi.logout();
    } catch {
      // 무시 — 아래에서 로컬 세션 정리
    }
    clearAuth();
    navigate("/login", { replace: true });
  };

  return (
    <div className="app-container">
      {/* 상단 헤더: 로고 + 워크스페이스 + 사용자 배지 + 로그아웃 (탭 네비는 제거됨) */}
      <header className="app-header">
        <div className="app-header__logo">🔨 AI Test Forge</div>
        <div className="app-header__workspace">🌐 demo-shop ▾</div>
        <div className="app-header__user">
          <span className="app-header__avatar" aria-hidden="true">
            {userInitials(user?.name, user?.username)}
          </span>
          <span className="app-header__username">{user?.name ?? user?.username ?? "사용자"}</span>
        </div>
        <button
          type="button"
          className="btn btn--ghost btn--sm app-header__logout"
          onClick={() => void handleLogout()}
        >
          로그아웃
        </button>
      </header>

      {/* 자식 라우트 */}
      <Outlet />

      {/* 인앱 토스트 (대화방 락 안내 등) — 전역 1회 */}
      <ToastContainer />
    </div>
  );
}
