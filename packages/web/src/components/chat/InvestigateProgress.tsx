// 정보 조회(investigate) 진행 블록 (디자인 명세 chat.html Case 16/16b).
// INVESTIGATE_PROGRESS 메시지 payload(schemaVersion 1)를 소비해 소스별 조회 단계를 표시한다.
//
// 원리(메시지 기반, ProgressSteps 패턴 재사용):
// - 진행/종료는 INVESTIGATE_PROGRESS 메시지 1개로 저장되고 message_update 로 갱신된다.
// - 새로고침/멀티탭 모두 메시지 로드/구독으로 동일 화면이 복원된다.
// - 최종 답변은 이 메시지를 갱신하지 않고 별도 TEXT(references) 메시지로 온다.
//
// 표시 규칙:
// - 헤더: running 이면 "🔍 정보 조회 중", 종료면 status 에 따라 톤/문구 변경
//   (failed→"⚠️ 정보 조회 실패", timeout→"⚠️ 정보 조회 시간 초과", done→"🔍 정보 조회 완료").
//   종료 상태에서는 running 스텝을 남기지 않는다(BE 가 finally 로 확정 — running 잔존 금지).
// - 스텝: 상태 아이콘(✅완료/🔄진행/⬜대기/⏭️스킵/❌실패) + source·query 텍스트 병기.
// - 단계 전이 announce: aria-live 노드는 초기 빈 상태로 두고, running 스텝이 바뀌는 시점에만
//   텍스트를 주입한다(정적 렌더로는 announce 미발생 — 스크린리더 정합, ProgressSteps 와 동일).

import { useEffect, useRef, useState } from "react";
import type { InvestigateProgressPayload, InvestigateStepPayload } from "../../api/types";

interface Props {
  payload: InvestigateProgressPayload;
}

/** 스텝 상태 → 아이콘/모디파이어 (chat.html .investigate-step) */
function stepView(status: string): { icon: string; mod: string; srLabel: string } {
  switch (status) {
    case "success":
      return { icon: "✅", mod: "investigate-step--done", srLabel: "완료: " };
    case "running":
      return { icon: "🔄", mod: "investigate-step--active", srLabel: "진행 중: " };
    case "failed":
      return { icon: "❌", mod: "investigate-step--failed", srLabel: "실패: " };
    case "skipped":
      return { icon: "⏭️", mod: "investigate-step--skipped", srLabel: "스킵: " };
    default:
      return { icon: "⬜", mod: "investigate-step--pending", srLabel: "대기: " };
  }
}

/** overallStatus 종료 여부 (running 이 아니면 종료) */
function isFinished(status: string): boolean {
  return status !== "running";
}

/** 폴백 안내 (아이콘 + 문구 분리 — 아이콘만 aria-hidden) */
interface Notice {
  icon: string;
  text: string;
}

/** status → 헤더 텍스트/톤 모디파이어/폴백 안내 */
function headerView(status: string): { text: string; mod: string; notice: Notice | null } {
  switch (status) {
    case "failed":
      return {
        text: "⚠️ 정보 조회 실패",
        mod: "investigate-progress__header--failed",
        notice: { icon: "ℹ️", text: "정보를 찾지 못했습니다. 질문을 조금 더 구체적으로 알려주세요." },
      };
    case "timeout":
      return {
        text: "⚠️ 정보 조회 시간 초과",
        mod: "investigate-progress__header--timeout",
        notice: { icon: "⏱️", text: "조회 시간이 초과되었습니다. 잠시 후 다시 시도해주세요." },
      };
    case "done":
      return { text: "🔍 정보 조회 완료", mod: "", notice: null };
    default:
      return { text: "🔍 정보 조회 중", mod: "", notice: null };
  }
}

export function InvestigateProgress({ payload }: Props) {
  const steps = payload.steps ?? [];
  const finished = isFinished(payload.status);
  const { text, mod, notice } = headerView(payload.status);
  const announce = useStepTransitionAnnounce(steps, finished);

  return (
    <div className="investigate-progress" role="group" aria-label="정보 조회 진행">
      <div className={`investigate-progress__header ${mod}`}>{text}</div>
      {/* 단계 전이 announce (스크린리더 전용). 초기 빈 상태 → 전이 시점에만 텍스트 주입. */}
      <div className="sr-only" role="status" aria-live="polite">
        {announce}
      </div>
      {steps.map((step, index) => (
        <StepRow key={`${index}-${step.source}`} step={step} />
      ))}
      {notice && (
        <div className="investigate-progress__notice">
          <span aria-hidden>{notice.icon}</span>
          <span>{notice.text}</span>
        </div>
      )}
    </div>
  );
}

/**
 * running 스텝이 바뀌는 시점에만 announce 텍스트를 만든다. 초기 렌더에서는 빈 문자열을 유지해
 * 정적 상태로는 스크린리더가 읽지 않게 한다(ProgressSteps 의 레시피 전이 announce 와 동일 패턴).
 * source·query 로 running 스텝을 식별한다(investigate 는 안정적 index 키가 없어 조합키 사용).
 */
function useStepTransitionAnnounce(
  steps: InvestigateStepPayload[],
  finished: boolean
): string {
  const running = steps.find((s) => s.status === "running") ?? null;
  const runningKey = running ? `${running.source}::${running.query ?? ""}` : null;

  const [announce, setAnnounce] = useState("");
  const prevKeyRef = useRef<string | null>(null);
  const mountedRef = useRef(false);

  useEffect(() => {
    const prev = prevKeyRef.current;
    prevKeyRef.current = runningKey;

    // 최초 마운트: 기준값만 세우고 announce 하지 않는다(정적 렌더 무발화).
    if (!mountedRef.current) {
      mountedRef.current = true;
      return;
    }
    // 종료되면 announce 하지 않는다(진행 전이만 읽음).
    if (finished) return;
    // running 스텝이 실제로 바뀐 시점에만 텍스트를 주입한다.
    if (runningKey != null && runningKey !== prev && running) {
      const label = running.query?.trim() || running.source;
      setAnnounce(`${label} 조회를 시작합니다.`);
    }
  }, [runningKey, finished, running]);

  return announce;
}

function StepRow({ step }: { step: InvestigateStepPayload }) {
  const { icon, mod, srLabel } = stepView(step.status);
  // 디자인 Case 16 구조: 메인=source, 보조=query 한 쌍. query 를 한 번만 노출(중복 제거).
  const query = step.query?.trim() || "";
  return (
    <div className={`investigate-step ${mod}`}>
      <span className="investigate-step__icon" aria-hidden>
        {icon}
      </span>
      <span className="sr-only">{srLabel}</span>
      <span>{step.source}</span>
      {query && <span className="investigate-step__source">{`"${query}"`}</span>}
    </div>
  );
}
