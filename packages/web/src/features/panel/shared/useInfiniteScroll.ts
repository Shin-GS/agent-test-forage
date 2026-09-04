// IntersectionObserver 기반 무한스크롤 sentinel 훅.
// 반환한 ref 를 목록 하단의 sentinel 요소에 부착하면, 뷰포트에 들어올 때 onLoadMore 를 호출한다.
// enabled=false 이면 관찰을 멈춘다(다음 페이지 없음/로딩 중).

import { useEffect, useRef } from "react";

interface Options {
  /** sentinel 이 보일 때 호출할 콜백 (보통 fetchNextPage) */
  onLoadMore: () => void;
  /** 관찰 활성화 여부 (hasNextPage && !isFetchingNextPage) */
  enabled: boolean;
  /** 스크롤 컨테이너 기준 여유 마진 (기본 200px 앞서 로드) */
  rootMargin?: string;
}

export function useInfiniteScroll<T extends HTMLElement = HTMLDivElement>({
  onLoadMore,
  enabled,
  rootMargin = "200px",
}: Options) {
  const sentinelRef = useRef<T | null>(null);
  // onLoadMore 를 ref 로 잡아 observer 를 매 렌더 재생성하지 않는다.
  const onLoadMoreRef = useRef(onLoadMore);
  onLoadMoreRef.current = onLoadMore;

  useEffect(() => {
    const node = sentinelRef.current;
    if (!node || !enabled) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          onLoadMoreRef.current();
        }
      },
      { rootMargin }
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [enabled, rootMargin]);

  return sentinelRef;
}
