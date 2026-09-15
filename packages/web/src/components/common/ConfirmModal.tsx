// 재사용 확인 모달 (브라우저 confirm() 대체).
// - 제목 + 설명 + [취소]/[확인] 버튼. 위험 액션은 danger(빨강) 강조.
// - 오버레이 동작(ESC/배경클릭/포커스 트랩·복귀/중첩 top-most)은 AppModal(Base UI Dialog)에 위임.
// - 초기 포커스는 initialFocus("cancel"|"confirm")로 선택 → 해당 버튼 ref 를 AppModal 에 전달.

import { useId, useRef } from "react";
import { AppModal } from "./AppModal";

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
  /** 열릴 때 최초 포커스 대상 (기본 "confirm"). "cancel" 이면 취소 버튼에 포커스 */
  initialFocus?: "cancel" | "confirm";
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
  initialFocus = "confirm",
  onConfirm,
  onCancel,
}: Props) {
  const confirmRef = useRef<HTMLButtonElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const descId = useId();

  if (!open) return null;

  return (
    <AppModal
      open={open}
      onClose={onCancel}
      title={title}
      showCloseButton={false}
      describedById={description ? descId : undefined}
      initialFocusRef={initialFocus === "cancel" ? cancelRef : confirmRef}
    >
      {description && (
        <div id={descId} className="modal__body" style={{ whiteSpace: "pre-line" }}>
          {description}
        </div>
      )}
      <div className="modal__footer">
        <button ref={cancelRef} type="button" className="btn btn--secondary" onClick={onCancel}>
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
    </AppModal>
  );
}
