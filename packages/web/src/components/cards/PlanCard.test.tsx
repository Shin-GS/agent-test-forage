// PlanCard(플랜 제안 카드) 2단계 편집(스킵 + 순서변경) 컴포넌트 테스트.
// 실제 컴포넌트를 jsdom 에 마운트해 상호작용을 검증한다.
// api/서비스 모듈은 mock 하고, zustand 스토어는 실제 setState 로 상태를 세팅한다.

import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, within, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

// ── mock: api / 서비스 계층 ──
const startPlanMock = vi.fn();
const cancelMock = vi.fn();
const runExecutionMock = vi.fn();
const applyRunResultMock = vi.fn();

vi.mock("../../api", () => ({
  // ApiError 는 instanceof 체크에 쓰이므로 실제 클래스 형태로 제공
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message?: string) {
      super(message);
      this.status = status;
    }
  },
  conversationsApi: { cancel: (...args: unknown[]) => cancelMock(...args) },
  executionsApi: { startPlan: (...args: unknown[]) => startPlanMock(...args) },
}));

vi.mock("../../services/executionRunner", () => ({
  runExecution: (...args: unknown[]) => runExecutionMock(...args),
}));
vi.mock("../../services/executionResult", () => ({
  applyRunResult: (...args: unknown[]) => applyRunResultMock(...args),
}));

import { PlanCard } from "./ListCards";
import { useChatStore } from "../../store/chatStore";
import type { PlanCard as PlanCardMeta } from "../../api/types";

const CARD: PlanCardMeta = {
  cardType: "plan",
  recipeIds: [10, 20, 30],
  recipes: [
    { recipeId: 10, recipeName: "이력서 작성", serviceName: "사람인", inputPreview: [] },
    { recipeId: 20, recipeName: "포지션 탐색", serviceName: "사람인", inputPreview: [] },
    { recipeId: 30, recipeName: "입사지원", serviceName: "사람인", inputPreview: [] },
  ],
  rationale: "입사지원은 이력서·포지션이 선행돼야 해 3단계로 구성",
};

/** 각 레시피 행 요소를 이름으로 찾는다 (plan-recipe 컨테이너 기준) */
function recipeRow(name: string): HTMLElement {
  const nameEl = screen.getByText(name);
  const row = nameEl.closest(".plan-recipe");
  if (!row) throw new Error(`row not found for ${name}`);
  return row as HTMLElement;
}

/**
 * 행의 순서번호를 반환한다. 스킵된 행은 번호 미부여 —
 * 컴포넌트는 빈 span(.plan-recipe__order--empty)을 렌더하므로 텍스트가 비면 null 로 취급한다.
 */
function orderText(name: string): string | null {
  const row = recipeRow(name);
  const order = row.querySelector(".plan-recipe__order");
  const text = (order?.textContent ?? "").trim();
  return text === "" ? null : text;
}

function isSkipped(name: string): boolean {
  return recipeRow(name).classList.contains("plan-recipe--skipped");
}

beforeEach(() => {
  startPlanMock.mockReset();
  cancelMock.mockReset();
  runExecutionMock.mockReset();
  applyRunResultMock.mockReset();
  // 기본: 실행이 pendingInputs 없이 성공하는 흐름
  startPlanMock.mockResolvedValue({ id: 999, pendingInputs: [] });
  runExecutionMock.mockResolvedValue({ outcome: "SUCCESS" });
  applyRunResultMock.mockResolvedValue(undefined);
  // 스토어 상태: idle + 대화방 지정
  useChatStore.setState({ currentConversationId: 1, conversationStatus: "idle" });
  cleanup();
});

describe("PlanCard 편집 (스킵 + 순서변경)", () => {
  it("초기 렌더: 카운터 '전체 3단계', 순서번호 1/2/3", () => {
    render(<PlanCard card={CARD} />);
    expect(screen.getByText("전체 3단계")).toBeInTheDocument();
    expect(orderText("이력서 작성")).toBe("1");
    expect(orderText("포지션 탐색")).toBe("2");
    expect(orderText("입사지원")).toBe("3");
  });

  it("스킵 토글: 체크 해제 시 '(제외됨)' + 카운터/순서 재매김", async () => {
    const user = userEvent.setup();
    render(<PlanCard card={CARD} />);

    // "포지션 탐색" 체크박스 해제
    const checkbox = screen.getByRole("checkbox", { name: "포지션 탐색 실행 포함" });
    await user.click(checkbox);

    expect(isSkipped("포지션 탐색")).toBe(true);
    expect(within(recipeRow("포지션 탐색")).getByText("(제외됨)")).toBeInTheDocument();
    // 카운터: 3 중 2 실행
    expect(screen.getByText("전체 3단계 중 2단계 실행")).toBeInTheDocument();
    // 순서 재매김: 이력서=1, 입사지원=2, 포지션(스킵)=번호 없음
    expect(orderText("이력서 작성")).toBe("1");
    expect(orderText("입사지원")).toBe("2");
    expect(orderText("포지션 탐색")).toBeNull();
  });

  it("순서변경: ↓ 클릭 시 순서번호가 뒤로 밀림", async () => {
    const user = userEvent.setup();
    render(<PlanCard card={CARD} />);

    // "이력서 작성"을 아래로 이동 → 이력서가 2, 포지션이 1
    const down = screen.getByRole("button", { name: "이력서 작성 아래로 이동" });
    await user.click(down);

    expect(orderText("포지션 탐색")).toBe("1");
    expect(orderText("이력서 작성")).toBe("2");
    expect(orderText("입사지원")).toBe("3");
  });

  it("경계: 첫 행 ↑ 비활성, 마지막 행 ↓ 비활성", () => {
    render(<PlanCard card={CARD} />);
    expect(screen.getByRole("button", { name: "이력서 작성 위로 이동" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "입사지원 아래로 이동" })).toBeDisabled();
    // 중간 행은 둘 다 활성
    expect(screen.getByRole("button", { name: "포지션 탐색 위로 이동" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "포지션 탐색 아래로 이동" })).toBeEnabled();
  });

  it("최소 1개 가드: 전부 스킵하면 [자동 실행] 비활성 + 안내", async () => {
    const user = userEvent.setup();
    render(<PlanCard card={CARD} />);

    await user.click(screen.getByRole("checkbox", { name: "이력서 작성 실행 포함" }));
    await user.click(screen.getByRole("checkbox", { name: "포지션 탐색 실행 포함" }));
    await user.click(screen.getByRole("checkbox", { name: "입사지원 실행 포함" }));

    expect(screen.getByRole("button", { name: "플랜 자동 실행" })).toBeDisabled();
    expect(screen.getByText(/최소 1개 선택/)).toBeInTheDocument();
  });

  it("자동 실행: 스킵/순서변경이 반영된 recipeIds 로 startPlan 호출", async () => {
    const user = userEvent.setup();
    render(<PlanCard card={CARD} />);

    // 포지션(20) 스킵
    await user.click(screen.getByRole("checkbox", { name: "포지션 탐색 실행 포함" }));
    // 이력서(10)를 아래로 → 순서: 입사지원(30) 자리? 아니, 배열 전체 기준 swap:
    //   초기 [10,20,30] → 이력서 아래로 → [20,10,30]. 포지션(20)은 스킵됨.
    await user.click(screen.getByRole("button", { name: "이력서 작성 아래로 이동" }));

    // 자동 실행
    await user.click(screen.getByRole("button", { name: "플랜 자동 실행" }));

    // startPlan 은 (conversationId, { recipeIds, mode }) 로 호출됨.
    // 배열 [20,10,30] 중 included=true 인 10,30 만 화면 순서대로 → [10, 30]
    expect(startPlanMock).toHaveBeenCalledTimes(1);
    const [convId, payload] = startPlanMock.mock.calls[0];
    expect(convId).toBe(1);
    expect(payload.recipeIds).toEqual([10, 30]);
    expect(payload.mode).toBe("AUTO");
  });

  it("자동 실행: 순서변경만 하면 재정렬된 순서대로 recipeIds 전달", async () => {
    const user = userEvent.setup();
    render(<PlanCard card={CARD} />);

    // 입사지원(30)을 위로 → [10,20,30] → 30과 20 swap → [10,30,20]
    await user.click(screen.getByRole("button", { name: "입사지원 위로 이동" }));
    await user.click(screen.getByRole("button", { name: "플랜 자동 실행" }));

    const [, payload] = startPlanMock.mock.calls[0];
    expect(payload.recipeIds).toEqual([10, 30, 20]);
  });

  // ── 회귀 방지: announce 의 'N번째'는 화면 실행 순서번호(included 재매김)와 일치해야 한다 ──
  it("앞에 스킵 행이 있어도 announce 'N번째'가 화면 순서번호와 일치한다", async () => {
    const user = userEvent.setup();
    render(<PlanCard card={CARD} />);

    // 이력서(맨 위) 스킵 → [이력서(skip), 포지션(1), 입사지원(2)]
    await user.click(screen.getByRole("checkbox", { name: "이력서 작성 실행 포함" }));
    // 입사지원(배열 idx=2)을 위로 → 배열 [이력서(skip), 입사지원, 포지션]
    //   화면 실행 순서번호: 입사지원=1 (스킵 뒤 첫 included)
    await user.click(screen.getByRole("button", { name: "입사지원 위로 이동" }));

    const announceText = document.querySelector('[role="status"]')?.textContent ?? "";
    // 화면 순서번호와 announce 가 모두 1 이어야 한다
    expect(orderText("입사지원")).toBe("1");
    expect(announceText).toContain("입사지원");
    expect(announceText).toContain("1번째");
    expect(announceText).not.toContain("2번째");
  });

  it("스킵된 행을 이동하면 announce 가 '실행에서 제외된 항목'임을 알린다", async () => {
    const user = userEvent.setup();
    render(<PlanCard card={CARD} />);

    // 이력서 스킵 후 그 행을 아래로 이동
    await user.click(screen.getByRole("checkbox", { name: "이력서 작성 실행 포함" }));
    await user.click(screen.getByRole("button", { name: "이력서 작성 아래로 이동" }));

    const announceText = document.querySelector('[role="status"]')?.textContent ?? "";
    expect(announceText).toContain("제외된 항목");
  });

  it("스킵 행을 아래로 이동해도 실행 배열은 스킵 무관하게 정상", async () => {
    const user = userEvent.setup();
    render(<PlanCard card={CARD} />);

    // 이력서(맨 위) 스킵 → [이력서(skip), 포지션(1), 입사지원(2)]
    await user.click(screen.getByRole("checkbox", { name: "이력서 작성 실행 포함" }));
    // 스킵된 이력서를 아래로 → [포지션, 이력서(skip), 입사지원]
    await user.click(screen.getByRole("button", { name: "이력서 작성 아래로 이동" }));
    await user.click(screen.getByRole("button", { name: "플랜 자동 실행" }));

    // 실행 순서: 스킵 제외하므로 [포지션(20), 입사지원(30)] — 스킵 행 위치와 무관
    const [, payload] = startPlanMock.mock.calls[0];
    expect(payload.recipeIds).toEqual([20, 30]);
  });
});
