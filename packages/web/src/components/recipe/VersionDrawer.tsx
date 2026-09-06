// 버전 기록 drawer (레시피 편집 Case 11). 우측 slide-in.
// - 최신순 목록, 현재 버전(currentVersion)은 "현재" 배지 + 강조(미리보기/복원 버튼 없음).
// - 각 과거 버전: [미리보기] [복원] (복원은 canEdit 일 때만).
// - [더 보기] 커서 페이징(useInfiniteQuery). 빈 상태(현재 버전만 존재 = 이력 없음).
// - ESC/바깥 클릭 닫기 + 포커스 관리는 useOverlayDismiss.

import { useEffect, useRef } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { recipesApi } from "../../api";
import type { RecipeVersionSummary } from "../../api/types";

interface VersionDrawerProps {
  recipeId: number;
  /** 현재 레시피의 최신 버전 번호 (강조/‘현재’ 배지용) */
  currentVersion: number;
  /** 복원 버튼 노출 여부 (읽기 전용이면 false) */
  canEdit: boolean;
  onClose: () => void;
  /** 트리거 버튼(닫힐 때 포커스 복귀). 상위에서 넘겨준다 */
  triggerRef: React.RefObject<HTMLButtonElement | null>;
  onPreview: (versionNo: number) => void;
  onRestore: (versionNo: number) => void;
}

const PAGE_SIZE = 20;

/** ISO → 상대시간 (간단: 방금/N분 전/N시간 전/N일 전, 그 외 날짜) */
function relativeTime(value: string): string {
  const parsed = Date.parse(value);
  if (Number.isNaN(parsed)) return value.slice(0, 10);
  const diffMs = Date.now() - parsed;
  const min = Math.floor(diffMs / 60000);
  if (min < 1) return "방금 전";
  if (min < 60) return `${min}분 전`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}시간 전`;
  const day = Math.floor(hour / 24);
  if (day < 7) return `${day}일 전`;
  return new Date(parsed).toISOString().slice(0, 10);
}

export function VersionDrawer({
  recipeId,
  currentVersion,
  canEdit,
  onClose,
  triggerRef,
  onPreview,
  onRestore,
}: VersionDrawerProps) {
  const containerRef = useRef<HTMLElement>(null);

  // ESC + 바깥 클릭 닫기 (트리거는 상위에 있어 판정 제외) + 열릴 때 닫기 버튼 포커스 + 닫힐 때 트리거 복귀
  useEffect(() => {
    const raf = requestAnimationFrame(() => {
      containerRef.current?.querySelector<HTMLElement>("button")?.focus();
    });
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
      }
    };
    const handlePointerDown = (e: PointerEvent) => {
      const target = e.target as Node;
      const inContainer = containerRef.current?.contains(target);
      const inTrigger = triggerRef.current?.contains(target);
      if (!inContainer && !inTrigger) onClose();
    };
    document.addEventListener("keydown", handleKeyDown);
    document.addEventListener("pointerdown", handlePointerDown, true);
    return () => {
      cancelAnimationFrame(raf);
      document.removeEventListener("keydown", handleKeyDown);
      document.removeEventListener("pointerdown", handlePointerDown, true);
      if (triggerRef.current && document.contains(triggerRef.current)) triggerRef.current.focus();
    };
  }, [onClose, triggerRef]);

  const {
    data,
    isLoading,
    isError,
    error,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ["recipe", recipeId, "versions"],
    queryFn: ({ pageParam }) =>
      recipesApi.listVersions(recipeId, pageParam ?? undefined, PAGE_SIZE),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => (lastPage.hasNext ? (lastPage.nextCursor ?? undefined) : undefined),
  });

  const versions: RecipeVersionSummary[] = data?.pages.flatMap((p) => p.items) ?? [];
  // 현재 버전만 존재하거나 비어 있으면 이력 없음으로 간주
  const hasHistory = versions.some((v) => v.versionNo !== currentVersion);

  return (
    <aside ref={containerRef} className="version-drawer" aria-label="버전 기록">
      <div className="version-drawer__header">
        <span className="version-drawer__title">버전 기록</span>
        <button
          type="button"
          className="version-drawer__close"
          aria-label="닫기"
          onClick={() => {
            onClose();
            // 트리거로 포커스 복귀
            triggerRef.current?.focus();
          }}
        >
          ✕
        </button>
      </div>

      <div className="version-drawer__list">
        {isLoading && (
          <div className="recipe-state" role="status" aria-live="polite">
            버전 기록을 불러오는 중입니다…
          </div>
        )}

        {isError && (
          <div className="recipe-state recipe-state--error" role="alert">
            버전 기록을 불러오지 못했습니다{error instanceof Error ? `: ${error.message}` : ""}
          </div>
        )}

        {!isLoading && !isError && !hasHistory && (
          <div className="empty-state">
            <div className="empty-state__icon">🕘</div>
            <div className="empty-state__title">아직 수정 이력이 없습니다</div>
            <div className="empty-state__desc">
              레시피를 수정하고 저장하면 이전 버전이 여기에 쌓입니다.
            </div>
          </div>
        )}

        {!isLoading &&
          !isError &&
          hasHistory &&
          versions.map((v) => {
            const isCurrent = v.versionNo === currentVersion;
            return (
              <div
                key={v.versionNo}
                className={`version-item${isCurrent ? " version-item--current" : ""}`}
              >
                <span className="version-item__dot" aria-hidden="true" />
                <div className="version-item__body">
                  <div className="version-item__no">
                    v{v.versionNo}
                    {isCurrent && <span className="badge badge--info">현재</span>}
                  </div>
                  <div className="version-item__time">{relativeTime(v.createdAt)}</div>
                  {!isCurrent && (
                    <div className="version-item__actions">
                      <button
                        type="button"
                        className="btn btn--ghost btn--sm"
                        onClick={() => onPreview(v.versionNo)}
                      >
                        미리보기
                      </button>
                      {canEdit && (
                        <button
                          type="button"
                          className="btn btn--secondary btn--sm"
                          onClick={() => onRestore(v.versionNo)}
                        >
                          복원
                        </button>
                      )}
                    </div>
                  )}
                </div>
              </div>
            );
          })}

        {!isLoading && !isError && hasHistory && hasNextPage && (
          <div style={{ textAlign: "center", padding: "var(--space-3)" }}>
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              disabled={isFetchingNextPage}
              onClick={() => void fetchNextPage()}
            >
              {isFetchingNextPage ? "불러오는 중…" : "더 보기"}
            </button>
          </div>
        )}
      </div>
    </aside>
  );
}
