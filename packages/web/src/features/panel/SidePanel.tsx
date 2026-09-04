// 우측 사이드 패널 컨테이너 — 채팅과 분리된 독립 미니 페이지 모듈.
// (기획 docs/specs/panel/overview.md, 디자인 docs/design/web/chat.html .side-panel__*)
// - 상단 가로 탭 3개(홈/레시피/히스토리). 활성 탭은 panelStore(로컬 zustand).
// - 데이터는 각 뷰가 React Query 로 스스로 조회(채팅 store 와 분리).
// - 반응형: Desktop 고정 열(항상 열림), Tablet/Mobile 은 is-open 오버레이(헤더 토글 버튼).

import { HomeView } from "./home/HomeView";
import { HistoryView } from "./history/HistoryView";
import { usePanelStore, type PanelTab } from "./panelStore";
import { RecipesView } from "./recipes/RecipesView";
import type { PanelContext } from "./types";

type Props = PanelContext;

const TABS: { key: PanelTab; label: string }[] = [
  { key: "home", label: "🏠 홈" },
  { key: "recipes", label: "📋 레시피" },
  { key: "history", label: "🕘 히스토리" },
];

export function SidePanel(props: Props) {
  const tab = usePanelStore((s) => s.tab);
  const open = usePanelStore((s) => s.open);
  const setTab = usePanelStore((s) => s.setTab);
  const setOpen = usePanelStore((s) => s.setOpen);

  return (
    <aside className={`side-panel${open ? " is-open" : ""}`}>
      <div className="side-panel__inner">
        {/* 상단 가로 탭 */}
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
          {/* Tablet/Mobile 오버레이 닫기 (Desktop 에선 CSS 로 숨김) */}
          <button
            type="button"
            className="side-panel__close"
            aria-label="패널 닫기"
            onClick={() => setOpen(false)}
          >
            ✕
          </button>
        </div>

        {tab === "home" && (
          <HomeView
            {...props}
            onGoRecipes={() => setTab("recipes")}
            onGoHistory={() => setTab("history")}
          />
        )}
        {tab === "recipes" && <RecipesView {...props} />}
        {tab === "history" && <HistoryView {...props} />}
      </div>
    </aside>
  );
}
