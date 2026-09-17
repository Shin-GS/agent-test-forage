// AuthRequiredCard 401/403 분기 회귀 방지 테스트.
// 실제 컴포넌트를 jsdom 에 마운트하고, api/서비스 모듈은 mock, zustand 스토어는 실제 setState.
//
// 검증 포인트:
// - 401: 로그인 링크 라벨 "로그인 →"(다른 계정 문구 없음), [레시피 중단] 버튼 없음
// - 403: 로그인 링크 라벨 "다른 계정으로 로그인", [레시피 중단] 버튼 있음
// - 403 [레시피 중단] 클릭 → conversationsApi.stop(conversationId) + setAuthPause(null)
// - authPause.conversationId !== currentConversationId 면 렌더 안 됨(null)

import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, cleanup, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// ── mock: api / 서비스 계층 ──
const stopMock = vi.fn();
const runExecutionMock = vi.fn();
const applyRunResultMock = vi.fn();

vi.mock("../../api", () => ({
  conversationsApi: { stop: (...args: unknown[]) => stopMock(...args) },
}));
vi.mock("../../services/executionRunner", () => ({
  runExecution: (...args: unknown[]) => runExecutionMock(...args),
}));
vi.mock("../../services/executionResult", () => ({
  applyRunResult: (...args: unknown[]) => applyRunResultMock(...args),
}));

import { AuthRequiredCard } from "./AuthRequiredCard";
import { useChatStore } from "../../store/chatStore";

/** authPause 최소 구성 헬퍼 */
function makeAuthPause(httpStatus: number, conversationId = 1) {
  return {
    conversationId,
    httpStatus,
    serviceName: "테스트서비스",
    loginProfiles: [{ name: "기본", loginPageUrl: "https://login.example.com" }],
    execution: { conversationId } as any,
    resumeState: {} as any,
    mode: "AUTO",
  };
}

beforeEach(() => {
  stopMock.mockReset();
  runExecutionMock.mockReset();
  applyRunResultMock.mockReset();
  stopMock.mockResolvedValue(undefined);
  useChatStore.setState({ currentConversationId: 1, authPause: null });
  cleanup();
});

describe("AuthRequiredCard 401/403 분기", () => {
  it("401: 로그인 링크 라벨은 '로그인 →', [레시피 중단] 버튼 없음", () => {
    useChatStore.setState({ authPause: makeAuthPause(401) });
    render(<AuthRequiredCard />);

    const link = screen.getByRole("link", { name: /기본/ });
    expect(link.textContent).toContain("로그인 →");
    expect(link.textContent).not.toContain("다른 계정");
    expect(screen.queryByRole("button", { name: "레시피 실행 중단" })).toBeNull();
  });

  it("403: 로그인 링크 라벨에 '다른 계정으로 로그인', [레시피 중단] 버튼 있음", () => {
    useChatStore.setState({ authPause: makeAuthPause(403) });
    render(<AuthRequiredCard />);

    const link = screen.getByRole("link", { name: /기본/ });
    expect(link.textContent).toContain("다른 계정으로 로그인");
    expect(screen.getByRole("button", { name: "레시피 실행 중단" })).toBeInTheDocument();
  });

  it("403 [레시피 중단] 클릭 → conversationsApi.stop(conversationId) + authPause 해제", async () => {
    useChatStore.setState({ authPause: makeAuthPause(403, 1) });
    render(<AuthRequiredCard />);

    await userEvent.click(screen.getByRole("button", { name: "레시피 실행 중단" }));

    await waitFor(() => expect(stopMock).toHaveBeenCalledWith(1));
    await waitFor(() => expect(useChatStore.getState().authPause).toBeNull());
  });

  it("authPause.conversationId 가 currentConversationId 와 다르면 렌더되지 않는다", () => {
    useChatStore.setState({ currentConversationId: 1, authPause: makeAuthPause(401, 99) });
    const { container } = render(<AuthRequiredCard />);
    expect(container).toBeEmptyDOMElement();
  });
});
