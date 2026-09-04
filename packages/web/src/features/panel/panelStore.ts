// 사이드 패널 로컬 UI 상태 (Zustand). 채팅 store 와 분리된 독립 store.
// - 현재 활성 탭 (홈/레시피/히스토리)
// - 반응형 열림 상태 (Tablet/Mobile 오버레이 토글용)
// 데이터는 여기 두지 않는다 — 각 뷰가 React Query 로 스스로 조회한다.

import { create } from "zustand";

export type PanelTab = "home" | "recipes" | "history";

interface PanelState {
  tab: PanelTab;
  /** Tablet/Mobile 에서 패널 오버레이 열림 여부 (Desktop 은 항상 열림 취급) */
  open: boolean;
  setTab: (tab: PanelTab) => void;
  toggleOpen: () => void;
  setOpen: (open: boolean) => void;
}

export const usePanelStore = create<PanelState>((set) => ({
  tab: "home",
  open: false,
  setTab: (tab) => set({ tab }),
  toggleOpen: () => set((state) => ({ open: !state.open })),
  setOpen: (open) => set({ open }),
}));
