import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
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
