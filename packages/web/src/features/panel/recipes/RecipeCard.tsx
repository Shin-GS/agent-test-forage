// 사이드 패널 공통 레시피 카드 (홈 탭 "자주 쓴 레시피" + 레시피 탭 공유).
// - 접힘: 이름 + ⚠️(INVALID) + [▾ 펼치기] + [▶ 실행] / 설명 / 사용 정보
// - 펼침(인라인): 공개범위·서비스·태그(즉시) + 스텝 흐름·입력 변수(지연 조회, recipesApi.detail)
// - [▶] 클릭 시 실행 확인 모달(ConfirmModal)을 카드가 직접 소유한다.
// 정본: docs/design/web/panel.cases.md Case 1/2/2a/2b/2c/6

import { useId, useState } from "react";
import { ConfirmModal } from "../../../components/common/ConfirmModal";
import { AppTooltip } from "../../../components/common/AppTooltip";
import { stepTypeLabel } from "../../../components/recipe/recipeForm";
import type { RecipeStep, RecipeSummary, RecipeVariable } from "../../../api/types";
import { formatRelative } from "../shared/format";
import type { RunRecipeFn } from "../types";
import { useRecipeDetail } from "./useRecipes";

interface Props {
  recipe: RecipeSummary;
  /** 서비스 표시명 (apiSpecId→name 매핑 결과). 없으면 null */
  serviceName: string | null;
  /** 대화방 처리 중 여부 (true 면 [▶] 비활성, 펼침은 허용) */
  busy: boolean;
  onRunRecipe: RunRecipeFn;
}

const VARS_HEAD = 5;

/** INVALID 여부 (validationStatus.code) */
function isInvalid(recipe: RecipeSummary): boolean {
  return (recipe.validationStatus?.code ?? "").toUpperCase() === "INVALID";
}

/** 공개범위 배지 (visibility.code COMMON/PRIVATE) */
function scopeBadge(recipe: RecipeSummary): { icon: string; label: string } {
  const code = (recipe.visibility?.code ?? "").toUpperCase();
  return code === "PRIVATE" ? { icon: "🔒", label: "개인" } : { icon: "🌐", label: "공통" };
}

/** 스텝 표시명 폴백: API 스텝은 label→name, 그 외는 name, 최종 "스텝 N" */
function stepDisplayName(step: RecipeStep, index: number): string {
  const fallback = `스텝 ${index + 1}`;
  if (step.type === "api") {
    return (step.label && step.label.trim()) || (step.name && step.name.trim()) || fallback;
  }
  return (step.name && step.name.trim()) || fallback;
}

/** 입력 변수 요약: "label||key" 조인, VARS_HEAD 초과 시 "…, 외 N개" */
function variablesSummary(variables: RecipeVariable[]): string {
  const names = variables.map((v) => (v.label && v.label.trim()) || v.key);
  if (names.length <= VARS_HEAD) return names.join(", ");
  const head = names.slice(0, VARS_HEAD).join(", ");
  return `${head}, 외 ${names.length - VARS_HEAD}개`;
}

/** 펼침 상세 (지연 조회 영역만 분리 — 스텝/변수) */
function RecipeCardDetail({ recipeId }: { recipeId: number }) {
  const { data, isLoading, isError } = useRecipeDetail(recipeId, true);

  if (isLoading) {
    return (
      <div className="side-panel__recipe-loading" role="status" aria-live="polite">
        <span className="side-panel__spinner" aria-hidden />
        <span>스텝 정보를 불러오는 중…</span>
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="side-panel__recipe-error" role="alert">
        ⚠️ 스텝 정보를 불러오지 못했어요.
      </div>
    );
  }

  const steps = data.steps ?? [];
  const variables = data.variables ?? [];

  return (
    <>
      {steps.length === 0 ? (
        <div className="side-panel__recipe-steps">
          <span className="side-panel__recipe-empty">등록된 스텝이 없어요.</span>
        </div>
      ) : (
        <div className="side-panel__recipe-steps">
          <span className="side-panel__recipe-steps-label">스텝 흐름</span>
          {steps.map((step, i) => (
            <div key={i} className="side-panel__recipe-step">
              <span>
                {i + 1}. {stepDisplayName(step, i)}
              </span>
              <span className="side-panel__recipe-step__type">{stepTypeLabel(step.type)}</span>
            </div>
          ))}
        </div>
      )}

      {variables.length > 0 && (
        <div className="side-panel__recipe-vars-value">
          <span className="side-panel__recipe-vars">입력 변수</span>{" "}
          {variablesSummary(variables)}
        </div>
      )}
    </>
  );
}

export function RecipeCard({ recipe, serviceName, busy, onRunRecipe }: Props) {
  const [expanded, setExpanded] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const detailId = useId();

  const invalid = isInvalid(recipe);
  const scope = scopeBadge(recipe);
  const relative = formatRelative(recipe.lastUsedAt);
  const usageParts = [
    relative && `🕒 ${relative}`,
    recipe.usageCount > 0 && `${recipe.usageCount}회`,
  ]
    .filter(Boolean)
    .join(" · ");

  // 확인 모달 설명: "이름 · 서비스" + 설명 + (INVALID 경고)
  const description = [
    [recipe.name, serviceName].filter(Boolean).join(" · "),
    recipe.description,
    invalid
      ? "스펙 변경으로 유효하지 않을 수 있어요. 실행은 되지만 일부 스텝이 실패할 수 있어요."
      : null,
  ]
    .filter(Boolean)
    .join("\n");

  return (
    <div className="side-panel__recipe">
      <div className="side-panel__recipe-top">
        <span className="side-panel__recipe-name">{recipe.name}</span>

        {invalid && (
          <AppTooltip
            trigger={
              <span
                className="side-panel__recipe-warn"
                tabIndex={0}
                role="img"
                aria-label="스펙 변경으로 필수 필드가 무효화됨"
              >
                ⚠️
              </span>
            }
          >
            스펙 변경으로 필수 필드가 무효화됨
          </AppTooltip>
        )}

        <button
          type="button"
          className="side-panel__recipe-toggle"
          aria-expanded={expanded}
          aria-controls={detailId}
          aria-label={expanded ? `${recipe.name} 접기` : `${recipe.name} 펼치기`}
          onClick={() => setExpanded((v) => !v)}
        >
          {expanded ? "▴" : "▾"}
        </button>

        <button
          type="button"
          className="btn btn--secondary btn--sm side-panel__recipe-run"
          disabled={busy}
          aria-label={`${recipe.name} 실행`}
          title={busy ? "실행 중에는 사용할 수 없어요" : "실행"}
          onClick={() => setConfirmOpen(true)}
        >
          ▶
        </button>
      </div>

      {recipe.description && (
        <span className="side-panel__recipe-desc">{recipe.description}</span>
      )}

      {usageParts && <span className="side-panel__recipe-meta">{usageParts}</span>}

      {expanded && (
        <div className="side-panel__recipe-detail" id={detailId}>
          {/* 즉시 표시: 공개범위 + 서비스명 */}
          <div className="side-panel__recipe-scope">
            <span className="badge badge--neutral">
              {scope.icon} {scope.label}
            </span>
            {serviceName && <span>{serviceName}</span>}
          </div>

          {/* 즉시 표시: 태그 칩 */}
          {recipe.tags.length > 0 && (
            <div className="side-panel__recipe-tags">
              {recipe.tags.map((tag) => (
                <span key={tag} className="side-panel__recipe-tag">
                  #{tag}
                </span>
              ))}
            </div>
          )}

          {/* 지연 조회: 스텝 흐름 + 입력 변수 */}
          <RecipeCardDetail recipeId={recipe.id} />
        </div>
      )}

      {/* 실행 확인 모달 (▶ → 확인 → onRunRecipe). 취소 기본 포커스, ESC/배경클릭 취소. */}
      <ConfirmModal
        open={confirmOpen}
        title="실행할까요?"
        description={description || undefined}
        confirmLabel="실행"
        cancelLabel="취소"
        initialFocus="cancel"
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => {
          setConfirmOpen(false);
          onRunRecipe(recipe.id, recipe.name);
        }}
      />
    </div>
  );
}
