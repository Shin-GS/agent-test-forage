// Vitest 전역 setup: jest-dom 매처(toBeInTheDocument 등) 등록 + 각 테스트 후 DOM 정리.
import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

afterEach(() => {
  cleanup();
});
