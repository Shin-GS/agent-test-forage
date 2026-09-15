// 목록 페이지 URL 필터 공통 파서/옵션.
//
// nuqs 기반 URL 상태 동기화에서 여러 목록 페이지가 공유하는 파서를 모은다.
// 원칙: docs/specs/common/page-layout.md#목록-상태와-url-목록형-페이지-공통

import { createParser } from "nuqs";

/**
 * 검색어 파서: URL에 쓸 때 앞뒤 공백을 제거(trim)한다.
 * - 공백만 입력하면 빈 문자열로 직렬화되어(→ 기본값 "") clearOnDefault 로 URL에서 제거된다.
 * - parse(URL→값)는 원본을 그대로 읽되, serialize(값→URL)에서 trim 하여 URL 위생을 지킨다.
 *   입력 중에는 컴포넌트 state 로 원문이 유지되므로 "단어 사이 공백" 타이핑에는 영향 없다.
 *
 * 사용: parseAsSearch.withDefault("").withOptions({ throttleMs: 300, clearOnDefault: true })
 */
export const parseAsSearch = createParser({
  parse: (value: string) => value,
  serialize: (value: string) => value.trim(),
  // clearOnDefault 비교용. trim 후 같으면 기본값("")과 동일로 판정 → 공백만 입력 시 URL에서 제거된다.
  eq: (a: string, b: string) => a.trim() === b.trim(),
});

/** 검색어 파라미터 공통 옵션 (디바운스 300ms + 기본값이면 URL 미기록) */
export const SEARCH_OPTIONS = { throttleMs: 300, clearOnDefault: true } as const;
