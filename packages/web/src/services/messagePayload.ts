// 메시지 payload 안전 파싱 (messaging.md payloadJson 계약).
// message.metadata 는 BE 가 payloadJson 을 파싱해 내려준 객체(any)다.
// kind/schemaVersion 을 검증하고, FE 가 모르는 상위 schemaVersion 이면 null 을 반환해
// 호출측이 content 텍스트로 폴백하게 한다.

import type { ProgressPayload, ResultPayload } from "../api/types";
import { SUPPORTED_PAYLOAD_SCHEMA_VERSION } from "../api/types";

/** schemaVersion 이 FE 가 아는 범위인지 (모르면 폴백) */
function isSupportedVersion(payload: { schemaVersion?: number }): boolean {
  const v = payload.schemaVersion;
  if (typeof v !== "number") return true; // 버전 미표기는 허용(레거시)
  return v <= SUPPORTED_PAYLOAD_SCHEMA_VERSION;
}

/** metadata 가 PROGRESS payload 면 반환, 아니면 null (→ content 폴백) */
export function asProgressPayload(metadata: unknown): ProgressPayload | null {
  if (!metadata || typeof metadata !== "object") return null;
  const p = metadata as Partial<ProgressPayload>;
  if (p.kind !== "progress") return null;
  if (!isSupportedVersion(p)) return null;
  if (!Array.isArray(p.steps)) return null;
  return p as ProgressPayload;
}

/** metadata 가 RESULT payload 면 반환, 아니면 null (→ content 폴백) */
export function asResultPayload(metadata: unknown): ResultPayload | null {
  if (!metadata || typeof metadata !== "object") return null;
  const p = metadata as Partial<ResultPayload>;
  if (p.kind !== "result") return null;
  if (!isSupportedVersion(p)) return null;
  return p as ResultPayload;
}
