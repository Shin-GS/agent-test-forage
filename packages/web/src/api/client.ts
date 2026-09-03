// 에이전트 서버 API 호출용 fetch 래퍼.
// - base URL: import.meta.env.VITE_API_BASE_URL (기본 http://localhost:8080)
// - 모든 경로에 /api/v1 프리픽스
// - credentials: 'include' 기본 (에이전트 API 는 cross-origin 세션 사용)
// - 에러 응답은 { error: { code, message } } 파싱해 ApiError 로 throw

import type { ApiErrorBody } from "./types";

const RAW_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
/** 끝 슬래시 제거한 서버 origin */
const BASE_URL = RAW_BASE_URL.replace(/\/+$/, "");
/** /api/v1 프리픽스를 붙인 API base */
export const API_BASE = `${BASE_URL}/api/v1`;

/** 서버 표준 에러를 표현하는 예외 */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly traceId?: string;

  constructor(code: string, message: string, status: number, traceId?: string) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
    this.traceId = traceId;
  }
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  /** JSON 바디 (직렬화됨) */
  body?: unknown;
  /** 쿼리 파라미터 */
  query?: Record<string, string | number | boolean | null | undefined>;
  signal?: AbortSignal;
}

function buildUrl(path: string, query?: RequestOptions["query"]): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const url = new URL(`${API_BASE}${normalizedPath}`);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null) {
        url.searchParams.set(key, String(value));
      }
    }
  }
  return url.toString();
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== "object" || value === null) return false;
  const error = (value as { error?: unknown }).error;
  if (typeof error !== "object" || error === null) return false;
  return (
    typeof (error as { code?: unknown }).code === "string" &&
    typeof (error as { message?: unknown }).message === "string"
  );
}

/**
 * 에이전트 서버로 요청을 보내고 JSON 응답을 반환한다.
 * 204/빈 본문은 undefined 로 반환한다.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, query, signal } = options;

  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(buildUrl(path, query), {
    method,
    headers,
    credentials: "include",
    body: body !== undefined ? JSON.stringify(body) : undefined,
    signal,
  });

  // 본문 파싱 (JSON 이 아니면 null)
  const text = await response.text();
  let parsed: unknown = null;
  if (text.length > 0) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = null;
    }
  }

  if (!response.ok) {
    if (isApiErrorBody(parsed)) {
      throw new ApiError(
        parsed.error.code,
        parsed.error.message,
        response.status,
        parsed.error.traceId
      );
    }
    throw new ApiError(
      "UNKNOWN_ERROR",
      `Request failed with status ${response.status}`,
      response.status
    );
  }

  return parsed as T;
}
