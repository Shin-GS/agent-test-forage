// 레시피 탭: 탐색/실행.
// - 서비스 드롭다운: specsApi.list. 탐색 전용 로컬 상태(대화방 서비스와 독립).
// - 검색: 300ms 디바운스 후 useRecipes 로 전달.
// - 필터: [전체][내 레시피][공통] = visibility.
// - 목록: 현재 BE 배열 반환 → 단순 렌더(무한스크롤 골격은 history 참조).
// - [▶] 클릭 → onRunRecipe(recipeId, name). 대화방 처리중이면 비활성.

import { useEffect, useMemo, useState } from "react";
import type { PanelContext } from "../types";
import { usePanelStore } from "../panelStore";
import { RecipeCard } from "./RecipeCard";
import { useRecipes, useServices, type RecipeFilter } from "./useRecipes";

const FILTERS: { key: RecipeFilter; label: string }[] = [
  { key: "all", label: "전체" },
  { key: "private", label: "내 레시피" },
  { key: "common", label: "공통" },
];

export function RecipesView({ conversationStatus, onRunRecipe }: PanelContext) {
  const busy = conversationStatus !== "idle";

  // 서비스 필터(선택 도메인): panelStore 로 승격. 대화방 대상 서비스가 바뀌면 SidePanel 이
  // syncRecipeFilterToService 로 이 값을 맞춰 두고(단방향), 사용자가 드롭다운으로 직접 바꾸면
  // setRecipeFilterApiSpecId 로 갱신한다(이후 탐색 자유). 기획 panel/overview.md "레시피 탭 필터와의 관계".
  const apiSpecId = usePanelStore((s) => s.recipeFilterApiSpecId);
  const setApiSpecId = usePanelStore((s) => s.setRecipeFilterApiSpecId);
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

  const selectedService = useMemo(
    () => servicesQuery.data?.find((s) => s.id === apiSpecId) ?? null,
    [servicesQuery.data, apiSpecId]
  );
  const selectedServiceName = selectedService?.name ?? null;

  // 레시피 항목 서비스 배지용 id→name 맵
  const serviceNameById = useMemo(() => {
    const map = new Map<number, string>();
    for (const s of servicesQuery.data ?? []) map.set(s.id, s.name);
    return map;
  }, [servicesQuery.data]);

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
              {/* 비개발자 배려: 설명이 있으면 "설명 (기술명)", 없으면 기술명만 */}
              {service.serviceDescription
                ? `${service.serviceDescription} (${service.name})`
                : service.name}
            </option>
          ))}
        </select>

        {/* 선택된 서비스 부가 정보 (설명 + 도메인) */}
        {selectedService && (selectedService.serviceDescription || selectedService.serviceDomain) && (
          <div className="side-panel__service-info">
            {selectedService.serviceDescription && (
              <span className="side-panel__service-desc">{selectedService.serviceDescription}</span>
            )}
            {selectedService.serviceDomain && (
              <span className="side-panel__service-domain">{selectedService.serviceDomain}</span>
            )}
          </div>
        )}

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
          recipes.map((recipe) => (
            <RecipeCard
              key={recipe.id}
              recipe={recipe}
              serviceName={
                recipe.apiSpecId != null
                  ? (serviceNameById.get(recipe.apiSpecId) ?? null)
                  : null
              }
              busy={busy}
              onRunRecipe={onRunRecipe}
            />
          ))
        )}
      </div>
    </div>
  );
}
