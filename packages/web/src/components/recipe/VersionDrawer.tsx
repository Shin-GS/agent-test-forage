// 버전 기록 drawer (레시피 편집 Case 11). 우측 slide-in.
// - 최신순 목록, 현재 버전(currentVersion)은 "현재" 배지 + 강조(미리보기/복원 버튼 없음).
// - 각 과거 버전: [미리보기] [복원] (복원은 canEdit 일 때만).
// - [더 보기] 커서 페이징(useInfiniteQuery). 빈 상태(현재 버전만 존재 = 이력 없음).
// - 오버레이 동작(ESC/바깥클릭/포커스 트랩·복귀/중첩 top-most)은 AppDrawer(Base UI Dialog)에 위임.
//   미리보기 모달이 위에 겹쳐도 Base UI 가 최상단만 dismiss 하므로 드로어가 오판으로 닫히지 않는다.

import { useState } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { recipesApi } from "../../api";
import type { RecipeVersionSummary } from "../../api/types";
import { AppDrawer } from "../common/AppDrawer";
import { ConfirmModal } from "../common/ConfirmModal";
import { VersionPreviewModal } from "./VersionPreviewModal";

interface VersionDrawerProps {
  recipeId: number;
  /** 현재 레시피의 최신 버전 번호 (강조/‘현재’ 배지용) */
  currentVersion: number;
  /** 복원 버튼 노출 여부 (읽기 전용이면 false) */
  canEdit: boolean;
  /** 열림 상태(제어형). 부모가 소유 */
  open: boolean;
  onClose: () => void;
  /**
   * 복원 확정 콜백. 드로어 내부에서 복원 확인 모달까지 처리한 뒤, 사용자가 확정하면 호출된다.
   * 실제 복원 실행(mutation/토스트/폼 반영)은 부모가 담당한다.
   */
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
  open,
  onClose,
  onRestore,
}: VersionDrawerProps) {
  // 미리보기/복원확인 모달 상태를 드로어 내부에서 소유한다. 이렇게 하면 두 모달(Dialog)이 드로어(Dialog)의
  // React 트리 안에 중첩 렌더되어 Base UI 가 nested dialog 로 인식한다 → 모달 내부 클릭/포커스가
  // 드로어의 바깥 클릭(outside-press)으로 오판되지 않고, aria-hidden/포커스 충돌도 없다(겹침 시 드로어 유지, R2).
  const [previewVersionNo, setPreviewVersionNo] = useState<number | null>(null);
  const [restoreVersionNo, setRestoreVersionNo] = useState<number | null>(null);

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
    enabled: open,
  });

  const versions: RecipeVersionSummary[] = data?.pages.flatMap((p) => p.items) ?? [];
  // 현재 버전만 존재하거나 비어 있으면 이력 없음으로 간주
  const hasHistory = versions.some((v) => v.versionNo !== currentVersion);

  return (
    <AppDrawer open={open} onClose={onClose} ariaLabel="버전 기록">
      <div className="version-drawer__header">
        <span className="version-drawer__title">버전 기록</span>
        {/* 닫기: 제어형 onClose 호출(부모가 open=false). AppDrawer(Base UI)가 상태 전이를 반영한다. */}
        <button
          type="button"
          className="version-drawer__close"
          aria-label="닫기"
          onClick={onClose}
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
                        onClick={() => setPreviewVersionNo(v.versionNo)}
                      >
                        미리보기
                      </button>
                      {canEdit && (
                        <button
                          type="button"
                          className="btn btn--secondary btn--sm"
                          onClick={() => setRestoreVersionNo(v.versionNo)}
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

      {/* 미리보기 모달을 드로어 트리 안에서 렌더 → Base UI nested dialog 로 인식되어
          미리보기 내부 클릭이 드로어의 바깥 클릭으로 오판되지 않는다(겹쳐도 드로어 유지). */}
      {previewVersionNo != null && (
        <VersionPreviewModal
          recipeId={recipeId}
          versionNo={previewVersionNo}
          canRestore={canEdit}
          onClose={() => setPreviewVersionNo(null)}
          onRestore={(versionNo) => {
            setPreviewVersionNo(null);
            setRestoreVersionNo(versionNo);
          }}
        />
      )}

      {/* 복원 확인 모달 — 드로어 트리 안에서 렌더(nested). 확정 시 실제 복원은 부모(onRestore)가 실행. */}
      <ConfirmModal
        open={restoreVersionNo != null}
        title={restoreVersionNo != null ? `v${restoreVersionNo}으로 복원` : "복원"}
        description={
          restoreVersionNo != null
            ? `v${restoreVersionNo} 내용으로 새 버전(v${currentVersion + 1})을 만듭니다. 현재 내용(v${currentVersion})은 버전으로 보관되어 안전하며, 되돌리기도 이력에 남습니다. 선택한 버전 이후 스펙이 변경돼 복원 결과가 유효하지 않을(INVALID) 수 있으며, 이 경우에도 복원은 되고 실행 전 유효성 경고로 안내됩니다.`
            : undefined
        }
        confirmLabel="이 버전으로 복원"
        onConfirm={() => {
          if (restoreVersionNo != null) onRestore(restoreVersionNo);
          setRestoreVersionNo(null);
        }}
        onCancel={() => setRestoreVersionNo(null)}
      />
    </AppDrawer>
  );
}
