// 레시피 탭: 탐색/실행.
// - 서비스 드롭다운: specsApi.list. 탐색 전용 로컬 상태(대화방 서비스와 독립).
// - 검색: 300ms 디바운스 후 useRecipes 로 전달.
// - 필터: [전체][내 레시피][공통] = visibility.
// - 목록: 현재 BE 배열 반환 → 단순 렌더(무한스크롤 골격은 history 참조).
// - [▶] 클릭 → onRunRecipe(recipeId, name). 대화방 처리중이면 비활성.

import { useEffect, useMemo, useState } from "react";
import { formatRelative } from "../shared/format";
import type { PanelContext } from "../types";
import { useRecipes, useServices, type RecipeFilter } from "./useRecipes";

const FILTERS: { key: RecipeFilter; label: string }[] = [
  { key: "all", label: "전체" },
  { key: "private", label: "내 레시피" },
  { key: "common", label: "공통" },
];

export function RecipesView({ conversationStatus, onRunRecipe }: PanelContext) {
  const busy = conversationStatus !== "idle";

  // 탐색 전용 로컬 상태 (대화방 서비스와 독립)
  const [apiSpecId, setApiSpecId] = useState<number | null>(null);
  const [filter, setFilter] = useState<RecipeFilter>("all");
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");

  // 검색어 디바운스 300ms
  useEffect(() => {
    const t = setTimeout(() => setKeyword(searchInput), 300);
    return () => clearTimeout(t);
  }, [searchInput]);

  const servicesQuery = useServices();
  const recipesQuery = useRecipes({ apiSpecId, keyword, filter });
  const recipes = useMemo(() => recipesQuery.data ?? [], [recipesQuery.data]);

  const selectedServiceName = useMemo(
    () => servicesQuery.data?.find((s) => s.id === apiSpecId)?.name ?? null,
    [servicesQuery.data, apiSpecId]
  );

  const emptyMessage = keyword.trim()
    ? `'${keyword.trim()}'에 해당하는 레시피가 없어요.`
    : selectedServiceName
      ? `이 서비스(${selectedServiceName})에 등록된 레시피가 없어요.`
      : "아직 레시피가 없어요. 관리자에게 요청하거나 직접 만들어보세요.";

  return (
    <div className="side-panel__view" role="tabpanel" aria-label="레시피">
      {/* 툴바: 서비스 드롭다운 + 검색 + 필터 */}
      <div className="side-panel__toolbar">
        <select
          className="input"
          aria-label="서비스 선택"
          value={apiSpecId ?? ""}
          onChange={(e) => setApiSpecId(e.target.value === "" ? null : Number(e.target.value))}
        >
          <option value="">전체 서비스</option>
          {(servicesQuery.data ?? []).map((service) => (
            <option key={service.id} value={service.id}>
              {service.name}
            </option>
          ))}
        </select>

        <div className="side-panel__search">
          <span aria-hidden>🔍</span>
          <input
            type="text"
            placeholder="레시피 검색..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
        </div>

        <div className="side-panel__filters" role="group" aria-label="레시피 필터">
          {FILTERS.map((f) => (
            <button
              key={f.key}
              type="button"
              className={`side-panel__filter${filter === f.key ? " is-active" : ""}`}
              aria-pressed={filter === f.key}
              onClick={() => setFilter(f.key)}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      <div className="side-panel__body">
        {recipesQuery.isLoading ? (
          <div className="side-panel__loading">
            <span className="side-panel__spinner" /> 불러오는 중…
          </div>
        ) : recipes.length === 0 ? (
          <div className="side-panel__empty">{emptyMessage}</div>
        ) : (
          recipes.map((recipe) => {
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
                    title={busy ? "대화방이 처리 중입니다" : "실행"}
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
    </div>
  );
}
