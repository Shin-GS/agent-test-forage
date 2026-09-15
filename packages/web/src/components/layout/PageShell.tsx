// PageShell — 별도 전체 화면 페이지의 공통 껍데기.
//
// 페이지 상단 바 원칙(docs/specs/common/page-layout.md)을 강제하는 컴포넌트다.
// - 제목 전용 헤더를 두지 않는다(사이드바가 위치를 표시). 상단 바는 기능이 있을 때만.
// - title 을 받아 sr-only <h1> + document.title 을 자동 처리한다(접근성/브라우저 탭).
// - 상단 바는 슬롯으로만 받는다: toolbar(목록형 PageToolbar) 또는 actionBar(상세형 PageActionBar).
//   둘 다 없으면 콘텐츠형(바 없음).
//
// 새 별도 페이지는 이 컴포넌트로 감싸고 필요한 슬롯만 채운다. 제목 전용 헤더를 직접 마크업하지 않는다.

import { useEffect, type ReactNode } from "react";

const APP_NAME = "테스트메이트";

interface PageShellProps {
  /** 페이지명. sr-only <h1> 과 document.title 의 소스. 예: "레시피 관리" */
  title: string;
  /**
   * sr-only <h1> 에 붙일 대상명(상세/편집형). 있으면 "{title} — {subject}" 로 렌더.
   * document.title 은 대상명 없이 title 기준으로 간결히 둔다(page-layout.md 제목 문구 컨벤션).
   */
  subject?: string;
  /** 목록형 상단 바(PageToolbar). actionBar 와 동시 사용하지 않는다. */
  toolbar?: ReactNode;
  /** 상세/편집형 상단 바(PageActionBar). toolbar 와 동시 사용하지 않는다. */
  actionBar?: ReactNode;
  /** 페이지 본문 */
  children: ReactNode;
  /** 루트 div 클래스(페이지별 레이아웃 유지용). 기본 "recipe-page" */
  className?: string;
}

export function PageShell({
  title,
  subject,
  toolbar,
  actionBar,
  children,
  className = "recipe-page",
}: PageShellProps) {
  // 문서 타이틀: "{페이지명} · 테스트메이트". 이탈 시 복원.
  useEffect(() => {
    const previous = document.title;
    document.title = `${title} · ${APP_NAME}`;
    return () => {
      document.title = previous;
    };
  }, [title]);

  const headingText = subject ? `${title} — ${subject}` : title;

  return (
    <div className={className}>
      {/* 접근성 제목: 화면에 크게 노출하지 않고 스크린리더/접근성 트리에만 남긴다 */}
      <h1 className="sr-only">{headingText}</h1>
      {actionBar}
      {toolbar}
      {children}
    </div>
  );
}
