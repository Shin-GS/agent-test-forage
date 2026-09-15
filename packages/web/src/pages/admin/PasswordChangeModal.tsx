// 관리자 — 비밀번호 변경 폼 모달 (admin.html Case 6).
// - 제목 "비밀번호 변경 — {username}", 새 비밀번호(8자 이상) 직접 지정(임시 발급 아님).
// - 오버레이 동작(role/aria/ESC/배경클릭/focus trap/포커스 복원/스크롤락)은 AppModal(Base UI)에 위임.
//   열릴 때 입력 포커스는 initialFocusRef 로 지정.
// - 클라이언트 1차 검증(8자) 후 onSubmit. 서버 400 은 부모가 토스트로 처리.

import { useEffect, useRef, useState } from "react";
import { AppModal } from "../../components/common/AppModal";

interface Props {
  open: boolean;
  /** 대상 사용자 아이디 (제목 표시) */
  username: string | null;
  submitting: boolean;
  onSubmit: (password: string) => void;
  onCancel: () => void;
}

export function PasswordChangeModal({ open, username, submitting, onSubmit, onCancel }: Props) {
  const fieldRef = useRef<HTMLInputElement>(null);

  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setPassword("");
    setError(null);
  }, [open]);

  function handleSubmit() {
    if (password.length < 8) {
      setError("비밀번호는 8자 이상이어야 합니다.");
      return;
    }
    setError(null);
    onSubmit(password);
  }

  return (
    <AppModal
      open={open}
      onClose={onCancel}
      title={`비밀번호 변경 — ${username ?? ""}`}
      showCloseButton={false}
      style={{ maxWidth: "420px" }}
      initialFocusRef={fieldRef}
    >
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
    </AppModal>
  );
}
