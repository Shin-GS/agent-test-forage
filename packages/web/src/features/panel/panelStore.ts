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
  /**
   * 레시피 탭의 서비스 필터(선택 도메인). null = 전체 서비스.
   * (기획 panel/overview.md "레시피 탭 필터와의 관계") 이 값은 두 경로로 바뀐다:
   *   1) 사용자가 레시피 탭 드롭다운을 직접 조작 → setRecipeFilterApiSpecId
   *   2) 대화방 대상 서비스 변경/대화방 전환/새 대화 pending 선택 시 SidePanel 이 동기화
   *      → syncRecipeFilterToService (대화방 서비스 → 필터 단방향)
   * RecipesView 로컬 state 가 아니라 store 로 승격한 이유: 대화방 서비스(외부)에서
   * 필터 값을 밀어넣어야 하고, 탭 상태와 응집도가 높기 때문.
   */
  recipeFilterApiSpecId: number | null;
  setTab: (tab: PanelTab) => void;
  toggleOpen: () => void;
  setOpen: (open: boolean) => void;
  /** 결과 상세 열기(드릴다운). 탭 전환 시에도 유지되지 않도록 탭 변경 시 자동 해제된다. */
  openDetail: (executionId: number) => void;
  /** 상세 닫고 목록 뷰로 복귀 */
  closeDetail: () => void;
  /** 레시피 탭 드롭다운 사용자 조작 (탐색). 대화방 서비스는 건드리지 않는다(단방향). */
  setRecipeFilterApiSpecId: (apiSpecId: number | null) => void;
  /**
   * 대화방 대상 서비스 → 레시피 탭 필터 동기화(단방향). 대화방 서비스가 정해지거나 바뀌거나
   * 대화방을 전환할 때 SidePanel effect 가 현재 대상 서비스(미설정이면 null)로 호출한다.
   * 탭 전환은 하지 않는다 — 필터 값만 갱신한다. 같은 값이면 no-op(불필요 리렌더 방지).
   */
  syncRecipeFilterToService: (apiSpecId: number | null) => void;
}

export const usePanelStore = create<PanelState>((set) => ({
  tab: "home",
  open: false,
  detailExecutionId: null,
  recipeFilterApiSpecId: null,
  // 탭을 바꾸면 상세 뷰는 해제한다(다른 탭으로 이동 시 이전 상세가 남지 않도록).
  setTab: (tab) => set({ tab, detailExecutionId: null }),
  toggleOpen: () => set((state) => ({ open: !state.open })),
  setOpen: (open) => set({ open }),
  openDetail: (executionId) => set({ detailExecutionId: executionId, open: true }),
  closeDetail: () => set({ detailExecutionId: null }),
  setRecipeFilterApiSpecId: (apiSpecId) => set({ recipeFilterApiSpecId: apiSpecId }),
  syncRecipeFilterToService: (apiSpecId) =>
    set((state) =>
      state.recipeFilterApiSpecId === apiSpecId ? state : { recipeFilterApiSpecId: apiSpecId }
    ),
}));
