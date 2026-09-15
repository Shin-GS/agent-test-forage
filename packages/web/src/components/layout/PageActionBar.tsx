// PageActionBar — 상세/편집형 페이지 상단 바(뒤로가기 + 제목 + 우측 액션).
//
// 페이지 상단 바 원칙(docs/specs/common/page-layout.md)의 "상세/편집형" 상단.
// 뒤로가기·저장 등 컨텍스트 액션을 콘텐츠 카드 안이 아니라 페이지 최상단에 둔다.
// 레시피 편집·스펙 상세가 동일 구조를 공유한다. sticky 는 .page-action-bar 스타일에 있다.

import type { ReactNode } from "react";

interface PageActionBarProps {
  /** 뒤로가기 클릭 핸들러(예: navigate("/recipes")) */
  onBack: () => void;
  /** 뒤로가기 라벨. 기본 "목록으로" */
  backLabel?: string;
  /** 화면에 표시할 제목(예: 레시피명/스펙명). PageShell 의 sr-only 제목과 별개의 시각 제목 */
  title: ReactNode;
  /** 제목 옆 부가 정보(상태 배지 등). 옵션 */
  meta?: ReactNode;
  /** 우측 끝 액션(저장/버전기록/비활성화/삭제 등) */
  actions?: ReactNode;
}

export function PageActionBar({
  onBack,
  backLabel = "목록으로",
  title,
  meta,
  actions,
}: PageActionBarProps) {
  return (
    <div className="page-action-bar">
      <button
        type="button"
        className="page-action-bar__back"
        onClick={onBack}
        aria-label={`${backLabel}(으)로 돌아가기`}
      >
        ← {backLabel}
      </button>
      <span className="page-action-bar__title">{title}</span>
      {meta}
      {actions != null && (
        <>
          <span className="page-action-bar__spacer" />
          {actions}
        </>
      )}
    </div>
  );
}
