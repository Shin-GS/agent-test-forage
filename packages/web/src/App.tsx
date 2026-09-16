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
import { RequireAdmin } from "./components/auth/RequireAdmin";
import { ChatPage } from "./pages/ChatPage";
import { LoginPage } from "./pages/LoginPage";
import { SettingsPage } from "./pages/SettingsPage";
import { RecipeListPage } from "./pages/RecipeListPage";
import { RecipeEditPage } from "./pages/RecipeEditPage";
import { HistoryPage } from "./pages/HistoryPage";
import { HistoryDetailPage } from "./pages/HistoryDetailPage";
import { AdminSpecListPage } from "./pages/admin/AdminSpecListPage";
import { AdminSpecDetailPage } from "./pages/admin/AdminSpecDetailPage";
import { AdminSpecFormPage } from "./pages/admin/AdminSpecFormPage";
import { AdminEndpointFormPage } from "./pages/admin/AdminEndpointFormPage";
import { AdminUserListPage } from "./pages/admin/AdminUserListPage";

function App() {
  return (
    <Routes>
      {/* 인증 불필요 (레이아웃 밖) */}
      <Route path="/login" element={<LoginPage />} />

      {/* 인증 필요 — RequireAuth 가 세션을 확인하고, 그 아래 AppLayout 전체를 보호한다 */}
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route path="/" element={<ChatPage />} />
          {/* 대화방 딥링크: URL 이 source of truth. ChatPage 가 :conversationId 를 읽어 store 동기화 */}
          <Route path="/c/:conversationId" element={<ChatPage />} />
          <Route path="/recipes" element={<RecipeListPage />} />
          <Route path="/recipes/new" element={<RecipeEditPage />} />
          <Route path="/recipes/:id/edit" element={<RecipeEditPage />} />
          <Route path="/history" element={<HistoryPage />} />
          {/* 실행 결과 상세: 별도 페이지(모달 아님). :executionId 가 URL 의 단일 진실 소스 */}
          <Route path="/history/:executionId" element={<HistoryDetailPage />} />
          <Route path="/settings" element={<SettingsPage />} />

          {/* 관리자 전용 (RBAC) — 비-admin 은 RequireAdmin 이 "/" 로 리다이렉트 */}
          <Route element={<RequireAdmin />}>
            <Route path="/admin/specs" element={<AdminSpecListPage />} />
            {/* 정적 세그먼트(new)를 동적(:id)보다 먼저 둔다. react-router v6 는 정적 우선 랭킹이나 명시적으로 안전하게 배치. */}
            <Route path="/admin/specs/new" element={<AdminSpecFormPage />} />
            {/* API 편집(Case 9)은 정적 세그먼트(endpoints/new)를 동적(:id)보다 먼저/명시적으로 배치 */}
            <Route path="/admin/specs/:id/endpoints/new" element={<AdminEndpointFormPage />} />
            <Route path="/admin/specs/:id/endpoints/:endpointId/edit" element={<AdminEndpointFormPage />} />
            <Route path="/admin/specs/:id" element={<AdminSpecDetailPage />} />
            <Route path="/admin/specs/:id/edit" element={<AdminSpecFormPage />} />
            <Route path="/admin/users" element={<AdminUserListPage />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Route>
    </Routes>
  );
}

export default App;
