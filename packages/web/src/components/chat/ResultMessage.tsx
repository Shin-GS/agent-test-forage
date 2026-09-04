// 실행 결과 메시지 (RESULT payload).
// content 요약을 주로 보여주고, 구조화된 resultValues 가 있으면 요약해 표시한다.
// [결과 보기] → 사이드 패널 결과 상세 드릴다운으로 진입(panelStore.openDetail).
//   executionId 가 유효할 때만 활성화. 홈/히스토리 항목 클릭과 동일한 진입점.

import type { ResultPayload } from "../../api/types";
import { usePanelStore } from "../../features/panel/panelStore";
import { resultKeyLabel, resultValueDisplay } from "../../features/panel/shared/format";

interface Props {
  payload: ResultPayload;
  /** 표시용 요약 텍스트 (payload 파생물). 있으면 상단에 노출 */
  content?: string | null;
}

export function ResultMessage({ payload, content }: Props) {
  const entries = Object.entries(payload.resultValues ?? {});
  const openDetail = usePanelStore((s) => s.openDetail);
  // executionId 가 유효할 때만 [결과 보기] 활성화(사이드 패널 상세 드릴다운으로 진입).
  const canView = typeof payload.executionId === "number" && payload.executionId > 0;

  return (
    <div className="result-message" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
      {content && (
        <div style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{content}</div>
      )}

      {entries.length > 0 && (
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: "var(--space-1)" }}>
          {entries.map(([key, value]) => (
            <li key={key} style={{ display: "flex", gap: "var(--space-2)", fontSize: "var(--font-size-sm)" }}>
              <span style={{ color: "var(--color-text-secondary)", minWidth: 90 }}>
                {resultKeyLabel(key, payload.resultLabels)}
              </span>
              <span style={{ color: "var(--color-text-primary)" }}>{resultValueDisplay(value)}</span>
            </li>
          ))}
        </ul>
      )}

      <div>
        <button
          type="button"
          className="btn btn--secondary btn--sm"
          disabled={!canView}
          title={canView ? "결과 상세 보기" : "상세를 볼 수 없어요"}
          onClick={() => canView && openDetail(payload.executionId)}
        >
          결과 보기
        </button>
      </div>
    </div>
  );
}
