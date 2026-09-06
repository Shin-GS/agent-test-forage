// 버전 미리보기 모달 (레시피 편집 Case 12). 읽기 전용.
// 특정 버전 상세를 조회해 메타/스텝/결과정의/결과템플릿을 요약 렌더한다.
// 푸터 [이 버전으로 복원] 은 canRestore(=편집 가능)일 때만 노출.
// ESC/배경 클릭 닫기 + focus trap 는 상위 오버레이 패턴을 따른다.

import { useEffect, useId, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import { recipesApi } from "../../api";
import { stepTypeLabel } from "./recipeForm";
import type { RecipeStep } from "../../api/types";

interface VersionPreviewModalProps {
  recipeId: number;
  versionNo: number;
  /** 복원 버튼 노출 여부 (읽기 전용 모드면 false) */
  canRestore: boolean;
  onClose: () => void;
  /** [이 버전으로 복원] 클릭 → 상위에서 복원 확인 모달을 연다 */
  onRestore: (versionNo: number) => void;
}

/** ISO → "YYYY-MM-DD HH:mm" (실패 시 원본 앞 16자) */
function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const parsed = Date.parse(value);
  if (Number.isNaN(parsed)) return value.slice(0, 16);
  const d = new Date(parsed);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function stepName(step: RecipeStep, index: number): string {
  if (step.type === "api" && step.label) return step.label;
  return step.name || `스텝 ${index + 1}`;
}

export function VersionPreviewModal({
  recipeId,
  versionNo,
  canRestore,
  onClose,
  onRestore,
}: VersionPreviewModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);
  const titleId = useId();

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["recipe", recipeId, "version", versionNo],
    queryFn: () => recipesApi.getVersion(recipeId, versionNo),
  });

  // ESC 닫기 + focus trap + 포커스 복원
  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    const raf = requestAnimationFrame(() => {
      modalRef.current?.querySelector<HTMLElement>("button")?.focus();
    });
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
        return;
      }
      if (e.key === "Tab") {
        const root = modalRef.current;
        if (!root) return;
        // body 가 로딩/에러/성공에 따라 바뀌므로 keydown 시점에 매번 재수집.
        // disabled 요소는 셀렉터로, 비가시(display:none/hidden) 요소는 offsetParent 로 제외.
        const focusables = Array.from(
          root.querySelectorAll<HTMLElement>(
            'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
          ),
        ).filter((el) => el.offsetParent !== null || el.getClientRects().length > 0);
        if (focusables.length === 0) return;
        const first = focusables[0];
        const last = focusables[focusables.length - 1];
        const active = document.activeElement as HTMLElement | null;
        if (e.shiftKey && active === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && active === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      cancelAnimationFrame(raf);
      document.removeEventListener("keydown", handleKeyDown);
      if (previouslyFocused && document.contains(previouslyFocused)) previouslyFocused.focus();
    };
  }, [onClose]);

  const steps = data?.steps ?? [];
  const resultDefs = data?.resultDefinition ?? [];

  return (
    <div
      className="modal-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={modalRef}
        className="modal modal--wide"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <div className="modal__header">
          <h2 id={titleId} className="modal__title">
            버전 미리보기 (읽기 전용)
          </h2>
          <button type="button" className="btn btn--ghost btn--sm" aria-label="닫기" onClick={onClose}>
            ✕
          </button>
        </div>

        <div className="modal__body">
          {isLoading && (
            <div className="recipe-state" role="status" aria-live="polite">
              버전을 불러오는 중입니다…
            </div>
          )}
          {isError && (
            <div className="recipe-state recipe-state--error" role="alert">
              버전을 불러오지 못했습니다{error instanceof Error ? `: ${error.message}` : ""}
            </div>
          )}
          {data && (
            <>
              <div className="preview-meta">
                <span className="badge badge--neutral">v{data.versionNo}</span>
                <span>· {formatDateTime(data.createdAt)} 생성</span>
              </div>

              <div className="preview-block">
                <div className="preview-block__title">① 메타 정보</div>
                <div className="preview-field">
                  <span className="preview-field__key">레시피명</span>
                  <span className="preview-field__val">{data.name || "-"}</span>
                </div>
                <div className="preview-field">
                  <span className="preview-field__key">설명</span>
                  <span className="preview-field__val">{data.description || "-"}</span>
                </div>
                <div className="preview-field">
                  <span className="preview-field__key">공개 범위</span>
                  <span className="preview-field__val">
                    {data.visibility?.description ?? data.visibility?.code ?? "-"}
                  </span>
                </div>
              </div>

              <div className="preview-block">
                <div className="preview-block__title">③ 스텝 ({steps.length}개)</div>
                {steps.length === 0 ? (
                  <div className="preview-field">
                    <span className="preview-field__val">스텝 없음</span>
                  </div>
                ) : (
                  steps.map((step, i) => (
                    <div key={i} className="preview-field">
                      <span className="preview-field__key">
                        {i + 1}. {stepName(step, i)}
                      </span>
                      <span className="preview-field__val badge badge--neutral">
                        {stepTypeLabel(step.type)}
                      </span>
                    </div>
                  ))
                )}
              </div>

              {resultDefs.length > 0 && (
                <div className="preview-block">
                  <div className="preview-block__title">④ 결과 정의</div>
                  {resultDefs.map((item, i) => (
                    <div key={i} className="preview-field">
                      <span className="preview-field__key">{item.key}</span>
                      <span className="preview-field__val">{item.label || item.key}</span>
                    </div>
                  ))}
                </div>
              )}

              {data.resultTemplate && (
                <div className="preview-block" style={{ marginBottom: 0 }}>
                  <div className="preview-block__title">⑤ 결과 메시지 템플릿</div>
                  <div className="preview-field">
                    <span className="preview-field__val">{data.resultTemplate}</span>
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        <div className="modal__footer">
          <button type="button" className="btn btn--secondary" onClick={onClose}>
            닫기
          </button>
          {canRestore && data && (
            <button type="button" className="btn btn--primary" onClick={() => onRestore(data.versionNo)}>
              이 버전으로 복원
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
