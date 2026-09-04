// 앱 라우트 정의.
// - AppLayout(헤더 + tab-nav + 전역 SSE 구독 + ToastContainer)을 공통 레이아웃으로 두고,
//   그 아래 자식 라우트를 <Outlet> 으로 렌더한다.
// - "/"        → 채팅 화면 (ChatPage)
// - "/settings" → 설정 페이지 (SettingsPage)
// - 그 외 경로  → "/" 로 리다이렉트
//
// SSE 전역 구독은 AppLayout(라우트 상위)에서 1회 수행되며, 라우트 전환 시 레이아웃이
// 언마운트되지 않으므로 연결이 끊기거나 재구독되지 않는다.

import { Navigate, Route, Routes } from "react-router-dom";
import { AppLayout } from "./components/layout/AppLayout";
import { ChatPage } from "./pages/ChatPage";
import { SettingsPage } from "./pages/SettingsPage";

function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<ChatPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

export default App;
