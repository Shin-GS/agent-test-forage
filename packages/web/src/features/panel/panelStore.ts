// 사이드 패널 로컬 UI 상태 (Zustand). 채팅 store 와 분리된 독립 store.
// - 현재 활성 탭 (홈/레시피/히스토리)
// - 반응형 열림 상태 (Tablet/Mobile 오버레이 토글용)
// - 결과 상세 드릴다운 (detailExecutionId 가 있으면 탭 뷰 대신 상세 뷰 렌더)
// 데이터는 여기 두지 않는다 — 각 뷰가 React Query 로 스스로 조회한다.

import { create } from "zustand";

export type PanelTab = "home" | "recipes" | "history";

interface PanelState {
  tab: PanelTab;
  /** Tablet/Mobile 에서 패널 오버레이 열림 여부 (Desktop 은 항상 열림 취급) */
  open: boolean;
  /**
   * 결과 상세로 드릴다운한 실행 ID. null 이면 탭 목록 뷰.
   * 스택은 1단계(목록 ↔ 상세)라 배열 대신 단일 값으로 둔다 — 뒤로가기는 null 로 복귀.
   */
  detailExecutionId: number | null;
  setTab: (tab: PanelTab) => void;
  toggleOpen: () => void;
  setOpen: (open: boolean) => void;
  /** 결과 상세 열기(드릴다운). 탭 전환 시에도 유지되지 않도록 탭 변경 시 자동 해제된다. */
  openDetail: (executionId: number) => void;
  /** 상세 닫고 목록 뷰로 복귀 */
  closeDetail: () => void;
}

export const usePanelStore = create<PanelState>((set) => ({
  tab: "home",
  open: false,
  detailExecutionId: null,
  // 탭을 바꾸면 상세 뷰는 해제한다(다른 탭으로 이동 시 이전 상세가 남지 않도록).
  setTab: (tab) => set({ tab, detailExecutionId: null }),
  toggleOpen: () => set((state) => ({ open: !state.open })),
  setOpen: (open) => set({ open }),
  openDetail: (executionId) => set({ detailExecutionId: executionId, open: true }),
  closeDetail: () => set({ detailExecutionId: null }),
}));
