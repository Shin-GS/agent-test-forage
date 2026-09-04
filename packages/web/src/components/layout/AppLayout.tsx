// 앱 공통 레이아웃.
// - 상단 헤더(로고 + 워크스페이스 + 아바타) + 탭 내비게이션(tab-nav) 을 렌더한다.
// - 탭은 NavLink 로 실제 라우팅한다(활성 탭은 현재 경로 기준 active 클래스).
//   채팅(/)·레시피(/recipes)·설정(/settings)이 실제 라우트. 서브도메인은 아직 화면이 없어
//   "준비 중"으로 비활성(클릭 시 이동 안 함) 처리한다.
// - 자식 라우트는 <Outlet> 위치에 렌더된다.
//
// SSE 구독 위치(회귀 방지 핵심):
//   useSse() 를 레이아웃(라우트 상위)에서 1회 호출한다. 레이아웃은 라우트 전환(/, /settings)에도
//   언마운트되지 않고 유지되므로 EventSource 연결이 끊기거나 재구독되지 않는다.
//   채팅 화면(ChatPage)이 아니라 여기서 구독하는 이유가 이것이다.

import { useNavigate } from "react-router-dom";
import { NavLink, Outlet } from "react-router-dom";
import { ToastContainer } from "../common/ToastContainer";
import { useSse } from "../../hooks/useSse";
import { authApi } from "../../api";
import { useAuthStore } from "../../store/authStore";

// 탭 정의. path 가 있으면 실제 라우트(NavLink), 없으면 준비 중(비활성).
const TABS = [
  { key: "chat", label: "💬 채팅", path: "/" },
  { key: "subdomain", label: "📡 서브도메인", path: null },
  { key: "recipe", label: "📋 레시피", path: "/recipes" },
  { key: "settings", label: "⚙️ 설정", path: "/settings" },
] as const;

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
  // 서버 호출 실패해도 로컬 세션은 정리한다(사용자 관점에선 로그아웃 완료).
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
      {/* 상단 헤더: 로고 + 워크스페이스 + 사용자 배지 + 로그아웃 버튼 */}
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

      {/* 탭 내비게이션 */}
      <nav className="tab-nav">
        {TABS.map((tab) =>
          tab.path ? (
            <NavLink
              key={tab.key}
              to={tab.path}
              end={tab.path === "/"}
              className={({ isActive }) => `tab-nav__item${isActive ? " active" : ""}`}
            >
              {tab.label}
            </NavLink>
          ) : (
            <button
              key={tab.key}
              type="button"
              className="tab-nav__item"
              disabled
              aria-disabled="true"
              title="준비 중"
            >
              {tab.label}
            </button>
          )
        )}
      </nav>

      {/* 자식 라우트 */}
      <Outlet />

      {/* 인앱 토스트 (대화방 락 안내 등) — 전역 1회 */}
      <ToastContainer />
    </div>
  );
}
