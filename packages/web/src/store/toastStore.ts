// 최소 토스트 스토어. 인앱 알림(대화방 락 안내 등)을 잠시 띄운다.
// 채팅/패널과 독립. show(message, level) 로 띄우고 duration 후 자동 제거.

import { create } from "zustand";

export type ToastLevel = "info" | "warning" | "error" | "success";

export interface ToastItem {
  id: number;
  message: string;
  level: ToastLevel;
}

interface ToastState {
  toasts: ToastItem[];
  /** 토스트 표시. 기본 3초 후 자동 제거 */
  show: (message: string, level?: ToastLevel, durationMs?: number) => void;
  dismiss: (id: number) => void;
}

let seq = 1;

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],
  show: (message, level = "info", durationMs = 3000) => {
    const id = seq++;
    set((state) => ({ toasts: [...state.toasts, { id, message, level }] }));
    if (durationMs > 0) {
      setTimeout(() => get().dismiss(id), durationMs);
    }
  },
  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}));
