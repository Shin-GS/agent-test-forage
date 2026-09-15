// PageToolbar — 목록형 페이지 상단 바(검색/필터 + 우측 액션).
//
// 페이지 상단 바 원칙(docs/specs/common/page-layout.md)의 "목록형" 상단.
// children 에 검색/필터 요소를, actions 에 우측 끝 주요 액션(만들기/추가 등)을 둔다.
// 우측 정렬은 내부 spacer 가 처리한다. sticky + flex-wrap 은 .page-toolbar 스타일에 있다.

import type { ReactNode } from "react";

interface PageToolbarProps {
  /** 검색/필터/정렬 등 좌측 요소 */
  children: ReactNode;
  /** 우측 끝 주요 액션(예: [+ 레시피 만들기]). 없으면 우측 비움 */
  actions?: ReactNode;
}

export function PageToolbar({ children, actions }: PageToolbarProps) {
  return (
    <div className="page-toolbar">
      {children}
      {actions != null && (
        <>
          <span className="page-toolbar__spacer" />
          {actions}
        </>
      )}
    </div>
  );
}
