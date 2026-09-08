// 카드에서 "레시피 단일 실행"을 시작하는 공통 훅.
// ExecutionModeCard(실행 모드 선택)와 CandidatesCard(후보 선택 → 실행)가 같은 실행 경로를 쓰므로
// 중복을 줄이기 위해 여기로 모은다. 실행 시작 → pendingInputs 있으면 액션 피커, 없으면 러너 구동.
//
// 대화방 락(conversationStatus!=="idle") 체크 + 409(CONVERSATION_BUSY) 토스트 처리 + 이 세션
// 중복 실행 방지(started)까지 담당한다. 각 카드는 running/started/error 상태만 소비하면 된다.

import { useState } from "react";
import { ApiError, executionsApi } from "../../api";
import { runExecution } from "../../services/executionRunner";
import { applyRunResult } from "../../services/executionResult";
import { useChatStore } from "../../store/chatStore";
import { useToastStore } from "../../store/toastStore";
import type { ConversationRuntimeStatus } from "../../store/types";

/** 대화방 처리 중 안내 문구 (상태별). ExecutionModeCard/PlanCard 와 동일 규칙 */
export function conversationLockMessage(status: ConversationRuntimeStatus): string {
  switch (status) {
    case "ai_responding":
      return "AI가 응답 중이에요. 완료 후 다시 시도해주세요.";
    case "executing":
      return "레시피 실행 중이에요. 완료 후 다시 시도해주세요.";
    case "input_waiting":
      return "입력 대기 중이에요. 먼저 진행 중인 작업을 마쳐주세요.";
    default:
      return "현재 작업이 진행 중이에요. 완료 후 다시 시도해주세요.";
  }
}

export interface StartRecipeRunArgs {
  /** 실행할 레시피 id */
  recipeId: number;
  /** 실행 모드 (AUTO/MANUAL). 후보 선택은 AUTO */
  mode: string;
  /** AI 가 발화에서 추출한 값 (실행 시작 시 initialContext 시드). 없으면 생략 */
  extractedValues?: Record<string, unknown>;
  /**
   * 촉발 파트 id (실행 요청 messageId 로 전달 → BE CONSUMED 처리, messaging.md).
   * 없으면 생략(하위호환).
   */
  partId?: number;
}

export interface UseRecipeRun {
  /** 실행 진행 중 (버튼 라벨/스피너용) */
  running: boolean;
  /** 이 세션에서 실행을 시작했는지 (중복 방지 + "실행됨" 배지) */
  started: boolean;
  /** 인증 대기(authPause) 중인지 (배지 문구 분기) */
  authPending: boolean;
  /** 실행 시작 실패 메시지 (409 는 토스트로 처리하므로 여기 안 담음) */
  error: string | null;
  /**
   * 레시피 실행을 시작한다. 대화방 락/중복이면 아무것도 하지 않고 반환한다.
   * consumed(파트 소진) 이거나 conversationId 가 없으면 호출측에서 미리 disabled 로 막는다.
   */
  startRun: (args: StartRecipeRunArgs) => Promise<void>;
}

/**
 * @param consumed 파트가 이미 CONSUMED/CANCELLED 인지 (새로고침 복원 시 재실행 차단)
 */
export function useRecipeRun(consumed: boolean): UseRecipeRun {
  const conversationId = useChatStore((state) => state.currentConversationId);
  const conversationStatus = useChatStore((state) => state.conversationStatus);
  const setActionPicker = useChatStore((state) => state.setActionPicker);
  const authPause = useChatStore((state) => state.authPause);
  const showToast = useToastStore((state) => state.show);

  const [running, setRunning] = useState(false);
  const [started, setStarted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 인증 대기 배지는 store.authPause 로 판단한다(AuthRequiredCard 가 재개 완료 시 해제하면
  // 배지도 자동으로 "실행됨" 으로 바뀐다 — 로컬 state 로는 그 해제를 못 봄).
  const authPending = authPause != null && authPause.conversationId === conversationId;

  const startRun = async ({ recipeId, mode, extractedValues, partId }: StartRecipeRunArgs) => {
    // 이미 실행 중/시작함/소진/대화방 없음이면 무시(중복 방지).
    if (running || started || consumed || conversationId == null) return;
    // 대화방 락: 이미 처리 중이면 새 실행을 막고 안내한다(대화방 단위 락).
    if (conversationStatus !== "idle") {
      showToast(conversationLockMessage(conversationStatus), "warning");
      return;
    }
    const convId = conversationId;
    setRunning(true);
    setError(null);
    try {
      const execution = await executionsApi.startExecution(convId, {
        recipeId,
        mode,
        // AI 가 발화에서 추출한 값을 실행 시작 시 시드(BE 가 recipe 변수 기본값과 병합).
        initialContext: extractedValues,
        // 촉발 파트를 CONSUMED 처리하도록 파트 id 전달(messaging.md — messageId 필드).
        messageId: partId,
      });

      // 입력 미충족: BE 가 pendingInputs(수집할 변수)를 준다 → 러너 대신 액션 피커를 띄운다.
      if ((execution.pendingInputs?.length ?? 0) > 0) {
        setActionPicker({
          conversationId: convId,
          executionId: execution.id,
          stepIndex: -1, // pre-run 일괄 수집
          variables: execution.pendingInputs ?? [],
          mode,
          partId: execution.actionPickerPartId ?? undefined,
        });
        setStarted(true);
        return;
      }

      // 실행 엔진 구동. 진행 상태는 SSE 로 스토어가 갱신한다.
      const result = await runExecution(execution, { mode });
      // 결과 후처리(AUTH_REQUIRED면 인증 안내 카드). ActionPicker 재개 경로와 공통 로직 사용.
      await applyRunResult(execution, result, mode);
      // 실행이 시작되면(성공/실패/인증대기 무관) 이 카드로 재실행하지 않는다.
      setStarted(true);
    } catch (err) {
      // 대화방 락 경합(409 CONVERSATION_BUSY): 다른 탭/요청이 선점한 경우. 토스트로 안내.
      if (err instanceof ApiError && err.status === 409) {
        showToast("현재 대화방에 진행 중인 작업이 있어요. 완료 후 다시 시도해주세요.", "warning");
      } else {
        setError(err instanceof Error ? err.message : "실행에 실패했습니다");
      }
    } finally {
      setRunning(false);
    }
  };

  return { running, started, authPending, error, startRun };
}
