// 우측 사이드 패널 컨테이너 — 채팅과 분리된 독립 미니 페이지 모듈.
// (기획 docs/specs/panel/overview.md, 디자인 docs/design/web/chat.html .side-panel__*)
// - 상단 가로 탭 3개(홈/레시피/히스토리). 활성 탭은 panelStore(로컬 zustand).
// - 데이터는 각 뷰가 React Query 로 스스로 조회(채팅 store 와 분리).
// - 반응형: Desktop 고정 열(항상 열림), Tablet/Mobile 은 is-open 오버레이(헤더 토글 버튼).

import type { CSSProperties } from "react";
import { ExecutionDetailView } from "./detail/ExecutionDetailView";
import { HomeView } from "./home/HomeView";
import { HistoryView } from "./history/HistoryView";
import { PanelServiceBlock } from "./PanelServiceBlock";
import { usePanelStore, type PanelTab } from "./panelStore";
import { RecipesView } from "./recipes/RecipesView";
import type { PanelContext } from "./types";

interface Props extends PanelContext {
  /** 완전 접힘 여부 (접히면 펼치기 핸들만 노출) */
  collapsed?: boolean;
  /** 펼치기 핸들 클릭 */
  onExpand?: () => void;
  /** 접기 버튼 클릭 (탭 바 우측) */
  onCollapse?: () => void;
  /** 리사이즈된 폭 등 인라인 스타일 (Desktop 펼침 상태) */
  style?: CSSProperties;
  /** 오버레이 모드(<1200px). true 면 접기 버튼이 완전 접기 대신 오버레이만 닫는다. */
  overlayMode?: boolean;

  // ── 대상 서비스 블록(최상단 고정) ──
  /** 현재 대화방 ID. null 이면 새 대화 */
  conversationId: number | null;
  /** 현재 대화방 대상 서비스 apiSpecId */
  conversationApiSpecId: number | null;
  /** 현재 대화방 서비스 표시명 */
  conversationServiceName?: string | null;
  /** 새 대화 pending 대상 서비스 */
  pendingApiSpecId: number | null;
  /** 새 대화 pending 갱신 */
  onChangePendingService: (apiSpecId: number | null) => void;
  /** 기존 대화 서비스 변경 성공 반영 */
  onServiceChanged: (apiSpecId: number | null, serviceName: string | null) => void;
}

const TABS: { key: PanelTab; label: string }[] = [
  { key: "home", label: "🏠 홈" },
  { key: "recipes", label: "📋 레시피" },
  { key: "history", label: "🕘 히스토리" },
];

export function SidePanel({
  collapsed,
  onExpand,
  onCollapse,
  style,
  overlayMode,
  conversationId,
  conversationApiSpecId,
  conversationServiceName,
  pendingApiSpecId,
  onChangePendingService,
  onServiceChanged,
  ...props
}: Props) {
  const tab = usePanelStore((s) => s.tab);
  const open = usePanelStore((s) => s.open);
  const detailExecutionId = usePanelStore((s) => s.detailExecutionId);
  const setTab = usePanelStore((s) => s.setTab);
  const setOpen = usePanelStore((s) => s.setOpen);
  const openDetail = usePanelStore((s) => s.openDetail);
  const closeDetail = usePanelStore((s) => s.closeDetail);

  return (
    <aside
      className={`side-panel${collapsed ? " hidden" : ""}${open ? " is-open" : ""}`}
      style={collapsed ? undefined : style}
    >
      {/* 접힘 상태: 우측 가장자리 펼치기 핸들(◀)만 노출 */}
      <button
        type="button"
        className="side-panel__reveal"
        aria-label="패널 펼치기"
        onClick={() => onExpand?.()}
      >
        ◀
      </button>

      <div className="side-panel__inner">
        {/* 대상 서비스 블록 (최상단 고정 — 탭 위, 상세 드릴다운과 무관하게 항상 표시) */}
        <PanelServiceBlock
          conversationId={conversationId}
          conversationApiSpecId={conversationApiSpecId}
          conversationServiceName={conversationServiceName}
          pendingApiSpecId={pendingApiSpecId}
          onChangePending={onChangePendingService}
          onServiceChanged={onServiceChanged}
        />

        {/* 상단 가로 탭 (상세 드릴다운 중에는 숨겨 목록 컨텍스트 혼동 방지) */}
        {detailExecutionId == null && (
          <div className="side-panel__tabs" role="tablist" aria-label="사이드 패널">
            {TABS.map((t) => (
              <button
                key={t.key}
                type="button"
                role="tab"
                aria-selected={tab === t.key}
                className={`side-panel__tab${tab === t.key ? " is-active" : ""}`}
                onClick={() => setTab(t.key)}
              >
                {t.label}
              </button>
            ))}
            {/* Desktop: 완전 접기(hidden). Tablet/Mobile(overlayMode): 오버레이만 닫기. */}
            <button
              type="button"
              className="side-panel__close"
              aria-label="패널 접기"
              onClick={() => {
                if (overlayMode) {
                  setOpen(false);
                } else {
                  onCollapse?.();
                }
              }}
            >
              ✕
            </button>
          </div>
        )}

        {/* 상세 드릴다운이 있으면 최우선으로 상세 뷰를 렌더(스택 top) */}
        {detailExecutionId != null ? (
          <ExecutionDetailView executionId={detailExecutionId} onBack={closeDetail} />
        ) : (
          <>
            {tab === "home" && (
              <HomeView
                {...props}
                onGoRecipes={() => setTab("recipes")}
                onGoHistory={() => setTab("history")}
                onOpenDetail={openDetail}
              />
            )}
            {tab === "recipes" && <RecipesView {...props} />}
            {tab === "history" && <HistoryView {...props} onOpenDetail={openDetail} />}
          </>
        )}
      </div>
    </aside>
  );
}
