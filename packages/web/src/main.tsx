import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { NuqsAdapter } from "nuqs/adapters/react-router/v7";
import App from "./App";
import "./styles/index.css";

const queryClient = new QueryClient();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        {/* nuqs: URL 쿼리 ↔ 상태 동기화 어댑터 (react-router v7). 목록 페이지 필터가 사용. */}
        <NuqsAdapter>
          <App />
        </NuqsAdapter>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>
);
