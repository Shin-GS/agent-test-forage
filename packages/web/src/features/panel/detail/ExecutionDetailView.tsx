// 결과 상세 드릴다운 뷰 (사이드 패널 스택 top).
// - 헤더: [← 뒤로] + 레시피명/실행 시각 (breadcrumb 역할)
// - 본문: 결과값(resultValues) + 스텝별 결과(recipes[0].steps[])
// 데이터는 useExecutionDetail(React Query)로 조회. 채팅 store 와 분리된 독립 조회.
// resultValues 는 첫 레시피 기준(단일 레시피 실행 가정). 플랜(다중)이면 레시피별로 순회 렌더.

import { useMemo } from "react";
import type { ExecutionRecipeView, ExecutionResponse, ExecutionStepView } from "../../../api/types";
import {
  formatDuration,
  formatTime,
  relativeTime,
  resultKeyLabel,
  resultValueDisplay,
  statusIcon,
} from "../shared/format";
import { useExecutionDetail } from "./useExecutionDetail";

interface Props {
  executionId: number;
  onBack: () => void;
  /**
   * true 면 각 스텝을 펼쳐 원본 응답/입력 JSON 을 확인할 수 있게 한다(히스토리 상세 페이지용).
   * 사이드 패널에서는 기본 false(요약만 노출).
   */
  showStepJson?: boolean;
  /**
   * true 면 헤더 subtitle 에 실행 모드(mode.description)를 함께 표기한다(히스토리 상세 페이지용).
   * 사이드 패널에서는 기본 false(회귀 방지). mode 가 없으면 옵션과 무관하게 생략.
   */
  showMode?: boolean;
}

/** JSON 값을 보기 좋게 직렬화. 실패 시 String 폴백 */
function prettyJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

/** 스텝 원본 JSON 펼침 블록 (응답/사용자 입력). details/summary 로 키보드 접근 가능 */
function StepJson({ step }: { step: ExecutionStepView }) {
  const hasResponse = step.response != null;
  const hasInput = step.userInput != null;
  if (!hasResponse && !hasInput) return null;
  return (
    <div className="exec-detail__step-json-wrap">
      {hasInput && (
        <details className="exec-detail__json">
          <summary className="exec-detail__json-summary">사용자 입력값</summary>
          <pre className="exec-detail__json-body">{prettyJson(step.userInput)}</pre>
        </details>
      )}
      {hasResponse && (
        <details className="exec-detail__json">
          <summary className="exec-detail__json-summary">원본 응답</summary>
          <pre className="exec-detail__json-body">{prettyJson(step.response)}</pre>
        </details>
      )}
    </div>
  );
}

function ResultValues({
  values,
  labels,
}: {
  values: Record<string, unknown> | null | undefined;
  /** 결과키 표시명 맵(BE 상세 응답 resultLabels). 없는 key는 원본 key 폴백 */
  labels?: Record<string, string> | null;
}) {
  const entries = Object.entries(values ?? {});
  if (entries.length === 0) return null;
  return (
    <div className="exec-detail__section">
      <div className="exec-detail__section-title">결과값</div>
      <ul className="exec-detail__kv">
        {entries.map(([key, value]) => (
          <li key={key} className="exec-detail__kv-row">
            <span className="exec-detail__kv-key">{resultKeyLabel(key, labels)}</span>
            <span className="exec-detail__kv-value">{resultValueDisplay(value)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function RecipeSteps({ recipe, showStepJson }: { recipe: ExecutionRecipeView; showStepJson: boolean }) {
  return (
    <div className="exec-detail__section">
      <div className="exec-detail__section-title">실행 단계</div>
      <ol className="exec-detail__steps">
        {recipe.steps.map((step) => {
          const failed = step.errorMessage || (step.status.code ?? "").toUpperCase() === "FAILED";
          return (
            <li key={step.id} className="exec-detail__step">
              <div className="exec-detail__step-row">
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
              </div>
              {showStepJson && <StepJson step={step} />}
            </li>
          );
        })}
      </ol>
    </div>
  );
}

function DetailBody({ data, showStepJson }: { data: ExecutionResponse; showStepJson: boolean }) {
  const recipes = data.recipes ?? [];
  return (
    <>
      {recipes.map((recipe) => (
        <div key={recipe.id} className="exec-detail__recipe">
          {recipes.length > 1 && (
            <div className="exec-detail__recipe-title">{recipe.recipeName}</div>
          )}
          <ResultValues values={recipe.resultValues} labels={recipe.resultLabels} />
          {recipe.steps.length > 0 && <RecipeSteps recipe={recipe} showStepJson={showStepJson} />}
        </div>
      ))}
      {recipes.length === 0 && (
        <div className="side-panel__empty">표시할 실행 상세가 없어요.</div>
      )}
    </>
  );
}

export function ExecutionDetailView({
  executionId,
  onBack,
  showStepJson = false,
  showMode = false,
}: Props) {
  const { data, isLoading, isError } = useExecutionDetail(executionId);

  const subtitle = useMemo(() => {
    if (!data) return null;
    const rel = relativeTime(data.startedAt) ?? formatTime(data.startedAt);
    const dur = formatDuration(data.durationMs);
    // showMode 일 때만 mode.description 을 포함(없으면 생략). 사이드 패널(showMode=false)은 기존 그대로.
    const mode = showMode ? data.mode?.description : null;
    return [rel, dur, mode].filter(Boolean).join(" · ");
  }, [data, showMode]);

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
          <DetailBody data={data} showStepJson={showStepJson} />
        )}
      </div>
    </div>
  );
}
