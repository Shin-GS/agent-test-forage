// 결과 상세 드릴다운 뷰.
// 두 곳에서 재사용된다:
//  - 사이드 패널(variant="panel", 기본): [← 뒤로] 헤더 + 결과값 + 스텝 요약. 기존 동작 유지(회귀 방지).
//  - 히스토리 상세 페이지(variant="page"): 내부 헤더 없음(PageActionBar 가 담당). 요약 헤더 +
//    실패 요약 배너/점프 + 레시피 블록(레시피명·원본 링크·버전 안내) + 결과값/스텝 + 복사.
// 데이터는 useExecutionDetail(React Query)로 조회. 플랜(다중)이면 recipes[] 를 순회 렌더한다.
//
// 디자인 명세: docs/design/web/history.html (Case 2·3·6), 기획: docs/specs/pages/history-full.md.

import { useMemo, type MouseEvent } from "react";
import type { ExecutionRecipeView, ExecutionResponse, ExecutionStepView } from "../../../api/types";
import { CopyButton } from "../../../components/common/CopyButton";
import {
  formatAbsolute,
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
   * true 면 각 스텝을 펼쳐 원본 응답/입력 JSON 을 확인할 수 있게 한다(히스토리 상세용).
   * 사이드 패널에서는 기본 false(요약만 노출).
   */
  showStepJson?: boolean;
  /**
   * true 면 헤더 subtitle 에 실행 모드(mode.description)를 함께 표기한다.
   * 사이드 패널에서는 기본 false(회귀 방지). mode 가 없으면 옵션과 무관하게 생략.
   */
  showMode?: boolean;
  /**
   * 표시 형태. "panel"(기본) = 사이드 패널(내부 [← 뒤로] 헤더 + 컴팩트).
   * "page" = 히스토리 상세 페이지(요약 헤더/실패 배너/레시피 블록/복사, 내부 헤더 없음).
   */
  variant?: "panel" | "page";
  /**
   * 원본 레시피 링크 클릭 핸들러(page variant 전용). 활성 링크일 때만 호출된다.
   * 없으면 링크를 렌더하지 않는다(패널 회귀 방지).
   */
  onOpenRecipe?: (recipeId: number) => void;
}

/** JSON 값을 보기 좋게 직렬화. 실패 시 String 폴백 */
function prettyJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

/** 스텝이 실패 상태인지 (errorMessage 또는 상태코드 FAILED/ERROR/TIMEOUT) */
function isStepFailed(step: ExecutionStepView): boolean {
  const code = (step.status.code ?? "").toUpperCase();
  return Boolean(step.errorMessage) || code === "FAILED" || code === "ERROR" || code === "TIMEOUT";
}

/** 스텝 성공률 N/M 계산. M=실행된 스텝(SKIPPED/PENDING 제외), N=SUCCESS. 전 레시피 합산. */
function computeStepSuccessRate(recipes: ExecutionRecipeView[]): { n: number; m: number } {
  let n = 0;
  let m = 0;
  for (const recipe of recipes) {
    for (const step of recipe.steps) {
      const code = (step.status.code ?? "").toUpperCase();
      if (code === "SKIPPED" || code === "PENDING") continue;
      m += 1;
      if (code === "SUCCESS" || code === "COMPLETED") n += 1;
    }
  }
  return { n, m };
}

/** 첫 실패 스텝(가장 앞 레시피의 가장 앞 실패 스텝)을 찾는다. 없으면 null. */
function findFirstFailure(
  recipes: ExecutionRecipeView[],
): { recipe: ExecutionRecipeView; step: ExecutionStepView } | null {
  for (const recipe of recipes) {
    for (const step of recipe.steps) {
      if (isStepFailed(step)) return { recipe, step };
    }
  }
  return null;
}

/** 실행 전체가 실패/부분성공인지 (실패 배너 노출 조건) */
function isFailedExecution(data: ExecutionResponse): boolean {
  const code = (data.status.code ?? "").toUpperCase();
  return (
    code === "FAILED" ||
    code === "ERROR" ||
    code === "TIMEOUT" ||
    code === "PARTIAL" ||
    code === "PARTIAL_SUCCESS"
  );
}

/** 스텝의 DOM id (실패 지점 점프용) */
function stepDomId(step: ExecutionStepView): string {
  return `exec-step-${step.id}`;
}

/** 스텝 원본 JSON 펼침 블록 (응답/사용자 입력). page variant 면 복사 버튼 포함. */
function StepJson({ step, withCopy }: { step: ExecutionStepView; withCopy: boolean }) {
  const hasResponse = step.response != null;
  const hasInput = step.userInput != null;
  if (!hasResponse && !hasInput) return null;
  return (
    <div className="exec-detail__step-json-wrap">
      {hasInput && (
        <details className="exec-detail__json">
          <summary className="exec-detail__json-summary">사용자 입력값</summary>
          {withCopy && (
            <div className="exec-detail__json-head">
              <CopyButton
                text={prettyJson(step.userInput)}
                label={`${step.stepIndex + 1}번 스텝 입력값 복사`}
              />
            </div>
          )}
          <pre className="exec-detail__json-body">{prettyJson(step.userInput)}</pre>
        </details>
      )}
      {hasResponse && (
        <details className="exec-detail__json">
          <summary className="exec-detail__json-summary">원본 응답</summary>
          {withCopy && (
            <div className="exec-detail__json-head">
              <CopyButton
                text={prettyJson(step.response)}
                label={`${step.stepIndex + 1}번 스텝 응답 복사`}
              />
            </div>
          )}
          <pre className="exec-detail__json-body">{prettyJson(step.response)}</pre>
        </details>
      )}
    </div>
  );
}

function ResultValues({
  values,
  labels,
  withCopy,
}: {
  values: Record<string, unknown> | null | undefined;
  /** 결과키 표시명 맵(BE 상세 응답 resultLabels). 없는 key는 원본 key 폴백 */
  labels?: Record<string, string> | null;
  withCopy: boolean;
}) {
  const entries = Object.entries(values ?? {});
  if (entries.length === 0) return null;
  return (
    <div className="exec-detail__section">
      <div className="exec-detail__section-title">결과값</div>
      <ul className="exec-detail__kv">
        {entries.map(([key, value]) => {
          const label = resultKeyLabel(key, labels);
          return (
            <li key={key} className="exec-detail__kv-row">
              <span className="exec-detail__kv-key">{label}</span>
              <span className="exec-detail__kv-value">{resultValueDisplay(value)}</span>
              {withCopy && <CopyButton text={resultValueDisplay(value)} label={`${label} 값 복사`} />}
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function RecipeSteps({
  recipe,
  showStepJson,
  withCopy,
}: {
  recipe: ExecutionRecipeView;
  showStepJson: boolean;
  withCopy: boolean;
}) {
  return (
    <div className="exec-detail__section">
      <div className="exec-detail__section-title">실행 단계</div>
      <ol className="exec-detail__steps">
        {recipe.steps.map((step) => {
          const failed = isStepFailed(step);
          return (
            <li
              key={step.id}
              id={stepDomId(step)}
              className={`exec-detail__step${failed ? " exec-detail__step--error" : ""}`}
              tabIndex={-1}
            >
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
              {showStepJson && <StepJson step={step} withCopy={withCopy} />}
            </li>
          );
        })}
      </ol>
    </div>
  );
}

/** 레시피 블록 헤더 (page variant 전용): 레시피명 + 버전 안내 + 원본 링크 */
function RecipeHeader({
  recipe,
  multi,
  onOpenRecipe,
}: {
  recipe: ExecutionRecipeView;
  /** 플랜(다중)이면 "N. " 순번을 앞에 붙인다 */
  multi: boolean;
  onOpenRecipe?: (recipeId: number) => void;
}) {
  const deleted = recipe.recipeDeleted === true;
  const current = recipe.recipeCurrentVersion;
  const versionDiffers = !deleted && current != null && current !== recipe.recipeVersionNo;

  const title = multi ? `${recipe.sequence + 1}. ${recipe.recipeName}` : recipe.recipeName;

  return (
    <div className="detail-recipe__head">
      <span className="detail-recipe__title">{title}</span>
      {versionDiffers && (
        <span className="version-note">
          이 실행은 v{recipe.recipeVersionNo} 기준 · 원본 현재 v{current}
        </span>
      )}
      {onOpenRecipe &&
        (deleted ? (
          <span className="detail-source-link" aria-disabled="true">
            🔗 원본 삭제됨
          </span>
        ) : (
          <button
            type="button"
            className="detail-source-link"
            onClick={() => onOpenRecipe(recipe.recipeId)}
          >
            🔗 원본 레시피
          </button>
        ))}
    </div>
  );
}

function DetailBody({
  data,
  showStepJson,
  variant,
  onOpenRecipe,
}: {
  data: ExecutionResponse;
  showStepJson: boolean;
  variant: "panel" | "page";
  onOpenRecipe?: (recipeId: number) => void;
}) {
  const recipes = data.recipes ?? [];
  const isPage = variant === "page";
  const multi = recipes.length > 1;
  return (
    <>
      {recipes.map((recipe) => (
        <div key={recipe.id} className={isPage ? "detail-recipe" : "exec-detail__recipe"}>
          {isPage ? (
            // 페이지: 단일도 레시피 블록 헤더를 항상 표시(N=1 플랜).
            <RecipeHeader recipe={recipe} multi={multi} onOpenRecipe={onOpenRecipe} />
          ) : (
            // 패널: 기존 동작 유지 — 다중일 때만 레시피명 표시.
            multi && <div className="exec-detail__recipe-title">{recipe.recipeName}</div>
          )}
          <ResultValues values={recipe.resultValues} labels={recipe.resultLabels} withCopy={isPage} />
          {recipe.steps.length > 0 && (
            <RecipeSteps recipe={recipe} showStepJson={showStepJson} withCopy={isPage} />
          )}
        </div>
      ))}
      {recipes.length === 0 && (
        <div className="side-panel__empty">표시할 실행 상세가 없어요.</div>
      )}
    </>
  );
}

/** 요약 헤더 (page variant 전용): 첫 지표(단일=서비스/플랜=유형) + 모드 + 성공률 + 소요 + 실행시각 */
function SummaryHeader({ data }: { data: ExecutionResponse }) {
  const recipes = data.recipes ?? [];
  const isPlan = recipes.length > 1;
  const { n, m } = computeStepSuccessRate(recipes);
  const dur = formatDuration(data.durationMs);
  const abs = formatAbsolute(data.startedAt);
  const rel = relativeTime(data.startedAt);
  const mode = data.mode?.description;

  // 단일: 서비스명(BE 상세 응답 serviceName — INACTIVE/삭제 스펙도 이름 채움), 플랜: 유형(N개 레시피)
  const firstMetric = isPlan
    ? { key: "유형", val: `플랜 (${recipes.length}개 레시피)` }
    : { key: "서비스", val: data.serviceName ?? "-" };

  return (
    <div className="exec-summary">
      <div className="exec-summary__metrics">
        <div className="exec-summary__item">
          <span className="exec-summary__key">{firstMetric.key}</span>
          <span className="exec-summary__val">{firstMetric.val}</span>
        </div>
        {mode && (
          <div className="exec-summary__item">
            <span className="exec-summary__key">실행 모드</span>
            <span className="exec-summary__val">{mode}</span>
          </div>
        )}
        <div className="exec-summary__item">
          <span className="exec-summary__key">스텝 성공률</span>
          <span className="exec-summary__val">
            {n}/{m}
          </span>
        </div>
        {dur && (
          <div className="exec-summary__item">
            <span className="exec-summary__key">소요시간</span>
            <span className="exec-summary__val">{dur}</span>
          </div>
        )}
      </div>
      {abs && (
        <div className="exec-summary__time">
          실행: {abs}
          {rel && <span className="exec-summary__time-rel"> ({rel})</span>}
        </div>
      )}
    </div>
  );
}

/** 실패 요약 배너 (page variant 전용): 첫 실패 스텝 기준. 플랜이면 레시피명 병기. */
function FailBanner({ data }: { data: ExecutionResponse }) {
  const recipes = data.recipes ?? [];
  const first = findFirstFailure(recipes);
  if (!first) return null;
  const multi = recipes.length > 1;
  const { recipe, step } = first;
  const stepNo = step.stepIndex + 1;
  const label = multi
    ? `"${recipe.recipeName}" ${stepNo}번 스텝 "${step.stepName}"에서 실패`
    : `${stepNo}번 스텝 "${step.stepName}"에서 실패`;

  function jumpToFailure(e: MouseEvent<HTMLAnchorElement>) {
    e.preventDefault();
    const el = document.getElementById(stepDomId(step));
    if (!el) return;
    el.scrollIntoView({ behavior: "smooth", block: "center" });
    el.focus({ preventScroll: true });
  }

  return (
    <div className="fail-banner" role="alert">
      <span className="fail-banner__icon" aria-hidden>
        ⚠️
      </span>
      <span>
        <strong>{label}</strong>
        {step.errorMessage ? ` — ${step.errorMessage}` : ""}
      </span>
      <a
        className="btn btn--secondary btn--sm fail-banner__jump"
        href={`#${stepDomId(step)}`}
        onClick={jumpToFailure}
      >
        실패 지점으로 이동 ↓
      </a>
    </div>
  );
}

export function ExecutionDetailView({
  executionId,
  onBack,
  showStepJson = false,
  showMode = false,
  variant = "panel",
  onOpenRecipe,
}: Props) {
  const { data, isLoading, isError } = useExecutionDetail(executionId);
  const isPage = variant === "page";

  const subtitle = useMemo(() => {
    if (!data) return null;
    const rel = relativeTime(data.startedAt) ?? formatTime(data.startedAt);
    const dur = formatDuration(data.durationMs);
    // showMode 일 때만 mode.description 을 포함(없으면 생략). 사이드 패널(showMode=false)은 기존 그대로.
    const mode = showMode ? data.mode?.description : null;
    return [rel, dur, mode].filter(Boolean).join(" · ");
  }, [data, showMode]);

  // 페이지 모드: 내부 헤더 없이 본문만(요약 헤더 + 실패 배너 + 레시피 블록). 헤더는 PageActionBar 담당.
  if (isPage) {
    if (isLoading) {
      return (
        <div className="infinite-loader" role="status" aria-live="polite" style={{ padding: "var(--space-8)" }}>
          <span className="spinner" />
          <span>불러오는 중…</span>
        </div>
      );
    }
    if (isError || !data) {
      // 페이지의 404/에러 빈 상태는 상위(HistoryDetailPage)가 처리한다. 방어적 폴백만.
      return (
        <div className="side-panel__empty">결과를 불러오지 못했어요. 잠시 후 다시 시도해주세요.</div>
      );
    }
    return (
      <>
        <SummaryHeader data={data} />
        {isFailedExecution(data) && <FailBanner data={data} />}
        <DetailBody
          data={data}
          showStepJson={showStepJson}
          variant="page"
          onOpenRecipe={onOpenRecipe}
        />
      </>
    );
  }

  // 패널 모드(기존): [← 뒤로] 헤더 + 결과값 + 스텝 요약.
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
          <DetailBody data={data} showStepJson={showStepJson} variant="panel" />
        )}
      </div>
    </div>
  );
}
