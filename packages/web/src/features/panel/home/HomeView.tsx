// 홈 탭: 요약 대시보드.
// - 자주 쓴 레시피: recipesApi.list(전체) → usageCount desc, lastUsedAt desc 정렬 상위 5
// - 최근 실행: executionsApi.history(size 5)
// 각 섹션 [전체 →] 로 해당 탭 전환(onGoRecipes / onGoHistory).

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { executionsApi, recipesApi } from "../../../api";
import type { CursorPage, ExecutionSummaryView, RecipeSummary } from "../../../api/types";
import { ConfirmModal } from "../../../components/common/ConfirmModal";
import { formatRelative, formatTime, statusIcon } from "../shared/format";
import type { PanelContext } from "../types";
import { useServices } from "../recipes/useRecipes";

/** 실행 확인 모달 대상 (레시피 ▶ 클릭 시 즉시 실행하지 않고 확인) */
interface RunTarget {
  id: number;
  name: string;
  serviceName: string | null;
  description: string | null;
}

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

  // 실행 확인 모달 대상 (▶ 클릭 시 설정)
  const [runTarget, setRunTarget] = useState<RunTarget | null>(null);

  const recipesQuery = useQuery<RecipeSummary[]>({
    queryKey: ["recipes", "home"],
    queryFn: () => recipesApi.list(),
  });

  const recentQuery = useQuery<CursorPage<ExecutionSummaryView>>({
    queryKey: ["executions", "home"],
    queryFn: () => executionsApi.history({ size: TOP_N }),
  });

  // 서비스(스펙) 목록 — 레시피의 apiSpecId 를 사람이 읽는 서비스명으로 변환(레시피 탭과 캐시 공유).
  const servicesQuery = useServices();
  const serviceNameById = useMemo(() => {
    const map = new Map<number, string>();
    for (const s of servicesQuery.data ?? []) map.set(s.id, s.name);
    return map;
  }, [servicesQuery.data]);

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
              // 서비스 표시명(레시피 탭과 동일 소스). 매핑 없으면 배지/부제 미표시.
              const serviceBadge =
                recipe.apiSpecId != null ? serviceNameById.get(recipe.apiSpecId) : undefined;
              return (
                <div key={recipe.id} className="side-panel__recipe">
                  <div className="side-panel__recipe-top">
                    <span className="side-panel__recipe-name">{recipe.name}</span>
                    {serviceBadge && <span className="badge badge--neutral">{serviceBadge}</span>}
                    <button
                      type="button"
                      className="btn btn--secondary btn--sm side-panel__recipe-run"
                      disabled={busy}
                      title={busy ? "실행 중에는 사용할 수 없어요" : "실행"}
                      onClick={() =>
                        setRunTarget({
                          id: recipe.id,
                          name: recipe.name,
                          // 확인 모달 부제 = 실제 서비스명(레시피 탭과 일관). 없으면 null.
                          serviceName: serviceBadge ?? null,
                          description: recipe.description ?? null,
                        })
                      }
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

      {/* 레시피 실행 확인 모달 (▶ → 확인 → onRunRecipe). 취소 기본 포커스, ESC/배경클릭 취소. */}
      <ConfirmModal
        open={runTarget != null}
        title="실행할까요?"
        description={
          runTarget
            ? [
                [runTarget.name, runTarget.serviceName].filter(Boolean).join(" · "),
                runTarget.description,
              ]
                .filter(Boolean)
                .join("\n")
            : undefined
        }
        confirmLabel="실행"
        cancelLabel="취소"
        initialFocus="cancel"
        onCancel={() => setRunTarget(null)}
        onConfirm={() => {
          const target = runTarget;
          setRunTarget(null);
          if (target) onRunRecipe(target.id, target.name);
        }}
      />
    </div>
  );
}
