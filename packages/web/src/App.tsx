// 앱 라우트 정의.
// - AppLayout(전역 사이드바 + 최소 헤더 + 전역 SSE 구독 + ToastContainer)을 공통 레이아웃으로 두고,
//   그 아래 자식 라우트를 <Outlet> 으로 렌더한다. 좌측 사이드바는 모든 라우트에서 상시 표시된다.
// - "/"        → 채팅 화면 (ChatPage)
// - "/settings" → 설정 페이지 (SettingsPage)
// - 그 외 경로  → "/" 로 리다이렉트
//
// SSE 전역 구독은 AppLayout(라우트 상위)에서 1회 수행되며, 라우트 전환 시 레이아웃이
// 언마운트되지 않으므로 연결이 끊기거나 재구독되지 않는다.

import { Navigate, Route, Routes } from "react-router-dom";
import { AppLayout } from "./components/layout/AppLayout";
import { RequireAuth } from "./components/auth/RequireAuth";
import { ChatPage } from "./pages/ChatPage";
import { LoginPage } from "./pages/LoginPage";
import { SettingsPage } from "./pages/SettingsPage";
import { RecipeListPage } from "./pages/RecipeListPage";
import { RecipeEditPage } from "./pages/RecipeEditPage";

function App() {
  return (
    <Routes>
      {/* 인증 불필요 (레이아웃 밖) */}
      <Route path="/login" element={<LoginPage />} />

      {/* 인증 필요 — RequireAuth 가 세션을 확인하고, 그 아래 AppLayout 전체를 보호한다 */}
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<ChatPage />} />
          <Route path="/recipes" element={<RecipeListPage />} />
          <Route path="/recipes/new" element={<RecipeEditPage />} />
          <Route path="/recipes/:id/edit" element={<RecipeEditPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Route>
    </Routes>
  );
}

export default App;
