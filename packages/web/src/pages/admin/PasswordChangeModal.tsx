// 관리자 — 비밀번호 변경 폼 모달 (admin.html Case 6).
// - 제목 "비밀번호 변경 — {username}", 새 비밀번호(8자 이상) 직접 지정(임시 발급 아님).
// - label for/id 연결, role="dialog" aria-modal aria-labelledby, ESC/배경 클릭 닫기,
//   열릴 때 입력 포커스 + focus trap + 닫힐 때 트리거로 포커스 복원(ConfirmModal 패턴).
// - 클라이언트 1차 검증(8자) 후 onSubmit. 서버 400 은 부모가 토스트로 처리.

import { useEffect, useId, useRef, useState } from "react";

interface Props {
  open: boolean;
  /** 대상 사용자 아이디 (제목 표시) */
  username: string | null;
  submitting: boolean;
  onSubmit: (password: string) => void;
  onCancel: () => void;
}

export function PasswordChangeModal({ open, username, submitting, onSubmit, onCancel }: Props) {
  const modalRef = useRef<HTMLDivElement>(null);
  const fieldRef = useRef<HTMLInputElement>(null);
  const titleId = useId();

  // 최신 onCancel 을 ref 로 참조 → focus trap effect 가 매 렌더 teardown/재실행되지 않도록.
  const onCancelRef = useRef(onCancel);
  onCancelRef.current = onCancel;

  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setPassword("");
    setError(null);

    const previouslyFocused = document.activeElement as HTMLElement | null;
    const raf = requestAnimationFrame(() => fieldRef.current?.focus());

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onCancelRef.current();
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
      if (previouslyFocused && document.contains(previouslyFocused)) {
        previouslyFocused.focus();
      }
    };
  }, [open]);

  if (!open) return null;

  function handleSubmit() {
    if (password.length < 8) {
      setError("비밀번호는 8자 이상이어야 합니다.");
      return;
    }
    setError(null);
    onSubmit(password);
  }

  return (
    <div
      className="modal-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div
        ref={modalRef}
        className="modal"
        style={{ maxWidth: "420px" }}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <div className="modal__header">
          <h2 id={titleId} className="modal__title">
            비밀번호 변경 — {username}
          </h2>
        </div>
        <div className="modal__body">
          <p className="form-hint" style={{ marginBottom: "var(--space-4)" }}>
            관리자가 새 비밀번호를 직접 지정합니다. (임시 비밀번호 자동 발급이 아님)
          </p>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSubmit();
            }}
            style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}
          >
            <div className="form-group">
              <label className="form-label" htmlFor="reset-password">
                새 비밀번호
              </label>
              <input
                ref={fieldRef}
                className="input"
                type="password"
                id="reset-password"
                name="reset-password"
                placeholder="새 비밀번호를 입력하세요"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <span className="form-hint">8자 이상.</span>
            </div>

            {error && (
              <div className="alert alert--error" role="alert">
                {error}
              </div>
            )}
          </form>
        </div>
        <div className="modal__footer">
          <button type="button" className="btn btn--secondary btn--sm" onClick={onCancel}>
            취소
          </button>
          <button
            type="button"
            className="btn btn--primary btn--sm"
            disabled={submitting}
            onClick={handleSubmit}
          >
            변경
          </button>
        </div>
      </div>
    </div>
  );
}
