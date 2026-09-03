// 레시피 실행 진행 블록 (디자인 명세 chat.html .progress-steps).
// PROGRESS 메시지 payload 를 소비해 스텝을 리스트로 표시한다.
//
// 원리(메시지 기반):
// - 진행/완료는 PROGRESS 메시지(payload.steps)로 저장되고 message_update 로 갱신된다.
// - 새로고침/멀티탭 모두 메시지 로드/구독으로 동일 화면이 복원된다.
// - 완료 후에도 블록은 유지된다(스텝을 완료 처리한 상태로 남김).

import type { ProgressPayload, ProgressStepPayload } from "../../api/types";

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

/** payload.status → 완료 여부 (running 이 아니면 종료) */
function isFinished(status: string): boolean {
  return status !== "running";
}

export function ProgressSteps({ payload }: Props) {
  const steps = payload.steps ?? [];
  const total = steps.length;
  const doneCount = steps.filter((s) => s.status === "success").length;
  const finished = isFinished(payload.status);
  const failed = payload.status === "failed";
  const recipeName = payload.recipeName ?? "레시피";

  const header = finished
    ? failed
      ? `📋 ${recipeName} 실행 실패 ❌`
      : `📋 ${recipeName} 완료 ✅`
    : `🔄 실행 중 (${doneCount}/${total})`;

  return (
    <div className="progress-steps">
      <div className="progress-steps__header">{header}</div>
      {steps.map((step) => (
        <StepRow key={step.index} step={step} />
      ))}
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
