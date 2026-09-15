// 레시피 목록 페이지 (라우트 "/recipes").
// 디자인 명세: docs/design/web/recipe-editor.html Case 1/7~10, recipe-editor.cases.md.
// - 다중 선택 필터(서비스/범위/태그) 체크박스 드롭다운, "빈 선택=전체", 선택 칩 + [필터 초기화].
// - 정렬(최근 사용/사용 많은/이름/최근 수정) + 오름/내림 토글.
// - 행: 범위 배지(🌐공통/🔒개인), 이름+설명 1줄, INVALID ⚠️, 상대시간, hover 액션(편집/복제/삭제).
// - 권한 게이팅: canEdit=false → 편집/삭제 숨김(복제만). 결과 카운트 "총 N개".
// - 삭제 ConfirmModal(window.confirm 금지), 복제 → 성공 토스트 후 편집 페이지 진입.
// - URL 쿼리 동기화(nuqs useQueryStates): 검색/필터/정렬 복원. 검색 디바운스(throttle) 300ms.
// - < 1024px 카드형 폴백. 빈 상태(레시피 0개 / 필터 결과 없음) 2종.
//
// 데이터: GET /recipes (recipesApi.list), GET /specs (specsApi.list) 를 React Query 로 조회.

import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQueryStates, parseAsString, parseAsStringEnum, parseAsArrayOf } from "nuqs";
import { parseAsSearch, SEARCH_OPTIONS } from "../lib/urlFilters";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, recipesApi, specsApi } from "../api";
import type { RecipeSummary, SpecListItem } from "../api/types";
import type { RecipeSort, SortDirection } from "../api/recipes";
import { FilterDropdown, type FilterOption } from "../components/recipe/FilterDropdown";
import { ConfirmModal } from "../components/common/ConfirmModal";
import { PageShell } from "../components/layout/PageShell";
import { PageToolbar } from "../components/layout/PageToolbar";
import { useListNavigation } from "../hooks/useListNavigation";
import { useToastStore } from "../store/toastStore";
import { useMediaQuery } from "../hooks/useMediaQuery";

const SORT_OPTIONS: { value: RecipeSort; label: string }[] = [
  { value: "recent", label: "최근 사용순" },
  { value: "usage", label: "사용 많은순" },
  { value: "name", label: "이름순" },
  { value: "updated", label: "최근 수정순" },
];

const VISIBILITY_OPTIONS: FilterOption[] = [
  { value: "COMMON", label: "🌐 공통" },
  { value: "PRIVATE", label: "🔒 개인" },
];

/** ISO → 상대시간(간단). 없으면 "미사용" */
function relativeTime(value: string | null): string {
  if (!value) return "미사용";
  const parsed = Date.parse(value);
  if (Number.isNaN(parsed)) return value.slice(0, 10);
  const diffMs = Date.now() - parsed;
  const min = Math.floor(diffMs / 60000);
  if (min < 1) return "방금 전";
  if (min < 60) return `${min}분 전`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}시간 전`;
  const day = Math.floor(hour / 24);
  if (day < 7) return `${day}일 전`;
  return new Date(parsed).toISOString().slice(0, 10);
}

function useSpecNameMap(specs: SpecListItem[] | undefined): Map<number, string> {
  return useMemo(() => {
    const map = new Map<number, string>();
    for (const spec of specs ?? []) map.set(spec.id, spec.name);
    return map;
  }, [specs]);
}

function isVisibilityCommon(recipe: RecipeSummary): boolean {
  return (recipe.visibility?.code ?? "").toUpperCase() === "COMMON";
}

function isInvalid(recipe: RecipeSummary): boolean {
  return (recipe.validationStatus?.code ?? "").toUpperCase() === "INVALID";
}

export function RecipeListPage() {
  const navigate = useNavigate();
  // 상세(편집) 진입 시 현재 목록 URL(필터 쿼리 포함)을 전달 → 편집에서 [← 목록으로] 시 필터 유지 복귀.
  const navigateToDetail = useListNavigation();
  const queryClient = useQueryClient();
  const showToast = useToastStore((s) => s.show);
  const isCompact = useMediaQuery("(max-width: 1023px)");

  // --- URL 상태 (nuqs): 검색/필터/정렬을 URL 쿼리에 동기화 (page-layout.md 목록 상태와 URL) ---
  // 필터 변경은 nuqs 기본 history=replace(오염 방지). 검색어(q)만 throttleMs 로 디바운스 커밋.
  // enum(sort/dir)은 화이트리스트 파싱으로 조작 URL 을 기본값으로 안전 흡수. 기본값은 clearOnDefault 로 URL 미기록.
  const [filters, setFilters] = useQueryStates({
    q: parseAsSearch.withDefault("").withOptions(SEARCH_OPTIONS),
    spec: parseAsArrayOf(parseAsString).withDefault([]),
    visibility: parseAsArrayOf(parseAsString).withDefault([]),
    tag: parseAsArrayOf(parseAsString).withDefault([]),
    sort: parseAsStringEnum<RecipeSort>(["recent", "usage", "name", "updated"])
      .withDefault("recent")
      .withOptions({ clearOnDefault: true }),
    dir: parseAsStringEnum<SortDirection>(["asc", "desc"])
      .withDefault("desc")
      .withOptions({ clearOnDefault: true }),
  });

  // 하위 코드 호환용 파생값 (기존 변수명 유지 → 렌더/쿼리 로직 변경 최소화)
  const urlKeyword = filters.q;
  const selectedSpecIds = filters.spec;
  const selectedVisibility = filters.visibility;
  const selectedTags = filters.tag;
  const sort = filters.sort;
  const direction = filters.dir;
  // 검색 입력값: nuqs state 는 즉시 반영되고 URL 쓰기만 throttle 되므로 입력이 매끄럽다.
  const keywordInput = filters.q;
  const setKeywordInput = (value: string) => void setFilters({ q: value });

  function setMultiParam(key: "spec" | "visibility" | "tag", values: string[]) {
    void setFilters({ [key]: values });
  }

  const { data: specs } = useQuery({ queryKey: ["specs"], queryFn: () => specsApi.list() });
  const specNameMap = useSpecNameMap(specs);

  const specOptions: FilterOption[] = useMemo(
    () => (specs ?? []).map((s) => ({ value: String(s.id), label: s.name })),
    [specs],
  );

  const listParams = useMemo(
    () => ({
      apiSpecId: selectedSpecIds.map(Number).filter((n) => Number.isFinite(n)),
      visibility: selectedVisibility,
      tag: selectedTags,
      keyword: urlKeyword.trim() || undefined,
      sort,
      direction,
    }),
    [selectedSpecIds, selectedVisibility, selectedTags, urlKeyword, sort, direction],
  );

  const {
    data: recipes,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ["recipes", listParams],
    queryFn: () => recipesApi.list(listParams),
  });

  // 태그 옵션: 현재 목록에서 수집(빈 상태 대비 selectedTags 도 포함)
  const tagOptions: FilterOption[] = useMemo(() => {
    const set = new Set<string>(selectedTags);
    for (const r of recipes ?? []) for (const t of r.tags) set.add(t);
    return [...set].sort().map((t) => ({ value: t, label: t }));
  }, [recipes, selectedTags]);

  // --- 삭제 (ConfirmModal) ---
  const [deleteTarget, setDeleteTarget] = useState<RecipeSummary | null>(null);
  const deleteMutation = useMutation({
    mutationFn: (id: number) => recipesApi.remove(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["recipes"] });
      showToast("레시피를 삭제했습니다", "success");
      setDeleteTarget(null);
    },
    onError: (err) => {
      setDeleteTarget(null);
      showToast(errorMessage(err, "삭제에 실패했습니다"), "error");
    },
  });

  // --- 복제 → 성공 시 편집 페이지 진입 ---
  const duplicateMutation = useMutation({
    mutationFn: (id: number) => recipesApi.duplicate(id),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ["recipes"] });
      showToast(`'${created.name}' 개인 사본을 만들었습니다`, "success");
      navigateToDetail(`/recipes/${created.id}/edit`);
    },
    onError: (err) => showToast(errorMessage(err, "복제에 실패했습니다"), "error"),
  });

  // --- 필터 칩/초기화 ---
  const activeFilterCount = selectedSpecIds.length + selectedVisibility.length + selectedTags.length;

  function clearAllFilters() {
    // 필터(서비스/범위/태그)만 초기화. 검색어(q)는 기존 동작대로 유지.
    void setFilters({ spec: [], visibility: [], tag: [] });
  }

  function removeSpec(id: string) {
    setMultiParam("spec", selectedSpecIds.filter((v) => v !== id));
  }
  function removeVisibility(v: string) {
    setMultiParam("visibility", selectedVisibility.filter((x) => x !== v));
  }
  function removeTag(t: string) {
    setMultiParam("tag", selectedTags.filter((x) => x !== t));
  }

  function toggleDirection() {
    void setFilters({ dir: direction === "desc" ? "asc" : "desc" });
  }

  const hasAnyRecipe = (recipes?.length ?? 0) > 0;
  // 필터/검색이 걸려 있는데 결과가 0인지 판정
  const hasActiveQuery = activeFilterCount > 0 || urlKeyword.trim().length > 0;

  function visibilityLabel(v: string): string {
    return VISIBILITY_OPTIONS.find((o) => o.value === v)?.label ?? v;
  }

  return (
    <PageShell
      title="레시피 관리"
      toolbar={
        <PageToolbar
          actions={
            <button type="button" className="btn btn--primary" onClick={() => navigateToDetail("/recipes/new")}>
              + 레시피 만들기
            </button>
          }
        >
          {/* 검색 + 필터 드롭다운 + 정렬 */}
          <input
            className="input"
            type="text"
            placeholder="🔍 이름/설명 검색..."
            aria-label="레시피 검색"
            style={{ maxWidth: "260px" }}
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
          />

          <FilterDropdown
            label="서비스"
            options={specOptions}
            selected={selectedSpecIds}
            onChange={(next) => setMultiParam("spec", next)}
          />
          <FilterDropdown
            label="범위"
            options={VISIBILITY_OPTIONS}
            selected={selectedVisibility}
            onChange={(next) => setMultiParam("visibility", next)}
          />
          <FilterDropdown
            label="태그"
            options={tagOptions}
            selected={selectedTags}
            onChange={(next) => setMultiParam("tag", next)}
          />

          <select
            className="input"
            aria-label="정렬 기준"
            style={{ maxWidth: "150px" }}
            value={sort}
            onChange={(e) => void setFilters({ sort: e.target.value as RecipeSort })}
          >
            {SORT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="sort-dir"
            aria-label={`정렬 방향: ${direction === "desc" ? "내림차순" : "오름차순"}`}
            title={direction === "desc" ? "내림차순" : "오름차순"}
            onClick={toggleDirection}
          >
            {direction === "desc" ? "▼" : "▲"}
          </button>
        </PageToolbar>
      }
    >
      <div className="page-body">
        {/* 필터 칩 요약 */}
        {activeFilterCount > 0 && (
          <div className="filter-summary">
              <span className="filter-summary__label">활성 필터 {activeFilterCount}개 ·</span>
              {selectedSpecIds.map((id) => (
                <span key={`spec-${id}`} className="filter-chip">
                  {specNameMap.get(Number(id)) ?? `#${id}`}
                  <button
                    type="button"
                    className="filter-chip__remove"
                    aria-label={`${specNameMap.get(Number(id)) ?? id} 필터 제거`}
                    onClick={() => removeSpec(id)}
                  >
                    ✕
                  </button>
                </span>
              ))}
              {selectedVisibility.map((v) => (
                <span key={`vis-${v}`} className="filter-chip">
                  {visibilityLabel(v)}
                  <button
                    type="button"
                    className="filter-chip__remove"
                    aria-label={`${visibilityLabel(v)} 필터 제거`}
                    onClick={() => removeVisibility(v)}
                  >
                    ✕
                  </button>
                </span>
              ))}
              {selectedTags.map((t) => (
                <span key={`tag-${t}`} className="filter-chip">
                  {t}
                  <button
                    type="button"
                    className="filter-chip__remove"
                    aria-label={`${t} 필터 제거`}
                    onClick={() => removeTag(t)}
                  >
                    ✕
                  </button>
                </span>
              ))}
              <button type="button" className="btn btn--ghost btn--sm" onClick={clearAllFilters}>
                필터 초기화
              </button>
              <span className="list-toolbar__spacer" />
              {recipes && <span className="list-count">총 {recipes.length}개</span>}
            </div>
          )}

        {isLoading && (
          <div className="recipe-state" role="status" aria-live="polite">
            레시피를 불러오는 중입니다…
          </div>
        )}

        {isError && (
          <div className="recipe-state recipe-state--error" role="alert">
            <div>레시피를 불러오지 못했습니다{error instanceof Error ? `: ${error.message}` : ""}</div>
            <button type="button" className="btn btn--secondary btn--sm" onClick={() => void refetch()}>
              다시 시도
            </button>
          </div>
        )}

        {/* 빈 상태: 레시피 0개 vs 필터 결과 없음 */}
        {recipes && !hasAnyRecipe && !hasActiveQuery && (
          <div className="empty-state">
            <div className="empty-state__icon">📋</div>
            <div className="empty-state__title">아직 레시피가 없습니다</div>
            <div className="empty-state__desc">
              첫 레시피를 만들어 워크플로우를 등록해보세요. 채팅에서 자연어로 실행할 수 있습니다.
            </div>
            <button type="button" className="btn btn--primary" onClick={() => navigateToDetail("/recipes/new")}>
              + 레시피 만들기
            </button>
          </div>
        )}

        {recipes && !hasAnyRecipe && hasActiveQuery && (
          <div className="empty-state">
            <div className="empty-state__icon">🔍</div>
            <div className="empty-state__title">조건에 맞는 레시피가 없습니다</div>
            <div className="empty-state__desc">필터를 완화하거나 초기화해보세요.</div>
            <button type="button" className="btn btn--secondary" onClick={clearAllFilters}>
              필터 초기화
            </button>
          </div>
        )}

        {/* 결과 카운트(필터 없을 때도 표시) */}
        {recipes && hasAnyRecipe && activeFilterCount === 0 && (
          <div className="list-count">총 {recipes.length}개</div>
        )}

        {/* 목록: Desktop 테이블 / Tablet 이하 카드 폴백 */}
        {recipes && hasAnyRecipe && !isCompact && (
          <table className="data-table">
            <thead>
              <tr>
                <th>레시피</th>
                <th>서비스</th>
                <th>범위</th>
                <th>최근 사용</th>
                <th style={{ width: "120px" }}>액션</th>
              </tr>
            </thead>
            <tbody>
              {recipes.map((recipe) => (
                <tr
                  key={recipe.id}
                  onClick={() => navigateToDetail(`/recipes/${recipe.id}/edit`)}
                  style={{ cursor: "pointer" }}
                >
                  <td>
                    <div className="recipe-cell__name">
                      {recipe.name}
                      {isInvalid(recipe) && (
                        <span
                          className="recipe-cell__warn tooltip"
                          tabIndex={0}
                          aria-label="유효성 경고"
                        >
                          ⚠️
                          <span className="tooltip__content">스펙 변경으로 필수 필드가 무효화됨</span>
                        </span>
                      )}
                    </div>
                    <div
                      className="recipe-cell__desc"
                      style={recipe.description ? undefined : { fontStyle: "italic" }}
                    >
                      {recipe.description || "(설명 없음)"}
                    </div>
                  </td>
                  <td>
                    {recipe.apiSpecId != null ? (
                      <span className="badge badge--neutral">
                        {specNameMap.get(recipe.apiSpecId) ?? `#${recipe.apiSpecId}`}
                      </span>
                    ) : (
                      <span style={{ color: "var(--color-text-tertiary)" }}>-</span>
                    )}
                  </td>
                  <td>
                    {isVisibilityCommon(recipe) ? (
                      <span className="badge badge--info">🌐 공통</span>
                    ) : (
                      <span className="badge badge--neutral">🔒 개인</span>
                    )}
                  </td>
                  <td style={{ color: "var(--color-text-tertiary)", fontSize: "var(--font-size-xs)" }}>
                    {relativeTime(recipe.lastUsedAt)}
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    <div className="row-actions data-table__actions">
                      {recipe.canEdit && (
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm"
                          title="편집"
                          aria-label={`${recipe.name} 편집`}
                          onClick={() => navigateToDetail(`/recipes/${recipe.id}/edit`)}
                        >
                          ✏️
                        </button>
                      )}
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        title="복제"
                        aria-label={`${recipe.name} 복제`}
                        disabled={duplicateMutation.isPending}
                        onClick={() => duplicateMutation.mutate(recipe.id)}
                      >
                        ⧉
                      </button>
                      {recipe.canEdit && (
                        <button
                          type="button"
                          className="btn btn--ghost btn--sm"
                          style={{ color: "var(--color-error)" }}
                          title="삭제"
                          aria-label={`${recipe.name} 삭제`}
                          onClick={() => setDeleteTarget(recipe)}
                        >
                          🗑️
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {/* 카드 폴백 (Tablet 이하) */}
        {recipes && hasAnyRecipe && isCompact && (
          <div className="recipe-cards">
            {recipes.map((recipe) => (
              <div
                key={recipe.id}
                className="recipe-card"
                role="button"
                tabIndex={0}
                onClick={() => navigateToDetail(`/recipes/${recipe.id}/edit`)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") navigateToDetail(`/recipes/${recipe.id}/edit`);
                }}
              >
                <div className="recipe-card__top">
                  {isVisibilityCommon(recipe) ? (
                    <span className="badge badge--info">🌐 공통</span>
                  ) : (
                    <span className="badge badge--neutral">🔒 개인</span>
                  )}
                  <span className="recipe-cell__name" style={{ flex: 1 }}>
                    {recipe.name}
                    {isInvalid(recipe) && (
                      <span className="recipe-cell__warn" aria-label="유효성 경고">
                        {" "}
                        ⚠️
                      </span>
                    )}
                  </span>
                </div>
                <div className="recipe-cell__desc" style={{ maxWidth: "none", whiteSpace: "normal" }}>
                  {recipe.description || "(설명 없음)"}
                </div>
                <div className="recipe-card__meta">
                  {recipe.apiSpecId != null && (
                    <span className="badge badge--neutral">
                      {specNameMap.get(recipe.apiSpecId) ?? `#${recipe.apiSpecId}`}
                    </span>
                  )}
                  {recipe.tags.map((t) => (
                    <span key={t} className="tag-chip">
                      {t}
                    </span>
                  ))}
                  <span>· {relativeTime(recipe.lastUsedAt)}</span>
                </div>
                <div className="recipe-card__actions" onClick={(e) => e.stopPropagation()}>
                  {recipe.canEdit && (
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm"
                      aria-label={`${recipe.name} 편집`}
                      onClick={() => navigateToDetail(`/recipes/${recipe.id}/edit`)}
                    >
                      ✏️ 편집
                    </button>
                  )}
                  <button
                    type="button"
                    className="btn btn--ghost btn--sm"
                    aria-label={`${recipe.name} 복제`}
                    disabled={duplicateMutation.isPending}
                    onClick={() => duplicateMutation.mutate(recipe.id)}
                  >
                    ⧉ 복제
                  </button>
                  {recipe.canEdit && (
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm"
                      style={{ color: "var(--color-error)" }}
                      aria-label={`${recipe.name} 삭제`}
                      onClick={() => setDeleteTarget(recipe)}
                    >
                      🗑️ 삭제
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 삭제 확인 모달 */}
      <ConfirmModal
        open={deleteTarget != null}
        title="레시피 삭제"
        description={
          deleteTarget
            ? `'${deleteTarget.name}' 레시피를 삭제할까요? 실행 히스토리는 유지되지만 레시피 정의는 되돌릴 수 없습니다.`
            : undefined
        }
        confirmLabel="삭제"
        danger
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
        onCancel={() => setDeleteTarget(null)}
      />
    </PageShell>
  );
}

/** ApiError 면 서버 메시지, 아니면 기본 메시지 */
function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return fallback;
}
