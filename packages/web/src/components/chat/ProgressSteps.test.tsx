// ProgressSteps 의 [이어서 실행] 재개 버튼 테스트.
// 실제 컴포넌트를 jsdom 에 마운트하고, api/서비스 모듈은 mock, zustand 스토어는 실제 setState.
//
// 검증 포인트:
// - partial/stopped(재개 가능) 진행 블록에만 [이어서 실행] 버튼이 노출된다.
// - success/failed/cancelled/running 에는 노출되지 않는다.
// - 버튼 클릭 시 resume(executionId) 호출 → pendingInputs 없으면 러너 구동(runExecution → applyRunResult).
// - resume 이 pendingInputs 를 주면 액션 피커(setActionPicker) 설정.
// - 409 에러 시 대화방 사용 중 토스트.

import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, cleanup, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// ── mock: api / 서비스 계층 ──
const resumeMock = vi.fn();
const runExecutionMock = vi.fn();
const applyRunResultMock = vi.fn();
const showToastMock = vi.fn();

vi.mock("../../api", () => ({
  // ApiError 는 instanceof 체크에 쓰이므로 실제 클래스 형태로 제공 (실제 시그니처: code, message, status)
  ApiError: class ApiError extends Error {
    code: string;
    status: number;
    constructor(code: string, message: string, status: number) {
      super(message);
      this.code = code;
      this.status = status;
    }
  },
  executionsApi: { resume: (...args: unknown[]) => resumeMock(...args) },
}));

vi.mock("../../services/executionRunner", () => ({
  runExecution: (...args: unknown[]) => runExecutionMock(...args),
}));
vi.mock("../../services/executionResult", () => ({
  applyRunResult: (...args: unknown[]) => applyRunResultMock(...args),
}));

import { ProgressSteps } from "./ProgressSteps";
import { useChatStore } from "../../store/chatStore";
import { useToastStore } from "../../store/toastStore";
import { ApiError } from "../../api";
import type { ProgressPayload } from "../../api/types";

/** 주어진 overallStatus 로 단일 레시피 진행 payload 를 만든다 */
function makePayload(overallStatus: string): ProgressPayload {
  return {
    kind: "progress",
    schemaVersion: 2,
    executionId: 77,
    title: "이력서 작성",
    overallStatus,
    recipeProgress: { current: 1, total: 1 },
    recipes: [
      {
        sequence: 0,
        recipeName: "이력서 작성",
        status: overallStatus === "partial" || overallStatus === "stopped" ? "failed" : "success",
        summary: null,
        steps: [{ index: 0, name: "스텝1", status: "success", summary: null }],
      },
    ],
  };
}

beforeEach(() => {
  resumeMock.mockReset();
  runExecutionMock.mockReset();
  applyRunResultMock.mockReset();
  showToastMock.mockReset();
  runExecutionMock.mockResolvedValue({ outcome: "SUCCESS" });
  applyRunResultMock.mockResolvedValue(undefined);
  // toast show 를 스파이로 교체
  useToastStore.setState({ show: showToastMock });
  useChatStore.setState({ currentConversationId: 1, conversationStatus: "idle", actionPicker: null });
  cleanup();
});

describe("ProgressSteps 재개 버튼", () => {
  it("partial 상태에서 [이어서 실행] 버튼이 노출된다", () => {
    render(<ProgressSteps payload={makePayload("partial")} />);
    expect(screen.getByRole("button", { name: "이어서 실행" })).toBeInTheDocument();
  });

  it("stopped 상태에서 [이어서 실행] 버튼이 노출된다", () => {
    render(<ProgressSteps payload={makePayload("stopped")} />);
    expect(screen.getByRole("button", { name: "이어서 실행" })).toBeInTheDocument();
  });

  it("success/failed/cancelled/running 에는 버튼이 노출되지 않는다", () => {
    for (const status of ["success", "failed", "cancelled", "running"]) {
      cleanup();
      render(<ProgressSteps payload={makePayload(status)} />);
      expect(screen.queryByRole("button", { name: "이어서 실행" })).toBeNull();
    }
  });

  it("클릭 시 resume 호출 → 러너 구동(runExecution → applyRunResult)", async () => {
    const resumed = { id: 77, conversationId: 1, pendingInputs: [] };
    resumeMock.mockResolvedValue(resumed);
    render(<ProgressSteps payload={makePayload("partial")} />);

    await userEvent.click(screen.getByRole("button", { name: "이어서 실행" }));

    await waitFor(() => expect(resumeMock).toHaveBeenCalledWith(77));
    expect(runExecutionMock).toHaveBeenCalledWith(resumed, { mode: "AUTO" });
    expect(applyRunResultMock).toHaveBeenCalledWith(resumed, { outcome: "SUCCESS" }, "AUTO");
  });

  it("resume 이 pendingInputs 를 주면 액션 피커를 설정한다", async () => {
    const variables = [{ key: "name", label: "이름", type: "text", required: true }];
    resumeMock.mockResolvedValue({ id: 77, conversationId: 1, pendingInputs: variables });
    render(<ProgressSteps payload={makePayload("partial")} />);

    await userEvent.click(screen.getByRole("button", { name: "이어서 실행" }));

    await waitFor(() => {
      const picker = useChatStore.getState().actionPicker;
      expect(picker).not.toBeNull();
      expect(picker?.executionId).toBe(77);
      expect(picker?.stepIndex).toBe(-1);
    });
    expect(runExecutionMock).not.toHaveBeenCalled();
  });

  it("409 에러 시 대화방 사용 중 토스트를 띄운다", async () => {
    resumeMock.mockRejectedValue(new ApiError("CONVERSATION_BUSY", "busy", 409));
    render(<ProgressSteps payload={makePayload("stopped")} />);

    await userEvent.click(screen.getByRole("button", { name: "이어서 실행" }));

    await waitFor(() =>
      expect(showToastMock).toHaveBeenCalledWith(
        expect.stringContaining("진행 중인 작업"),
        "warning"
      )
    );
  });
});
