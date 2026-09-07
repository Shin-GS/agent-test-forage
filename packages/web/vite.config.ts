/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // 컴포넌트 단위 테스트 (Vitest + jsdom + React Testing Library).
  // 브라우저 API가 필요하므로 jsdom 환경, setup 에서 jest-dom 매처를 로드한다.
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    css: false,
  },
  server: {
    port: 5173,
    // same-origin 세션 쿠키를 위한 프록시. /api 요청을 에이전트 서버로 전달한다.
    // (client.ts 가 VITE_API_BASE_URL 절대주소를 쓰면 직접 호출되므로 이 프록시는 상대경로 사용 시에만 관여)
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
