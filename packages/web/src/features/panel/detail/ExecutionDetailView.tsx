// 결과 상세 드릴다운 뷰 (사이드 패널 스택 top).
// - 헤더: [← 뒤로] + 레시피명/실행 시각 (breadcrumb 역할)
// - 본문: 결과값(resultValues) + 스텝별 결과(recipes[0].steps[])
// 데이터는 useExecutionDetail(React Query)로 조회. 채팅 store 와 분리된 독립 조회.
// resultValues 는 첫 레시피 기준(단일 레시피 실행 가정). 플랜(다중)이면 레시피별로 순회 렌더.

import { useMemo } from "react";
import type { ExecutionRecipeView, ExecutionResponse } from "../../../api/types";
import { formatDuration, formatTime, relativeTime, statusIcon } from "../shared/format";
import { useExecutionDetail } from "./useExecutionDetail";

interface Props {
  executionId: number;
  onBack: () => void;
}

/** 값 하나를 사람이 읽는 문자열로. 객체/배열은 JSON 요약 */
function stringifyValue(value: unknown): string {
  if (value == null) return "-";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function ResultValues({ values }: { values: Record<string, unknown> | null | undefined }) {
  const entries = Object.entries(values ?? {});
  if (entries.length === 0) return null;
  return (
    <div className="exec-detail__section">
      <div className="exec-detail__section-title">결과값</div>
      <ul className="exec-detail__kv">
        {entries.map(([key, value]) => (
          <li key={key} className="exec-detail__kv-row">
            <span className="exec-detail__kv-key">{key}</span>
            <span className="exec-detail__kv-value">{stringifyValue(value)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function RecipeSteps({ recipe }: { recipe: ExecutionRecipeView }) {
  return (
    <div className="exec-detail__section">
      <div className="exec-detail__section-title">실행 단계</div>
      <ol className="exec-detail__steps">
        {recipe.steps.map((step) => {
          const failed = step.errorMessage || (step.status.code ?? "").toUpperCase() === "FAILED";
          return (
            <li key={step.id} className="exec-detail__step">
              <span className="exec-detail__step-icon" aria-hidden>
                {statusIcon(step.status.code)}
              </span>
              <div className="exec-detail__step-main">
                <span className="exec-detail__step-name">
                  {step.stepIndex + 1}. {step.stepName}
                </span>
                {step.summary && (
                  <span className="exec-detail__step-summary">{step.summary}</span>
                )}
                {failed && step.errorMessage && (
                  <span className="exec-detail__step-error">{step.errorMessage}</span>
                )}
              </div>
              <span className="exec-detail__step-status">{step.status.description}</span>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

function DetailBody({ data }: { data: ExecutionResponse }) {
  const recipes = data.recipes ?? [];
  return (
    <>
      {recipes.map((recipe) => (
        <div key={recipe.id} className="exec-detail__recipe">
          {recipes.length > 1 && (
            <div className="exec-detail__recipe-title">{recipe.recipeName}</div>
          )}
          <ResultValues values={recipe.resultValues} />
          {recipe.steps.length > 0 && <RecipeSteps recipe={recipe} />}
        </div>
      ))}
      {recipes.length === 0 && (
        <div className="side-panel__empty">표시할 실행 상세가 없어요.</div>
      )}
    </>
  );
}

export function ExecutionDetailView({ executionId, onBack }: Props) {
  const { data, isLoading, isError } = useExecutionDetail(executionId);

  const subtitle = useMemo(() => {
    if (!data) return null;
    const rel = relativeTime(data.startedAt) ?? formatTime(data.startedAt);
    const dur = formatDuration(data.durationMs);
    return [rel, dur].filter(Boolean).join(" · ");
  }, [data]);

  return (
    <div className="side-panel__view" role="tabpanel" aria-label="실행 결과 상세">
      {/* 헤더: 뒤로가기 + 제목 */}
      <div className="exec-detail__head">
        <button
          type="button"
          className="exec-detail__back"
          onClick={onBack}
          aria-label="목록으로 돌아가기"
        >
          ← 뒤로
        </button>
        <div className="exec-detail__title-wrap">
          <span className="exec-detail__title">
            {statusIcon(data?.status.code)} {data?.title ?? "실행 결과"}
          </span>
          {subtitle && <span className="exec-detail__subtitle">{subtitle}</span>}
        </div>
      </div>

      <div className="side-panel__body">
        {isLoading ? (
          <div className="side-panel__loading">
            <span className="side-panel__spinner" /> 불러오는 중…
          </div>
        ) : isError || !data ? (
          <div className="side-panel__empty">결과를 불러오지 못했어요. 잠시 후 다시 시도해주세요.</div>
        ) : (
          <DetailBody data={data} />
        )}
      </div>
    </div>
  );
}
