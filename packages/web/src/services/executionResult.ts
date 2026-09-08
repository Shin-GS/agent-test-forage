// 실행 결과(RunResult) 후처리 공통 로직.
// ExecutionModeCard(최초 실행)와 ActionPicker(입력 후 재개) 모두 runExecution 결과를 동일하게
// 처리해야 하므로 여기로 모은다. 특히 재개 실행에서도 401(AUTH_REQUIRED)이 날 수 있어,
// 두 경로 모두 인증 안내 카드를 띄워야 한다(누락 시 실행이 조용히 멈춤).

import { specsApi } from "../api";
import type { ExecutionResponse } from "../api/types";
import type { RunResult } from "./executionRunner";
import { useChatStore } from "../store/chatStore";
import { useToastStore } from "../store/toastStore";

/**
 * runExecution 결과를 스토어에 반영한다.
 * - AUTH_REQUIRED: 스펙에서 서비스명/로그인 프로필을 조회해 authPause 설정(인증 안내 카드).
 * - INPUT_REQUIRED: 다음 레시피 pre-run 필수 입력 미충족 → actionPicker 설정(레시피 사이 액션 피커).
 * - STALLED: 러너가 진행 불가로 중단(재조회/보고 실패). BE가 EXECUTING 이면 대화방 락이 남으므로
 *   사용자에게 토스트로 새로고침을 안내한다(FE는 complete 를 못 부르는 계약).
 * - 그 외: 별도 처리 없음(진행/완료는 SSE 로 갱신).
 */
export async function applyRunResult(
  execution: ExecutionResponse,
  result: RunResult,
  mode: string
): Promise<void> {
  // 러너가 진행 불가로 중단 → 상태 갱신 실패. 사용자에게 안내(락이 남을 수 있음).
  if (result.outcome === "STALLED") {
    useToastStore.getState().show(
      "실행 상태를 갱신하지 못했습니다. 페이지를 새로고침 해주세요.",
      "error"
    );
    return;
  }

  // 플랜 레시피 전이 시 다음 레시피 pre-run 필수 입력 미충족 → 액션 피커(레시피 사이 입력, Case 18).
  if (result.outcome === "INPUT_REQUIRED" && result.input) {
    const store = useChatStore.getState();
    store.setActionPicker({
      conversationId: execution.conversationId,
      executionId: result.input.executionId,
      stepIndex: -1, // pre-run 일괄 수집
      variables: result.input.variables,
      mode,
      partId: result.input.partId ?? undefined,
    });
    return;
  }

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
