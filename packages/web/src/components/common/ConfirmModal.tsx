// 재사용 확인 모달 (브라우저 confirm() 대체).
// - 제목 + 설명 + [취소]/[확인] 버튼. 위험 액션은 danger(빨강) 강조.
// - ESC / 배경 클릭으로 닫기, 열릴 때 확인 버튼에 포커스, focus trap(Tab 순환).
// - role="dialog" + aria-modal + aria-labelledby/‑describedby 로 접근성 준수.

import { useEffect, useId, useRef } from "react";

interface Props {
  open: boolean;
  title: string;
  description?: string;
  /** 확인 버튼 라벨 (기본 "확인") */
  confirmLabel?: string;
  /** 취소 버튼 라벨 (기본 "취소") */
  cancelLabel?: string;
  /** 위험 액션이면 확인 버튼을 빨강으로 강조 */
  danger?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmModal({
  open,
  title,
  description,
  confirmLabel = "확인",
  cancelLabel = "취소",
  danger = false,
  onConfirm,
  onCancel,
}: Props) {
  const modalRef = useRef<HTMLDivElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const titleId = useId();
  const descId = useId();

  // 열릴 때 확인 버튼 포커스 + ESC/Tab(focus trap) 키 처리 + 닫힐 때 포커스 복원
  useEffect(() => {
    if (!open) return;
    // 열기 직전 포커스를 갖고 있던 요소를 저장 → 언마운트/닫힘 시 복원
    const previouslyFocused = document.activeElement as HTMLElement | null;
    // 다음 프레임에 포커스(애니메이션/마운트 후)
    const raf = requestAnimationFrame(() => confirmRef.current?.focus());

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onCancel();
        return;
      }
      if (e.key === "Tab") {
        const root = modalRef.current;
        if (!root) return;
        const focusables = root.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        );
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
      // 포커스 복원: 저장해둔 요소가 여전히 문서에 있으면 포커스를 되돌린다
      if (previouslyFocused && document.contains(previouslyFocused)) {
        previouslyFocused.focus();
      }
    };
  }, [open, onCancel]);

  if (!open) return null;

  return (
    <div
      className="modal-backdrop"
      onMouseDown={(e) => {
        // 배경(백드롭) 클릭 시에만 닫는다(모달 내부 클릭은 무시)
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div
        ref={modalRef}
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descId : undefined}
      >
        <div className="modal__header">
          <h2 id={titleId} className="modal__title">
            {title}
          </h2>
        </div>
        {description && (
          <div id={descId} className="modal__body">
            {description}
          </div>
        )}
        <div className="modal__footer">
          <button type="button" className="btn btn--secondary" onClick={onCancel}>
            {cancelLabel}
          </button>
          <button
            ref={confirmRef}
            type="button"
            className={`btn ${danger ? "btn--danger" : "btn--primary"}`}
            onClick={onConfirm}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
