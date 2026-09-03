// 인증 필요(401/403) 안내 카드 (디자인 명세 chat.html Case 4).
// 레시피 실행 중 인증 에러로 중단되면 표시된다.
// - 서비스명 + 로그인 링크(새 탭) — 대화 컨텍스트 이탈 방지
// - "로그인 완료 — 계속 진행" → 중단된 스텝부터 재개(이미 성공한 스텝은 재실행 안 함)
//
// authPause 는 chatStore 의 클라이언트 사이드 상태다(execution/resumeState 는 직렬화 불가한 런타임 객체).

import { useState } from "react";
import type { ExecutionResponse } from "../../api/types";
import { runExecution } from "../../services/executionRunner";
import type { RunExecutionOptions } from "../../services/executionRunner";
import { useChatStore } from "../../store/chatStore";

export function AuthRequiredCard() {
  const authPause = useChatStore((state) => state.authPause);
  const currentConversationId = useChatStore((state) => state.currentConversationId);
  const setAuthPause = useChatStore((state) => state.setAuthPause);
  const [resuming, setResuming] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 현재 대화방의 인증 대기 상태일 때만 표시
  if (!authPause || authPause.conversationId !== currentConversationId) {
    return null;
  }

  const statusLabel = authPause.httpStatus === 403 ? "권한이 없습니다" : "로그인이 필요합니다";

  const handleContinue = async () => {
    if (resuming) return;
    setResuming(true);
    setError(null);
    try {
      const execution = authPause.execution as ExecutionResponse;
      const resumeState = authPause.resumeState as NonNullable<RunExecutionOptions["resume"]>;
      const result = await runExecution(execution, { mode: authPause.mode, resume: resumeState });

      if (result.outcome === "AUTH_REQUIRED" && result.auth) {
        // 여전히 인증 실패(로그인 안 됨 등) — 재개 상태만 갱신하고 카드 유지
        setAuthPause({
          ...authPause,
          httpStatus: result.auth.httpStatus,
          resumeState: result.auth.resumeState,
        });
        setError("아직 로그인되지 않았습니다. 로그인 후 다시 시도해주세요.");
      } else {
        // 재개 성공/종료 — 인증 대기 해제(진행/완료는 SSE 로 갱신됨)
        setAuthPause(null);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "계속 진행에 실패했습니다");
    } finally {
      setResuming(false);
    }
  };

  return (
    <div className="message message--ai">
      <div className="message__avatar">🤖</div>
      <div className="message__content" style={{ maxWidth: "100%", flex: 1 }}>
        <div className="alert alert--warning">
          <div style={{ width: "100%" }}>
            <strong>
              ⚠️ {authPause.serviceName ? `${authPause.serviceName}에 ` : ""}
              {statusLabel}
            </strong>

            {authPause.loginProfiles.length > 0 && (
              <div
                style={{
                  marginTop: "var(--space-2)",
                  display: "flex",
                  gap: "var(--space-2)",
                  flexWrap: "wrap",
                }}
              >
                {authPause.loginProfiles.map((profile) => (
                  <a
                    key={profile.name}
                    className="btn btn--secondary btn--sm"
                    href={profile.loginPageUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    🔗 {profile.name} 로그인 →
                  </a>
                ))}
              </div>
            )}

            <div style={{ marginTop: "var(--space-3)" }}>
              <button type="button" className="btn btn--primary btn--sm" onClick={handleContinue} disabled={resuming}>
                {resuming ? "확인 중..." : "로그인 완료 — 계속 진행"}
              </button>
            </div>

            {error && (
              <div style={{ marginTop: "var(--space-2)", fontSize: "var(--font-size-xs)", color: "var(--color-error)" }}>
                {error}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
