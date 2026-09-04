// matchMedia 기반 미디어 쿼리 훅. 뷰포트 변화에 반응해 boolean 을 반환한다.
// 반응형 자동 접기(우측 패널 <1200px, 좌측 사이드바 <768px)에 사용.

import { useEffect, useState } from "react";

/** query(예: "(max-width: 1199px)") 매칭 여부. 뷰포트 변화에 따라 갱신된다. */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState<boolean>(() => {
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
      return false;
    }
    return window.matchMedia(query).matches;
  });

  useEffect(() => {
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
      return;
    }
    const mql = window.matchMedia(query);
    const handler = (e: MediaQueryListEvent) => setMatches(e.matches);
    // 초기 동기화(SSR/최초 렌더 차이 보정)
    setMatches(mql.matches);
    mql.addEventListener("change", handler);
    return () => mql.removeEventListener("change", handler);
  }, [query]);

  return matches;
}
