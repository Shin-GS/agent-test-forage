// 앱 공통 레이아웃 (개편: ChatGPT식, 전역 사이드바 승격).
// - 좌측 통합 사이드바(AppSidebar)를 모든 라우트에서 상시 표시한다.
// - 구조: .app-container > .main-content(가로) > AppSidebar + .app-main(<Outlet/>)
// - 전역 상단 헤더 바는 폐지했다(대상 서비스는 우측 사이드 패널 최상단 블록으로 이동).
//   로고/회원정보/로그아웃은 좌측 사이드바에 있다.
// - 좌측 접기 상태(testforge.ui.leftSidebar.collapsed)는 전역이므로 여기서 관리한다.
//
// SSE 구독 위치(회귀 방지 핵심):
//   useSse() 를 레이아웃(라우트 상위)에서 1회 호출한다. 레이아웃은 라우트 전환에도
//   언마운트되지 않으므로 EventSource 연결이 끊기거나 재구독되지 않는다.

import { useNavigate, Outlet } from "react-router-dom";
import { ToastContainer } from "../common/ToastContainer";
import { AppSidebar } from "./AppSidebar";
import { useSse } from "../../hooks/useSse";
import { authApi } from "../../api";
import { useAuthStore } from "../../store/authStore";
import { useLocalStorageState } from "../../hooks/useLocalStorageState";
import { useMediaQuery } from "../../hooks/useMediaQuery";

// 좌측 사이드바 접힘 상태 localStorage 키 (전역)
const LS_LEFT_COLLAPSED = "testforge.ui.leftSidebar.collapsed";

export function AppLayout() {
  // 전역 SSE 구독 (앱 전역 1회, 라우트 전환에도 유지)
  useSse();

  const navigate = useNavigate();
  const clearAuth = useAuthStore((s) => s.clear);

  // 좌측 사이드바 접힘 (전역). 모바일(<768)은 강제 레일.
  const [leftCollapsed, setLeftCollapsed] = useLocalStorageState<boolean>(LS_LEFT_COLLAPSED, false);
  const isMobile = useMediaQuery("(max-width: 767px)");
  const collapsed = isMobile ? true : leftCollapsed;

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
      <div className="main-content">
        {/* 좌측 전역 사이드바 (모든 라우트 상시) */}
        <AppSidebar
          collapsed={collapsed}
          onToggleCollapse={() => setLeftCollapsed((v) => !v)}
          onLogout={() => void handleLogout()}
        />

        {/* 우측 콘텐츠 영역: 전역 상단 헤더 바는 폐지(대상 서비스는 우측 패널 블록으로 이동). */}
        <div className="app-main">
          <Outlet />
        </div>
      </div>

      {/* 인앱 토스트 (대화방 락 안내 등) — 전역 1회 */}
      <ToastContainer />
    </div>
  );
}
