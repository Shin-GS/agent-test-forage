// 정보 조회(investigate) 참고 자료 (디자인 명세 chat.html Case 17 .references).
// TEXT 답변 메시지에 동반된 references payload 를 답변 본문 하단에 출처 칩 리스트로 표시한다.
//
// 방향 A (1단계 확정):
// - references 는 답변의 "출처 근거 표시용"이다. 클릭 인터랙션 없음.
// - 사이드 패널 스펙 상세로 이동하는 동작은 2단계 백로그(사이드 패널에 스펙 상세 전용 뷰가 없음).
//   1단계에서 클릭 시 어정쩡하게 패널을 열고 토스트를 띄우던 처리를 제거하고,
//   비인터랙션 출처 칩(📋 label)으로만 표시한다.
// - 2단계+(jira/figma)의 외부 URL 새 탭 열기는 1단계 범위 밖(여기서 처리하지 않음).

import type { ReferencesPayload } from "../../api/types";

interface Props {
  payload: ReferencesPayload;
}

export function MessageReferences({ payload }: Props) {
  const references = payload.references ?? [];

  if (references.length === 0) return null;

  return (
    <div className="references">
      <div className="references__label">참고한 자료</div>
      <ul className="references__list">
        {references.map((ref, index) => (
          <li
            key={`${index}-${ref.label}`}
            className="reference-chip"
            aria-label={`참고한 자료: ${ref.label}`}
          >
            <span aria-hidden>📋</span> {ref.label}
          </li>
        ))}
      </ul>
    </div>
  );
}
