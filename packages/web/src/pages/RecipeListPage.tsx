// 레시피 목록 페이지 (라우트 "/recipes").
// 디자인 명세: docs/design/web/recipe-editor.html Case 1/7~10, recipe-editor.cases.md.
// - 다중 선택 필터(서비스/범위/태그) 체크박스 드롭다운, "빈 선택=전체", 선택 칩 + [필터 초기화].
// - 정렬(최근 사용/사용 많은/이름/최근 수정) + 오름/내림 토글.
// - 행: 범위 배지(🌐공통/🔒개인), 이름+설명 1줄, INVALID ⚠️, 상대시간, hover 액션(편집/복제/삭제).
// - 권한 게이팅: canEdit=false → 편집/삭제 숨김(복제만). 결과 카운트 "총 N개".
// - 삭제 ConfirmModal(window.confirm 금지), 복제 → 성공 토스트 후 편집 페이지 진입.
// - URL 쿼리 동기화(useSearchParams): 검색/필터/정렬 복원. 검색 디바운스 300ms.
// - < 1024px 카드형 폴백. 빈 상태(레시피 0개 / 필터 결과 없음) 2종.
//
// 데이터: GET /recipes (recipesApi.list), GET /specs (specsApi.list) 를 React Query 로 조회.

import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, recipesApi, specsApi } from "../api";
import type { RecipeSummary, SpecListItem } from "../api/types";
import type { RecipeSort, SortDirection } from "../api/recipes";
import { FilterDropdown, type FilterOption } from "../components/recipe/FilterDropdown";
import { ConfirmModal } from "../components/common/ConfirmModal";
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
  const queryClient = useQueryClient();
  const showToast = useToastStore((s) => s.show);
  const isCompact = useMediaQuery("(max-width: 1023px)");

  const [searchParams, setSearchParams] = useSearchParams();

  // --- URL → 상태 파싱 ---
  const urlKeyword = searchParams.get("q") ?? "";
  const selectedSpecIds = searchParams.getAll("spec");
  const selectedVisibility = searchParams.getAll("visibility");
  const selectedTags = searchParams.getAll("tag");
  const sort = (searchParams.get("sort") as RecipeSort) || "recent";
  const direction = (searchParams.get("dir") as SortDirection) || "desc";

  // 검색 입력은 로컬 상태(단일 입력 소스) + 디바운스 후 URL 커밋.
  // URL(urlKeyword)은 "커밋 결과"이자 외부 변경(뒤로가기/새로고침) 감지용.
  const [keywordInput, setKeywordInput] = useState(urlKeyword);
  // 마지막으로 URL 에 커밋한(또는 URL 에서 동기화한) 값. 외부 변경 판별 기준.
  const committedKeywordRef = useRef(urlKeyword);

  // 외부에서 urlKeyword 가 바뀌면(뒤로가기 등, 우리가 커밋한 값과 다를 때만) 입력값을 동기화.
  // 타이핑으로 우리가 방금 커밋한 값은 여기서 무시되어 입력값이 튀지 않는다.
  useEffect(() => {
    if (urlKeyword === committedKeywordRef.current) return;
    committedKeywordRef.current = urlKeyword;
    setKeywordInput(urlKeyword);
  }, [urlKeyword]);

  // keywordInput 변경만을 기준으로 디바운스 커밋. committedKeywordRef 로 "이미 반영된 값인지"를
  // 판별해 불필요한 커밋을 건너뛴다. setSearchParams 는 안정적인 참조라 deps 로 넣어도 안전(억제 불필요).
  useEffect(() => {
    const trimmed = keywordInput.trim();
    if (trimmed === committedKeywordRef.current) return;
    const handle = setTimeout(() => {
      committedKeywordRef.current = trimmed;
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (trimmed) next.set("q", trimmed);
          else next.delete("q");
          return next;
        },
        { replace: true },
      );
    }, 300);
    return () => clearTimeout(handle);
  }, [keywordInput, setSearchParams]);

  /** searchParams 를 함수형으로 갱신 (replace 로 히스토리 오염 방지) */
  function updateParams(mutate: (params: URLSearchParams) => void) {
    const next = new URLSearchParams(searchParams);
    mutate(next);
    setSearchParams(next, { replace: true });
  }

  function setMultiParam(key: string, values: string[]) {
    updateParams((params) => {
      params.delete(key);
      for (const v of values) params.append(key, v);
    });
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
      navigate(`/recipes/${created.id}/edit`);
    },
    onError: (err) => showToast(errorMessage(err, "복제에 실패했습니다"), "error"),
  });

  // --- 필터 칩/초기화 ---
  const activeFilterCount = selectedSpecIds.length + selectedVisibility.length + selectedTags.length;

  function clearAllFilters() {
    updateParams((params) => {
      params.delete("spec");
      params.delete("visibility");
      params.delete("tag");
    });
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
    updateParams((params) => params.set("dir", direction === "desc" ? "asc" : "desc"));
  }

  const hasAnyRecipe = (recipes?.length ?? 0) > 0;
  // 필터/검색이 걸려 있는데 결과가 0인지 판정
  const hasActiveQuery = activeFilterCount > 0 || urlKeyword.trim().length > 0;

  function visibilityLabel(v: string): string {
    return VISIBILITY_OPTIONS.find((o) => o.value === v)?.label ?? v;
  }

  return (
    <div className="recipe-page">
      <div className="page-header">
        <span className="page-header__title">레시피 관리</span>
        <div className="page-header__actions">
          <button type="button" className="btn btn--primary" onClick={() => navigate("/recipes/new")}>
            + 레시피 만들기
          </button>
        </div>
      </div>

      <div className="page-body">
        {/* 툴바: 검색 + 필터 드롭다운 + 정렬 */}
        <div className="list-toolbar">
          <div className="list-toolbar__row">
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

            <div className="list-toolbar__spacer" />

            <select
              className="input"
              aria-label="정렬 기준"
              style={{ maxWidth: "150px" }}
              value={sort}
              onChange={(e) => updateParams((p) => p.set("sort", e.target.value))}
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
          </div>

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
        </div>

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
            <button type="button" className="btn btn--primary" onClick={() => navigate("/recipes/new")}>
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
                  onClick={() => navigate(`/recipes/${recipe.id}/edit`)}
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
                          onClick={() => navigate(`/recipes/${recipe.id}/edit`)}
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
                onClick={() => navigate(`/recipes/${recipe.id}/edit`)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") navigate(`/recipes/${recipe.id}/edit`);
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
                      onClick={() => navigate(`/recipes/${recipe.id}/edit`)}
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
    </div>
  );
}

/** ApiError 면 서버 메시지, 아니면 기본 메시지 */
function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error) return err.message;
  return fallback;
}
