// 로그인 페이지 (라우트 "/login", 레이아웃 밖).
// 디자인 명세: docs/design/web/login.html (로고 + 아이디 + 비밀번호 + 로그인 버튼).
// 상태:
//  - 기본: 입력이 비어 로그인 버튼 disabled
//  - 입력 중: 아이디/비밀번호 채워지면 버튼 활성
//  - 로딩: input/버튼 disabled + 스피너 "로그인 중..."
//  - 실패: 상단 alert--error + 비밀번호 input--error
// components.css 글로벌 클래스만 사용(색/간격 하드코딩 금지 — 스타일은 login.html 대응 최소 인라인은 토큰 변수).

import { useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { authApi, ApiError } from "../api";
import { useAuthStore } from "../store/authStore";

/** 로그인 후 돌아갈 원래 경로 (라우트 가드가 state.from 으로 보존) */
interface LocationState {
  from?: string;
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const setUser = useAuthStore((s) => s.setUser);

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSubmit = username.trim() !== "" && password !== "" && !loading;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setLoading(true);
    setError(null);
    try {
      const user = await authApi.login({ username: username.trim(), password });
      setUser(user);
      // 원래 목적지가 있으면 그리로, 없으면 홈으로.
      const from = (location.state as LocationState | null)?.from;
      navigate(from && from !== "/login" ? from : "/", { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("로그인에 실패했습니다. 잠시 후 다시 시도해주세요.");
      }
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">
          <div className="login-logo__icon">⚡</div>
          <h1 className="login-logo__title">테스트메이트</h1>
          <p className="login-logo__subtitle">API 워크플로우 실행 플랫폼</p>
        </div>

        <form className="login-form" onSubmit={handleSubmit} noValidate>
          {error && (
            <div className="alert alert--error" role="alert" aria-live="polite">
              ✕ {error}
            </div>
          )}

          <div className="form-group">
            <label className="form-label" htmlFor="login-username">
              아이디
            </label>
            <input
              className="input"
              id="login-username"
              type="text"
              placeholder="아이디를 입력하세요"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={loading}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="login-password">
              비밀번호
            </label>
            <input
              className={`input${error ? " input--error" : ""}`}
              id="login-password"
              type="password"
              placeholder="비밀번호를 입력하세요"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading}
            />
          </div>

          <button className="btn btn--primary btn--full btn--lg" type="submit" disabled={!canSubmit}>
            {loading ? (
              <>
                <span className="spinner" /> 로그인 중...
              </>
            ) : (
              "로그인"
            )}
          </button>
        </form>
      </div>
    </div>
  );
}
