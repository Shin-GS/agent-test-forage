// MessageReferences 테스트.
// - parseSpecEndpointUrl 순수 로직: 정상/null/외부 URL/형식 불일치
// - 컴포넌트: 패턴 불일치 정적 폴백, 패턴 일치 시 클릭 가능 칩 + 인라인 확장,
//   조회 성공 렌더, 관리자 링크 분기.

import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, within, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

// ── mock: api (getSpec) ──
const getSpecMock = vi.fn();
vi.mock("../../api", () => ({
  specsApi: { getSpec: (...args: unknown[]) => getSpecMock(...args) },
}));

import { MessageReferences, parseSpecEndpointUrl, isExternalUrl } from "./MessageReferences";
import { useAuthStore } from "../../store/authStore";
import type { ReferencesPayload, SpecDetail } from "../../api/types";

function payloadWith(url: string | null): ReferencesPayload {
  return {
    kind: "references",
    schemaVersion: 1,
    references: [{ source: "api_spec", label: "POST /api/v1/users", url }],
  };
}

const SPEC: SpecDetail = {
  id: 42,
  name: "사용자 서비스",
  baseUrl: "https://api.example.com",
  status: { code: "ACTIVE", description: "활성" },
  serviceInfo: null,
  endpoints: [
    {
      id: 7,
      method: "POST",
      path: "/api/v1/users",
      summary: "회원가입",
      status: { code: "ACTIVE", description: "활성" },
      excluded: false,
      confirmRequired: false,
    },
  ],
  authProfiles: [],
  diagnostics: null,
};

function renderRefs(payload: ReferencesPayload) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <MessageReferences payload={payload} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  cleanup();
  getSpecMock.mockReset();
  useAuthStore.setState({ user: null, status: "anonymous" });
});

describe("parseSpecEndpointUrl", () => {
  it("정상 패턴을 두 정수로 파싱한다", () => {
    expect(parseSpecEndpointUrl("/specs/42/endpoints/7")).toEqual({
      apiSpecId: 42,
      endpointId: 7,
    });
  });

  it("null 이면 null", () => {
    expect(parseSpecEndpointUrl(null)).toBeNull();
  });

  it("외부 URL 이면 null", () => {
    expect(parseSpecEndpointUrl("https://demo.atlassian.net/wiki/spaces/BT/pages/1")).toBeNull();
  });

  it("형식 불일치(꼬리 세그먼트)면 null", () => {
    expect(parseSpecEndpointUrl("/specs/42/endpoints/7/extra")).toBeNull();
    expect(parseSpecEndpointUrl("/specs/42/endpoints/")).toBeNull();
    expect(parseSpecEndpointUrl("/specs/abc/endpoints/7")).toBeNull();
  });
});

describe("MessageReferences 정적 폴백", () => {
  it("url 이 null 이면 button 아닌 정적 칩으로 렌더한다", () => {
    renderRefs(payloadWith(null));
    expect(screen.queryByRole("button")).toBeNull();
    // aria-label 은 중복 낭독 방지로 제거됨 — 칩 텍스트가 렌더되는지로 검증
    expect(screen.getByText("POST /api/v1/users")).toBeTruthy();
    expect(getSpecMock).not.toHaveBeenCalled();
  });

  it("references 가 비면 아무것도 렌더하지 않는다", () => {
    const { container } = renderRefs({ kind: "references", schemaVersion: 1, references: [] });
    expect(container.firstChild).toBeNull();
  });
});

describe("isExternalUrl", () => {
  it("http/https URL 이면 true", () => {
    expect(isExternalUrl("https://demo.atlassian.net/wiki/spaces/BT/pages/42")).toBe(true);
    expect(isExternalUrl("http://x.test")).toBe(true);
  });

  it("내부 경로/null 이면 false", () => {
    expect(isExternalUrl("/specs/42/endpoints/7")).toBe(false);
    expect(isExternalUrl(null)).toBe(false);
  });
});

describe("MessageReferences 외부 링크 분기", () => {
  it("외부 https URL 이면 새 탭 링크(a[target=_blank])로 렌더한다", () => {
    renderRefs(payloadWith("https://demo.atlassian.net/wiki/spaces/BT/pages/42"));
    // 인라인 확장 button 이 아니라 <a> 링크
    expect(screen.queryByRole("button")).toBeNull();
    const link = screen.getByRole("link");
    expect(link.getAttribute("href")).toBe("https://demo.atlassian.net/wiki/spaces/BT/pages/42");
    expect(link.getAttribute("target")).toBe("_blank");
    expect(link.getAttribute("rel")).toBe("noopener noreferrer");
    expect(link.getAttribute("aria-label")).toContain("새 탭에서 열림");
    expect(getSpecMock).not.toHaveBeenCalled();
  });

  it("/specs 패턴은 외부 링크가 아니라 인라인 button 으로 남는다", () => {
    renderRefs(payloadWith("/specs/42/endpoints/7"));
    expect(screen.getByRole("button")).toBeTruthy();
    expect(screen.queryByRole("link")).toBeNull();
  });

  it("null 이면 정적 칩(링크/버튼 아님)으로 폴백한다", () => {
    renderRefs(payloadWith(null));
    expect(screen.queryByRole("button")).toBeNull();
    expect(screen.queryByRole("link")).toBeNull();
    expect(screen.getByText("POST /api/v1/users")).toBeTruthy();
  });
});

describe("MessageReferences 인라인 확장", () => {
  it("패턴 일치 시 클릭 가능한 칩(aria-expanded)으로 렌더한다", () => {
    renderRefs(payloadWith("/specs/42/endpoints/7"));
    const btn = screen.getByRole("button");
    expect(btn.getAttribute("aria-expanded")).toBe("false");
    // 펼치기 전에는 조회하지 않는다 (enabled: false)
    expect(getSpecMock).not.toHaveBeenCalled();
  });

  it("펼치면 조회 후 엔드포인트 상세를 표시한다", async () => {
    getSpecMock.mockResolvedValue(SPEC);
    const user = userEvent.setup();
    renderRefs(payloadWith("/specs/42/endpoints/7"));

    await user.click(screen.getByRole("button"));

    const region = await screen.findByRole("region");
    expect(within(region).getByText("서비스: 사용자 서비스")).toBeTruthy();
    // 엔드포인트 상세 영역 내에서만 조회 (칩 라벨과 텍스트가 겹치므로 within 으로 스코프)
    expect(within(region).getByText("POST /api/v1/users")).toBeTruthy();
    expect(within(region).getByText("회원가입")).toBeTruthy();
    expect(getSpecMock).toHaveBeenCalledWith(42);
  });

  it("조회 실패 시 안내 문구를 표시한다", async () => {
    getSpecMock.mockRejectedValue(new Error("boom"));
    const user = userEvent.setup();
    renderRefs(payloadWith("/specs/42/endpoints/7"));

    await user.click(screen.getByRole("button"));

    expect(await screen.findByText("스펙 정보를 불러올 수 없습니다")).toBeTruthy();
  });

  it("엔드포인트를 못 찾으면 별도 안내를 표시한다", async () => {
    getSpecMock.mockResolvedValue({ ...SPEC, endpoints: [] });
    const user = userEvent.setup();
    renderRefs(payloadWith("/specs/42/endpoints/7"));

    await user.click(screen.getByRole("button"));

    expect(await screen.findByText("해당 엔드포인트를 찾을 수 없습니다")).toBeTruthy();
  });
});

describe("전체 스펙 보기 링크 분기", () => {
  it("일반 사용자에겐 링크를 렌더하지 않는다", async () => {
    getSpecMock.mockResolvedValue(SPEC);
    useAuthStore.setState({
      user: { id: 1, username: "u", name: "U", role: "USER" },
      status: "authenticated",
    });
    const user = userEvent.setup();
    renderRefs(payloadWith("/specs/42/endpoints/7"));

    await user.click(screen.getByRole("button"));
    await screen.findByText("서비스: 사용자 서비스");
    expect(screen.queryByText("전체 스펙 보기 →")).toBeNull();
  });

  it("관리자에겐 /admin/specs 링크를 렌더한다", async () => {
    getSpecMock.mockResolvedValue(SPEC);
    useAuthStore.setState({
      user: { id: 1, username: "a", name: "A", role: "ADMIN" },
      status: "authenticated",
    });
    const user = userEvent.setup();
    renderRefs(payloadWith("/specs/42/endpoints/7"));

    await user.click(screen.getByRole("button"));
    const link = await screen.findByText("전체 스펙 보기 →");
    expect(link.getAttribute("href")).toBe("/admin/specs/42");
  });
});
