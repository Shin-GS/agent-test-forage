// 실행 결과(RunResult) 후처리 공통 로직.
// ExecutionModeCard(최초 실행)와 ActionPicker(입력 후 재개) 모두 runExecution 결과를 동일하게
// 처리해야 하므로 여기로 모은다. 특히 재개 실행에서도 401(AUTH_REQUIRED)이 날 수 있어,
// 두 경로 모두 인증 안내 카드를 띄워야 한다(누락 시 실행이 조용히 멈춤).

import { specsApi } from "../api";
import type { ExecutionResponse } from "../api/types";
import type { RunResult } from "./executionRunner";
import { useChatStore } from "../store/chatStore";

/**
 * runExecution 결과를 스토어에 반영한다.
 * - AUTH_REQUIRED: 스펙에서 서비스명/로그인 프로필을 조회해 authPause 설정(인증 안내 카드).
 * - 그 외: 별도 처리 없음(진행/완료는 SSE 로 갱신).
 */
export async function applyRunResult(
  execution: ExecutionResponse,
  result: RunResult,
  mode: string
): Promise<void> {
  if (result.outcome !== "AUTH_REQUIRED" || !result.auth) {
    return;
  }
  const store = useChatStore.getState();
  let serviceName: string | null = null;
  let loginProfiles: { name: string; loginPageUrl: string }[] = [];
  try {
    const spec = await specsApi.getSpec(execution.apiSpecId);
    serviceName = spec.name;
    loginProfiles = (spec.authProfiles ?? [])
      .filter((p) => !!p.loginPageUrl)
      .map((p) => ({ name: p.name, loginPageUrl: p.loginPageUrl as string }));
  } catch {
    // 스펙 조회 실패 시에도 안내 카드는 띄운다(로그인 링크만 비어있음)
  }
  store.setAuthPause({
    conversationId: execution.conversationId,
    httpStatus: result.auth.httpStatus,
    serviceName,
    loginProfiles,
    execution,
    resumeState: result.auth.resumeState,
    mode,
  });
}
