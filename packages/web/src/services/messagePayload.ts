// 메시지 파트 payload 안전 파싱 (messaging.md payloadJson 계약).
// part.payload 는 BE 가 payloadJson 을 파싱해 내려준 객체(any)다.
// kind/schemaVersion 을 검증하고, FE 가 모르는 상위 schemaVersion 이면 null 을 반환해
// 호출측이 content 텍스트로 폴백하게 한다.

import type {
  InvestigateProgressPayload,
  ProgressPayload,
  ReferencesPayload,
  ResultPayload,
} from "../api/types";
import { SUPPORTED_PAYLOAD_SCHEMA_VERSION } from "../api/types";

/** schemaVersion 이 FE 가 아는 범위인지 (모르면 폴백) */
function isSupportedVersion(payload: { schemaVersion?: number }): boolean {
  const v = payload.schemaVersion;
  if (typeof v !== "number") return true; // 버전 미표기는 허용(레거시)
  return v <= SUPPORTED_PAYLOAD_SCHEMA_VERSION;
}

/**
 * kind 별 고정 schemaVersion 검증 (v1 고정 payload 용).
 * investigate_progress/references 는 v1 만 존재하므로, 미래 상위 버전(v2+)이 오면 폴백한다.
 * 버전 미표기(레거시)는 허용.
 */
function isVersionAtMost(payload: { schemaVersion?: number }, max: number): boolean {
  const v = payload.schemaVersion;
  if (typeof v !== "number") return true; // 버전 미표기는 허용(레거시)
  return v <= max;
}

/** payload 가 PROGRESS payload 면 반환, 아니면 null (→ content 폴백) */
export function asProgressPayload(payload: unknown): ProgressPayload | null {
  if (!payload || typeof payload !== "object") return null;
  const p = payload as Partial<ProgressPayload>;
  if (p.kind !== "progress") return null;
  if (!isSupportedVersion(p)) return null;
  // schemaVersion 2: recipes 그룹 구조. recipes 배열이 진실이다.
  if (!Array.isArray(p.recipes)) return null;
  return p as ProgressPayload;
}

/** payload 가 RESULT payload 면 반환, 아니면 null (→ content 폴백) */
export function asResultPayload(payload: unknown): ResultPayload | null {
  if (!payload || typeof payload !== "object") return null;
  const p = payload as Partial<ResultPayload>;
  if (p.kind !== "result") return null;
  if (!isSupportedVersion(p)) return null;
  // schemaVersion 2: recipes 배열 구조.
  if (!Array.isArray(p.recipes)) return null;
  return p as ResultPayload;
}

/** payload 가 INVESTIGATE_PROGRESS payload 면 반환, 아니면 null (→ content 폴백) */
export function asInvestigateProgressPayload(
  payload: unknown
): InvestigateProgressPayload | null {
  if (!payload || typeof payload !== "object") return null;
  const p = payload as Partial<InvestigateProgressPayload>;
  if (p.kind !== "investigate_progress") return null;
  // investigate_progress 는 v1 고정. 상위 버전(v2+)이면 폴백.
  if (!isVersionAtMost(p, 1)) return null;
  // steps 배열이 없으면 렌더할 게 없으므로 폴백(빈 배열은 허용 — 시작 직후 상태).
  if (!Array.isArray(p.steps)) return null;
  if (typeof p.status !== "string") return null;
  return p as InvestigateProgressPayload;
}

/**
 * payload 가 references payload 면 반환, 아니면 null.
 * references 는 별도 REFERENCES 파트의 payload 다. 파싱 실패 시 호출측은 references 섹션만 생략한다.
 */
export function asReferencesPayload(payload: unknown): ReferencesPayload | null {
  if (!payload || typeof payload !== "object") return null;
  const p = payload as Partial<ReferencesPayload>;
  if (p.kind !== "references") return null;
  // references 는 v1 고정. 상위 버전(v2+)이면 폴백.
  if (!isVersionAtMost(p, 1)) return null;
  if (!Array.isArray(p.references) || p.references.length === 0) return null;
  return p as ReferencesPayload;
}
