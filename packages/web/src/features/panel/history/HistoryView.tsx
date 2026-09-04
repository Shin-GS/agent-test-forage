// 히스토리 탭: 실행 이력 커서 무한스크롤.
// - useHistory(useInfiniteQuery) 로 페이지 조회, useInfiniteScroll sentinel 로 바닥 감지 → fetchNextPage.
// - 항목: 상태 아이콘 + 레시피명(title) + 시각 + 결과 요약.
// - 결과 상세 드릴다운은 2차.

import { useMemo } from "react";
import { formatDuration, relativeTime, statusIcon } from "../shared/format";
import { useInfiniteScroll } from "../shared/useInfiniteScroll";
import type { PanelContext } from "../types";
import { useHistory } from "./useHistory";

interface Props extends PanelContext {
  /** 항목 클릭 시 결과 상세 드릴다운 열기 */
  onOpenDetail: (executionId: number) => void;
}

export function HistoryView({ onOpenDetail }: Props) {
  const query = useHistory();
  const {
    data,
    isLoading,
    hasNextPage,
    isFetchingNextPage,
    fetchNextPage,
  } = query;

  const items = useMemo(
    () => (data?.pages ?? []).flatMap((page) => page.items),
    [data]
  );

  const sentinelRef = useInfiniteScroll<HTMLDivElement>({
    onLoadMore: () => void fetchNextPage(),
    enabled: Boolean(hasNextPage) && !isFetchingNextPage,
  });

  return (
    <div className="side-panel__view" role="tabpanel" aria-label="히스토리">
      <div className="side-panel__body">
        {isLoading ? (
          <div className="side-panel__loading">
            <span className="side-panel__spinner" /> 불러오는 중…
          </div>
        ) : items.length === 0 ? (
          <div className="side-panel__empty">아직 실행한 레시피가 없어요.</div>
        ) : (
          <>
            {items.map((item) => {
              // 서비스명: serviceName 있으면 그대로, 없고 apiSpecId도 없으면(플랜 등) "여러 서비스".
              const serviceLabel =
                item.serviceName ?? (item.apiSpecId == null ? "여러 서비스" : null);
              const relative = relativeTime(item.startedAt);
              const duration = formatDuration(item.durationMs);
              const subParts = [serviceLabel && `🌐 ${serviceLabel}`, item.resultSummary]
                .filter(Boolean)
                .join(" · ");
              const metaParts = [relative, duration].filter(Boolean).join(" · ");
              return (
                <button
                  key={item.id}
                  type="button"
                  className="side-panel__history-item side-panel__history-item--clickable"
                  onClick={() => onOpenDetail(item.id)}
                  title="결과 상세 보기"
                >
                  <span aria-hidden>{statusIcon(item.status.code)}</span>
                  <div className="side-panel__history-main">
                    <span className="side-panel__history-name">{item.title ?? "레시피"}</span>
                    {subParts && (
                      <span className="side-panel__history-summary">{subParts}</span>
                    )}
                  </div>
                  {metaParts && (
                    <span className="side-panel__history-time">{metaParts}</span>
                  )}
                </button>
              );
            })}

            {/* 무한스크롤 sentinel + 로딩 표시 */}
            <div ref={sentinelRef} />
            {isFetchingNextPage && (
              <div className="side-panel__loading">
                <span className="side-panel__spinner" /> 불러오는 중…
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
