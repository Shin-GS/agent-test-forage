// 홈 탭: 요약 대시보드.
// - 자주 쓴 레시피: recipesApi.list(전체) → usageCount desc, lastUsedAt desc 정렬 상위 5
// - 최근 실행: executionsApi.history(size 5)
// 각 섹션 [전체 →] 로 해당 탭 전환(onGoRecipes / onGoHistory).

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { executionsApi, recipesApi } from "../../../api";
import type { CursorPage, ExecutionSummaryView, RecipeSummary } from "../../../api/types";
import { formatRelative, formatTime, statusIcon } from "../shared/format";
import type { PanelContext } from "../types";

interface Props extends PanelContext {
  onGoRecipes: () => void;
  onGoHistory: () => void;
  /** 최근 실행 항목 클릭 시 결과 상세 드릴다운 열기 */
  onOpenDetail: (executionId: number) => void;
}

const TOP_N = 5;

/** usageCount desc → lastUsedAt desc 정렬 후 상위 N */
function rankRecipes(list: RecipeSummary[]): RecipeSummary[] {
  return [...list]
    .sort((a, b) => {
      if (b.usageCount !== a.usageCount) return b.usageCount - a.usageCount;
      const ta = a.lastUsedAt ? Date.parse(a.lastUsedAt) : 0;
      const tb = b.lastUsedAt ? Date.parse(b.lastUsedAt) : 0;
      return tb - ta;
    })
    .slice(0, TOP_N);
}

export function HomeView({
  conversationStatus,
  onRunRecipe,
  onGoRecipes,
  onGoHistory,
  onOpenDetail,
}: Props) {
  const busy = conversationStatus !== "idle";

  const recipesQuery = useQuery<RecipeSummary[]>({
    queryKey: ["recipes", "home"],
    queryFn: () => recipesApi.list(),
  });

  const recentQuery = useQuery<CursorPage<ExecutionSummaryView>>({
    queryKey: ["executions", "home"],
    queryFn: () => executionsApi.history({ size: TOP_N }),
  });

  const topRecipes = useMemo(() => rankRecipes(recipesQuery.data ?? []), [recipesQuery.data]);
  const recent = recentQuery.data?.items ?? [];

  return (
    <div className="side-panel__view" role="tabpanel" aria-label="홈">
      <div className="side-panel__body">
        {/* 자주 쓴 레시피 */}
        <div className="side-panel__section">
          <div className="side-panel__section-head">
            <span className="side-panel__section-title">자주 쓴 레시피</span>
            <button type="button" className="side-panel__link" onClick={onGoRecipes}>
              레시피 전체 →
            </button>
          </div>

          {topRecipes.length === 0 ? (
            <div className="side-panel__empty">
              아직 레시피가 없어요. 관리자에게 요청하거나 직접 만들어보세요.
            </div>
          ) : (
            topRecipes.map((recipe) => {
              const relative = formatRelative(recipe.lastUsedAt);
              return (
                <div key={recipe.id} className="side-panel__recipe">
                  <div className="side-panel__recipe-top">
                    <span className="side-panel__recipe-name">{recipe.name}</span>
                    <span className="badge badge--neutral">{recipe.visibility.description}</span>
                    <button
                      type="button"
                      className="btn btn--secondary btn--sm side-panel__recipe-run"
                      disabled={busy}
                      title={busy ? "실행 중에는 사용할 수 없어요" : "실행"}
                      onClick={() => onRunRecipe(recipe.id, recipe.name)}
                    >
                      ▶
                    </button>
                  </div>
                  {relative && (
                    <span className="side-panel__recipe-meta">최근 사용: {relative}</span>
                  )}
                </div>
              );
            })
          )}
        </div>

        {/* 최근 실행 */}
        <div className="side-panel__section">
          <div className="side-panel__section-head">
            <span className="side-panel__section-title">최근 실행</span>
            <button type="button" className="side-panel__link" onClick={onGoHistory}>
              히스토리 전체 →
            </button>
          </div>

          {recent.length === 0 ? (
            <div className="side-panel__empty">아직 실행한 레시피가 없어요.</div>
          ) : (
            recent.map((item) => {
              const serviceLabel =
                item.serviceName ?? (item.apiSpecId == null ? "여러 서비스" : null);
              return (
                <button
                  key={item.id}
                  type="button"
                  className="side-panel__history-item side-panel__history-item--clickable"
                  onClick={() => onOpenDetail(item.id)}
                  title="결과 상세 보기"
                >
                  <span aria-hidden>{statusIcon(item.status.code)}</span>
                  <div className="side-panel__history-main">
                    <span className="side-panel__history-name">{item.title ?? "레시피"}</span>
                    {serviceLabel && (
                      <span className="side-panel__history-summary">🌐 {serviceLabel}</span>
                    )}
                  </div>
                  <span className="side-panel__history-time">{formatTime(item.startedAt)}</span>
                </button>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
