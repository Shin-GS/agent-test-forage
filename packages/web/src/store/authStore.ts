// 인증 전역 스토어 (Zustand).
// - status: 'loading'(초기 확인 중) | 'authenticated' | 'anonymous'
// - initialize(): 앱 시작 시 GET /auth/me 로 세션 확인 → 성공 authenticated / 실패 anonymous
// - setUser(): 로그인 성공 직후 사용자 설정(authenticated)
// - clear(): 로그아웃 시 초기화(anonymous)
// 라우트 가드가 status 로 접근을 제어하고, userId 가 필요한 곳은 user.id 를 참조한다.

import { create } from "zustand";
import { authApi, ApiError } from "../api";
import { setUnauthorizedHandler } from "../api/client";
import type { AuthUser } from "../api/auth";

export type AuthStatus = "loading" | "authenticated" | "anonymous";

interface AuthState {
  user: AuthUser | null;
  status: AuthStatus;
  /** 앱 시작 시 세션 확인 (GET /auth/me) */
  initialize: () => Promise<void>;
  /** 로그인 성공 후 사용자 설정 */
  setUser: (user: AuthUser) => void;
  /** 로그아웃/세션 만료 시 초기화 */
  clear: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  status: "loading",

  initialize: async () => {
    try {
      const user = await authApi.me();
      set({ user, status: "authenticated" });
    } catch (err) {
      // 401 등: 비로그인. 그 외 네트워크 오류도 anonymous 로 두어 로그인 화면으로 유도한다.
      void (err instanceof ApiError);
      set({ user: null, status: "anonymous" });
    }
  },

  setUser: (user) => set({ user, status: "authenticated" }),

  clear: () => set({ user: null, status: "anonymous" }),
}));

// 세션 만료(401) 전역 처리 등록.
// 로그인된 상태에서 API 가 401 을 주면(세션 만료/서버 재시작) 로컬 세션을 정리해
// 라우트 가드가 /login 으로 유도하도록 한다. 이미 anonymous 면 상태를 건드리지 않는다.
setUnauthorizedHandler(() => {
  if (useAuthStore.getState().status === "authenticated") {
    useAuthStore.getState().clear();
  }
});
