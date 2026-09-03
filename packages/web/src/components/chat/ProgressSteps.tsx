// 레시피 실행 진행 상태 (디자인 명세 chat.html .progress-steps).
// store.executionProgress 를 소비해 진행을 표시한다.
//
// BE 계약(ExecutionProgressPayload): { sessionId, executionId, stepIndex, status, summary }
// - execution_progress: status="STARTED"(시작) 또는 스텝 상태 코드 + stepIndex(0-based)
// - execution_complete 는 store 에서 outcome.code 를 status 로 정규화해 전달
// payload 에 전체 스텝 수/스텝별 상세가 없으므로, 헤더 + 방금 보고된 스텝 한 줄 + 요약으로 표시한다.
// (스텝별 상세가 payload 에 추가되면 이 컴포넌트를 확장)

import type { ExecutionProgress } from "../../store/types";

interface Props {
  progress: ExecutionProgress;
}

/** status(대문자) 분류 */
function classify(status: string): "started" | "running" | "done" | "error" {
  const s = (status ?? "").toUpperCase();
  if (s === "COMPLETED" || s === "SUCCESS") return "done";
  if (s === "PARTIAL") return "done";
  if (s === "FAILED" || s === "ERROR" || s === "STOPPED" || s === "CANCELLED") return "error";
  if (s === "STARTED") return "started";
  return "running";
}

export function ProgressSteps({ progress }: Props) {
  const kind = classify(progress.status);
  const stepNo = progress.stepIndex != null ? progress.stepIndex + 1 : null;

  const header =
    kind === "done"
      ? { icon: "📋", label: "레시피 실행 완료" }
      : kind === "error"
        ? { icon: "❌", label: "실행 중단" }
        : kind === "started"
          ? { icon: "🔄", label: "레시피 실행 시작" }
          : { icon: "🔄", label: `API 호출 중...${stepNo ? ` (Step ${stepNo})` : ""}` };

  // 방금 보고된 스텝 한 줄 (시작/완료 알림엔 스텝 라인 생략)
  const stepLine =
    stepNo != null && (kind === "running" || kind === "error")
      ? {
          cls: kind === "error" ? "" : "progress-steps__item--done",
          icon: kind === "error" ? "❌" : "✅",
          text: `Step ${stepNo} ${kind === "error" ? "실패" : "완료"}`,
        }
      : null;

  return (
    <div className="progress-steps">
      <div className="progress-steps__header">
        {header.icon} {header.label}
      </div>

      {stepLine && (
        <div
          className={`progress-steps__item ${stepLine.cls}`}
          style={stepLine.icon === "❌" ? { color: "var(--color-error)" } : undefined}
        >
          {stepLine.icon} {stepLine.text}
        </div>
      )}

      {progress.summary && (
        <div style={{ marginTop: "var(--space-2)", fontSize: "var(--font-size-sm)" }}>{progress.summary}</div>
      )}
    </div>
  );
}
