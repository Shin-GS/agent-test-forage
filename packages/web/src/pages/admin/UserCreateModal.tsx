// 관리자 — 계정 생성 폼 모달 (admin.html Case 4).
// - 아이디(3~50자, 공백 불가) + 이름(선택, 100자 이하) + 비밀번호(8자 이상) + 역할(라디오).
// - label for/id 연결, role="dialog" aria-modal aria-labelledby, ESC/배경 클릭 닫기,
//   열릴 때 첫 입력 포커스 + focus trap + 닫힐 때 트리거로 포커스 복원(ConfirmModal 패턴).
// - 클라이언트 1차 검증(빈값/길이/공백/8자) 후 onSubmit. 서버 400 은 부모가 토스트로 처리.

import { useEffect, useId, useRef, useState } from "react";
import type { CreateUserBody } from "../../api/users";

interface Props {
  open: boolean;
  /** 생성 진행 중(중복 제출 방지 + 버튼 비활성) */
  submitting: boolean;
  onSubmit: (body: CreateUserBody) => void;
  onCancel: () => void;
}

export function UserCreateModal({ open, submitting, onSubmit, onCancel }: Props) {
  const modalRef = useRef<HTMLDivElement>(null);
  const firstFieldRef = useRef<HTMLInputElement>(null);
  const titleId = useId();

  // 최신 onCancel 을 ref 로 참조 → focus trap effect 가 매 렌더 teardown/재실행되지 않도록.
  const onCancelRef = useRef(onCancel);
  onCancelRef.current = onCancel;

  const [username, setUsername] = useState("");
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<"USER" | "ADMIN">("USER");
  const [error, setError] = useState<string | null>(null);

  // 열릴 때 필드 초기화 + 첫 입력 포커스, ESC/Tab(focus trap), 닫힐 때 포커스 복원
  useEffect(() => {
    if (!open) return;
    setUsername("");
    setName("");
    setPassword("");
    setRole("USER");
    setError(null);

    const previouslyFocused = document.activeElement as HTMLElement | null;
    const raf = requestAnimationFrame(() => firstFieldRef.current?.focus());

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
    const trimmedUsername = username.trim();
    // 클라이언트 1차 검증 (서버가 최종 강제)
    if (trimmedUsername.length < 3 || trimmedUsername.length > 50) {
      setError("아이디는 3~50자여야 합니다.");
      return;
    }
    if (/\s/.test(trimmedUsername)) {
      setError("아이디에는 공백을 포함할 수 없습니다.");
      return;
    }
    if (name.trim().length > 100) {
      setError("이름은 100자 이하여야 합니다.");
      return;
    }
    if (password.length < 8) {
      setError("비밀번호는 8자 이상이어야 합니다.");
      return;
    }
    setError(null);
    const trimmedName = name.trim();
    onSubmit({
      username: trimmedUsername,
      password,
      name: trimmedName ? trimmedName : undefined,
      role,
    });
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
        style={{ maxWidth: "460px" }}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <div className="modal__header">
          <h2 id={titleId} className="modal__title">
            사용자 추가
          </h2>
        </div>
        <div className="modal__body">
          <p className="form-hint" style={{ marginBottom: "var(--space-4)" }}>
            관리자가 아이디 · 비밀번호 · 역할을 직접 지정하여 계정을 생성합니다. (셀프 회원가입 없음)
          </p>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSubmit();
            }}
            style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}
          >
            <div className="form-group">
              <label className="form-label" htmlFor="new-user-id">
                아이디
              </label>
              <input
                ref={firstFieldRef}
                className="input"
                type="text"
                id="new-user-id"
                name="new-user-id"
                placeholder="아이디를 입력하세요"
                autoComplete="off"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
              <span className="form-hint">3~50자, 공백 불가.</span>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="new-user-name">
                이름{" "}
                <span style={{ color: "var(--color-text-tertiary)", fontWeight: "var(--font-weight-normal)" }}>
                  (선택)
                </span>
              </label>
              <input
                className="input"
                type="text"
                id="new-user-name"
                name="new-user-name"
                placeholder="이름을 입력하세요 (선택)"
                autoComplete="off"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
              <span className="form-hint">100자 이하.</span>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="new-user-password">
                비밀번호
              </label>
              <input
                className="input"
                type="password"
                id="new-user-password"
                name="new-user-password"
                placeholder="비밀번호를 입력하세요"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <span className="form-hint">8자 이상. 관리자가 지정한 비밀번호로 즉시 로그인할 수 있습니다.</span>
            </div>

            <fieldset className="form-group" style={{ border: "none", padding: 0, margin: 0 }}>
              <legend className="form-label" style={{ padding: 0 }}>
                역할
              </legend>
              <div className="admin-user-radio-group">
                <label className="admin-user-radio-option">
                  <input
                    type="radio"
                    id="role-user"
                    name="new-user-role"
                    value="user"
                    checked={role === "USER"}
                    onChange={() => setRole("USER")}
                  />
                  일반 사용자
                </label>
                <label className="admin-user-radio-option">
                  <input
                    type="radio"
                    id="role-admin"
                    name="new-user-role"
                    value="admin"
                    checked={role === "ADMIN"}
                    onChange={() => setRole("ADMIN")}
                  />
                  관리자
                </label>
              </div>
            </fieldset>

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
            생성
          </button>
        </div>
      </div>
    </div>
  );
}
