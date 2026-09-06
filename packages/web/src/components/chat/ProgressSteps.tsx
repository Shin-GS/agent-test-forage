// 레시피 실행 진행 블록 (디자인 명세 chat.html Case 3/12).
// PROGRESS 메시지 payload(schemaVersion 2, 레시피 그룹 구조)를 소비해 진행 상태를 표시한다.
//
// 원리(메시지 기반):
// - 진행/완료는 PROGRESS 메시지(payload.recipes[])로 저장되고 message_update 로 갱신된다.
// - 새로고침/멀티탭 모두 메시지 로드/구독으로 동일 화면이 복원된다.
// - 완료 후에도 블록은 유지된다(스텝을 완료 처리한 상태로 남김).
//
// 표시 규칙:
// - 단일 실행(recipes 1개): 기존과 동일하게 스텝 리스트 + 스텝 단위 진행률("실행 중 (k/N)").
// - 플랜(recipes ≥2): 레시피 그룹. 완료 레시피는 접힘(✅ + 결과 한 줄), 현재 running 레시피만
//   스텝 펼침, 대기 레시피는 ⬜. 헤더는 레시피 단위 진행률(recipeProgress "k/N 레시피").
// - 레시피 전이 announce: aria-live 노드는 초기 렌더 시 빈 상태로 두고, running 레시피가
//   바뀌는 시점에만 텍스트를 주입한다(정적 렌더로는 announce 미발생 — 스크린리더 정합).

import { useEffect, useRef, useState } from "react";
import type { ProgressPayload, ProgressRecipePayload, ProgressStepPayload } from "../../api/types";

interface Props {
  payload: ProgressPayload;
}

/** 스텝 상태 → 아이콘/모디파이어 */
function stepView(status: string): { icon: string; mod: string } {
  switch (status) {
    case "success":
      return { icon: "✅", mod: "progress-steps__item--done" };
    case "running":
      return { icon: "🔄", mod: "progress-steps__item--active" };
    case "failed":
      return { icon: "❌", mod: "progress-steps__item--failed" };
    case "skipped":
      return { icon: "⏭️", mod: "progress-steps__item--pending" };
    default:
      return { icon: "⬜", mod: "progress-steps__item--pending" };
  }
}

/** 레시피 상태 → 아이콘/모디파이어 (그룹 헤더) */
function recipeView(status: string): { icon: string; mod: string } {
  switch (status) {
    case "success":
      return { icon: "✅", mod: "progress-steps__recipe-head--done" };
    case "running":
      return { icon: "🔄", mod: "progress-steps__recipe-head--active" };
    case "failed":
      return { icon: "❌", mod: "progress-steps__recipe-head--failed" };
    case "stopped":
    case "cancelled":
      return { icon: "⏹️", mod: "progress-steps__recipe-head--stopped" };
    case "skipped":
      return { icon: "⏭️", mod: "progress-steps__recipe-head--pending" };
    default:
      return { icon: "⬜", mod: "progress-steps__recipe-head--pending" };
  }
}

/** overallStatus 종료 여부 (running 이 아니면 종료) */
function isFinished(status: string): boolean {
  return status !== "running";
}

/** overallStatus → 종료 라벨 */
function overallLabel(status: string): string {
  switch (status) {
    case "success":
      return "완료 ✅";
    case "partial":
      return "부분 완료";
    case "failed":
      return "실패 ❌";
    case "stopped":
      return "중단됨 ⏹️";
    case "cancelled":
      return "취소됨";
    default:
      return status;
  }
}

export function ProgressSteps({ payload }: Props) {
  const recipes = payload.recipes ?? [];
  const isPlan = recipes.length >= 2;

  return isPlan ? <PlanProgress payload={payload} /> : <SingleProgress payload={payload} />;
}

/** 단일 실행(N=1): 스텝 리스트 + 스텝 단위 진행률 */
function SingleProgress({ payload }: Props) {
  const recipe = payload.recipes?.[0];
  const steps = recipe?.steps ?? [];
  const total = steps.length;
  const done = steps.filter((s) => s.status === "success" || s.status === "skipped").length;
  const finished = isFinished(payload.overallStatus);
  const recipeName = recipe?.recipeName ?? payload.title ?? "레시피";

  const header = finished
    ? `📋 ${recipeName} ${overallLabel(payload.overallStatus)}`
    : `🔄 ${recipeName} 실행 중 (${done}/${total})`;

  return (
    <div className="progress-steps">
      <div className="progress-steps__header">{header}</div>
      {steps.map((step) => (
        <StepRow key={step.index} step={step} />
      ))}
    </div>
  );
}

/** 플랜(N≥2): 레시피 그룹. 완료 접힘 / 현재 스텝 펼침 / 대기. 헤더는 레시피 단위 진행률 */
function PlanProgress({ payload }: Props) {
  const recipes = payload.recipes ?? [];
  const { current, total } = payload.recipeProgress ?? { current: 0, total: recipes.length };
  const finished = isFinished(payload.overallStatus);
  const name = payload.title ?? "플랜";

  const header = finished
    ? `📋 ${name} 플랜 ${overallLabel(payload.overallStatus)} (${current}/${total} 레시피)`
    : `📋 ${name} 플랜 실행 중 (${current}/${total} 레시피)`;

  const announce = useRecipeTransitionAnnounce(recipes);

  return (
    <div className="progress-steps" role="group" aria-label="플랜 실행 진행">
      <div className="progress-steps__header">{header}</div>
      {/* 레시피 전이 announce (스크린리더 전용). 초기 렌더 시 빈 상태 → 전이 시점에만 텍스트 주입. */}
      <div className="sr-only" role="status" aria-live="polite">
        {announce}
      </div>
      {recipes.map((recipe) => (
        <RecipeGroup key={recipe.sequence} recipe={recipe} />
      ))}
    </div>
  );
}

/**
 * running 레시피가 바뀌는 시점에만 announce 텍스트를 만든다. 초기 렌더에서는 빈 문자열을 유지해
 * 정적 상태로는 스크린리더가 읽지 않게 한다(전이 시점에만 aria-live 가 발화하도록).
 */
function useRecipeTransitionAnnounce(recipes: ProgressRecipePayload[]): string {
  const running = recipes.find((r) => r.status === "running") ?? null;
  const [announce, setAnnounce] = useState("");
  const prevRunningSeqRef = useRef<number | null>(null);
  const mountedRef = useRef(false);

  useEffect(() => {
    const runningSeq = running?.sequence ?? null;
    const prev = prevRunningSeqRef.current;
    prevRunningSeqRef.current = runningSeq;

    // 최초 마운트: 기준값만 세우고 announce 하지 않는다(정적 렌더 무발화).
    if (!mountedRef.current) {
      mountedRef.current = true;
      return;
    }
    // running 레시피가 실제로 바뀐 시점에만 텍스트를 주입한다.
    if (runningSeq != null && runningSeq !== prev && running?.recipeName) {
      setAnnounce(`${running.recipeName} 실행을 시작합니다.`);
    }
  }, [running?.sequence, running?.recipeName]);

  return announce;
}

/** 레시피 그룹: 완료(접힘 + 결과 한 줄) / running(스텝 펼침) / 대기(⬜) */
function RecipeGroup({ recipe }: { recipe: ProgressRecipePayload }) {
  const { icon, mod } = recipeView(recipe.status);
  const label = recipe.recipeName ?? `레시피 ${recipe.sequence + 1}`;
  const isRunning = recipe.status === "running";

  return (
    <div className="progress-steps__recipe">
      <div className={`progress-steps__recipe-head ${mod}`}>
        <span className="progress-steps__icon" aria-hidden>
          {icon}
        </span>
        <span>
          {recipe.sequence + 1}. {label}
        </span>
        {recipe.summary && <span className="progress-steps__recipe-result">— {recipe.summary}</span>}
      </div>
      {/* 현재 running 레시피만 스텝 펼침 (완료/대기는 접힘) */}
      {isRunning && recipe.steps.length > 0 && (
        <div className="progress-steps__recipe-steps">
          {recipe.steps.map((step) => (
            <StepRow key={step.index} step={step} />
          ))}
        </div>
      )}
    </div>
  );
}

function StepRow({ step }: { step: ProgressStepPayload }) {
  const { icon, mod } = stepView(step.status);
  const label = step.name ?? `스텝 ${step.index + 1}`;
  return (
    <div className={`progress-steps__item ${mod}`}>
      <span className="progress-steps__icon" aria-hidden>
        {icon}
      </span>
      <span className="progress-steps__label">
        {step.index + 1}. {label}
      </span>
      {step.summary && <span className="progress-steps__summary">— {step.summary}</span>}
    </div>
  );
}
