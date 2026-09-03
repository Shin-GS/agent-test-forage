// 실행 결과 메시지 (RESULT payload).
// content 요약을 주로 보여주고, 구조화된 resultValues 가 있으면 접어서 상세를 표시한다.
// [결과 보기] 는 프로토타입에서 비활성(패널 상세 보기는 추후).

import type { ResultPayload } from "../../api/types";

interface Props {
  payload: ResultPayload;
  /** 표시용 요약 텍스트 (payload 파생물). 있으면 상단에 노출 */
  content?: string | null;
}

export function ResultMessage({ payload, content }: Props) {
  const entries = Object.entries(payload.resultValues ?? {});

  return (
    <div className="result-message" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
      {content && (
        <div style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{content}</div>
      )}

      {entries.length > 0 && (
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: "var(--space-1)" }}>
          {entries.map(([key, value]) => (
            <li key={key} style={{ display: "flex", gap: "var(--space-2)", fontSize: "var(--font-size-sm)" }}>
              <span style={{ color: "var(--color-text-secondary)", minWidth: 90 }}>{key}</span>
              <span style={{ color: "var(--color-text-primary)" }}>{String(value)}</span>
            </li>
          ))}
        </ul>
      )}

      <div>
        <button type="button" className="btn btn--secondary btn--sm" disabled title="추후 제공">
          결과 보기
        </button>
      </div>
    </div>
  );
}
