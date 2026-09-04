// 레시피 목록 페이지 (라우트 "/recipes").
// 디자인 명세: docs/design/web/recipe-editor.html Case1 기준.
// - 상단: 제목 + [+ 레시피 만들기]
// - 필터: 검색어 + 서비스 드롭다운 + 공개범위(전체/공통/개인)
// - 테이블: 레시피명 / 서비스 / 범위 / 태그 / 최근 사용(lastUsedAt) / 액션(편집·삭제)
// - 행 클릭 → 편집 페이지, 삭제는 확인 후 mutation → 목록 invalidate
//
// 데이터: GET /recipes (recipesApi.list), GET /specs (specsApi.list) 를 React Query 로 조회.

import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { recipesApi, specsApi } from "../api";
import type { RecipeSummary, SpecListItem } from "../api/types";

/** 스펙 id → 이름 맵 */
function useSpecNameMap(specs: SpecListItem[] | undefined): Map<number, string> {
  return useMemo(() => {
    const map = new Map<number, string>();
    for (const spec of specs ?? []) map.set(spec.id, spec.name);
    return map;
  }, [specs]);
}

function formatDate(value: string | null): string {
  if (!value) return "-";
  // ISO → YYYY-MM-DD (실패 시 원본 앞 10자)
  const parsed = Date.parse(value);
  if (Number.isNaN(parsed)) return value.slice(0, 10);
  return new Date(parsed).toISOString().slice(0, 10);
}

export function RecipeListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [keyword, setKeyword] = useState("");
  const [apiSpecId, setApiSpecId] = useState<number | "">("");
  const [visibility, setVisibility] = useState<string>("");

  const { data: specs } = useQuery({
    queryKey: ["specs"],
    queryFn: () => specsApi.list(),
  });
  const specNameMap = useSpecNameMap(specs);

  const listParams = {
    apiSpecId: apiSpecId === "" ? undefined : apiSpecId,
    keyword: keyword.trim() || undefined,
    visibility: visibility || undefined,
  };

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

  const deleteMutation = useMutation({
    mutationFn: (id: number) => recipesApi.remove(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["recipes"] });
    },
  });

  function handleDelete(recipe: RecipeSummary) {
    const ok = window.confirm(`'${recipe.name}' 레시피를 삭제할까요? 되돌릴 수 없습니다.`);
    if (ok) deleteMutation.mutate(recipe.id);
  }

  return (
    <div className="recipe-page">
      <div className="page-header">
        <span className="page-header__title">레시피 관리</span>
        <div className="page-header__actions">
          <button
            type="button"
            className="btn btn--primary"
            onClick={() => navigate("/recipes/new")}
          >
            + 레시피 만들기
          </button>
        </div>
      </div>

      <div className="page-body">
        {/* 필터 */}
        <div className="list-filters">
          <input
            className="input"
            type="text"
            placeholder="🔍 레시피 검색..."
            aria-label="레시피 검색"
            style={{ maxWidth: "300px" }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <select
            className="input"
            aria-label="서비스 필터"
            style={{ maxWidth: "180px" }}
            value={apiSpecId}
            onChange={(e) => setApiSpecId(e.target.value === "" ? "" : Number(e.target.value))}
          >
            <option value="">전체 서비스</option>
            {(specs ?? []).map((spec) => (
              <option key={spec.id} value={spec.id}>
                {spec.name}
              </option>
            ))}
          </select>
          <select
            className="input"
            aria-label="공개 범위 필터"
            style={{ maxWidth: "120px" }}
            value={visibility}
            onChange={(e) => setVisibility(e.target.value)}
          >
            <option value="">전체</option>
            <option value="COMMON">공통</option>
            <option value="PRIVATE">개인</option>
          </select>
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

        {recipes && recipes.length === 0 && (
          <div className="recipe-state">조건에 맞는 레시피가 없습니다.</div>
        )}

        {recipes && recipes.length > 0 && (
          <table className="data-table">
            <thead>
              <tr>
                <th>레시피명</th>
                <th>서비스</th>
                <th>범위</th>
                <th>태그</th>
                <th>최근 사용</th>
                <th>액션</th>
              </tr>
            </thead>
            <tbody>
              {recipes.map((recipe) => (
                <tr
                  key={recipe.id}
                  onClick={() => navigate(`/recipes/${recipe.id}/edit`)}
                  style={{ cursor: "pointer" }}
                >
                  <td style={{ fontWeight: "var(--font-weight-medium)" }}>{recipe.name}</td>
                  <td>
                    {recipe.apiSpecId != null ? (
                      <span className="badge badge--neutral">
                        {specNameMap.get(recipe.apiSpecId) ?? `#${recipe.apiSpecId}`}
                      </span>
                    ) : (
                      <span style={{ color: "var(--color-text-tertiary)" }}>-</span>
                    )}
                  </td>
                  <td>{recipe.visibility?.description ?? recipe.visibility?.code ?? "-"}</td>
                  <td>
                    {recipe.tags.length > 0
                      ? recipe.tags.map((tag) => (
                          <span key={tag} className="tag-chip">
                            {tag}
                          </span>
                        ))
                      : "-"}
                  </td>
                  <td style={{ color: "var(--color-text-tertiary)" }}>
                    {formatDate(recipe.lastUsedAt)}
                  </td>
                  <td className="data-table__actions" onClick={(e) => e.stopPropagation()}>
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm"
                      title="편집"
                      aria-label={`${recipe.name} 편집`}
                      onClick={() => navigate(`/recipes/${recipe.id}/edit`)}
                    >
                      ✏️
                    </button>
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm"
                      style={{ color: "var(--color-error)" }}
                      title="삭제"
                      aria-label={`${recipe.name} 삭제`}
                      disabled={deleteMutation.isPending}
                      onClick={() => handleDelete(recipe)}
                    >
                      🗑️
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
