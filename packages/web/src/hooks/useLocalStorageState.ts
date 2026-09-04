// localStorage 동기화 상태 훅.
// - JSON 파싱 실패/이상값이면 default 로 폴백한다(크래시 방지).
// - options.sanitize 로 읽어온 값을 검증/보정(숫자 폭 clamp 등)할 수 있다.
// - SSR/비브라우저 환경(window undefined)에서도 안전하게 동작한다.

import { useCallback, useEffect, useState } from "react";

interface Options<T> {
  /** 파싱된 값을 검증/보정. 잘못된 값이면 default 로 대체하거나 clamp 한다. */
  sanitize?: (value: T) => T;
}

/** localStorage 값을 안전하게 읽는다. 실패 시 default 반환. */
function readStorage<T>(key: string, fallback: T, sanitize?: (value: T) => T): T {
  if (typeof window === "undefined") return fallback;
  try {
    const raw = window.localStorage.getItem(key);
    if (raw == null) return fallback;
    const parsed = JSON.parse(raw) as T;
    return sanitize ? sanitize(parsed) : parsed;
  } catch {
    // JSON 파싱 실패/접근 불가 → 기본값
    return fallback;
  }
}

/**
 * localStorage 에 저장되는 상태.
 * @returns [value, setValue] — setValue 는 useState 와 동일하게 값 또는 updater 를 받는다.
 */
export function useLocalStorageState<T>(
  key: string,
  defaultValue: T,
  options?: Options<T>
): [T, (next: T | ((prev: T) => T)) => void] {
  const sanitize = options?.sanitize;

  const [state, setState] = useState<T>(() => readStorage(key, defaultValue, sanitize));

  // 상태 변경 시 localStorage 에 반영(쓰기 실패는 조용히 무시 — 사생활 모드 등).
  useEffect(() => {
    if (typeof window === "undefined") return;
    try {
      window.localStorage.setItem(key, JSON.stringify(state));
    } catch {
      // 저장 실패 무시
    }
  }, [key, state]);

  const setValue = useCallback(
    (next: T | ((prev: T) => T)) => {
      setState((prev) => {
        const resolved = typeof next === "function" ? (next as (p: T) => T)(prev) : next;
        return sanitize ? sanitize(resolved) : resolved;
      });
    },
    [sanitize]
  );

  return [state, setValue];
}

/** 숫자를 [min, max] 로 clamp. NaN/무한대면 fallback 반환. */
export function clampNumber(value: number, min: number, max: number, fallback: number): number {
  if (typeof value !== "number" || !Number.isFinite(value)) return fallback;
  return Math.min(max, Math.max(min, value));
}
