// 사이드 패널 표시용 포맷 헬퍼.

/** startedAt(ISO) → HH:mm. 파싱 불가 시 빈 문자열 */
export function formatTime(iso: string | null): string {
  if (!iso) return "";
  const ts = Date.parse(iso);
  if (Number.isNaN(ts)) return "";
  const d = new Date(ts);
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  return `${hh}:${mm}`;
}

/** lastUsedAt(ISO) → "N분 전" 등 상대 시각. 없거나 파싱 불가 시 null */
export function formatRelative(iso: string | null): string | null {
  if (!iso) return null;
  const ts = Date.parse(iso);
  if (Number.isNaN(ts)) return null;
  const diffSec = Math.max(0, Math.floor((Date.now() - ts) / 1000));
  if (diffSec < 60) return "방금 전";
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}분 전`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}시간 전`;
  const diffDay = Math.floor(diffHour / 24);
  return `${diffDay}일 전`;
}

/**
 * startedAt(ISO) → 히스토리용 상대 시각. "방금 전 / N분 전 / N시간 전 / 어제 / M월 D일".
 * 없거나 파싱 불가 시 null.
 */
export function relativeTime(iso: string | null): string | null {
  if (!iso) return null;
  const ts = Date.parse(iso);
  if (Number.isNaN(ts)) return null;
  const diffSec = Math.max(0, Math.floor((Date.now() - ts) / 1000));
  if (diffSec < 60) return "방금 전";
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin}분 전`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}시간 전`;
  const diffDay = Math.floor(diffHour / 24);
  if (diffDay === 1) return "어제";
  const d = new Date(ts);
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

/**
 * durationMs → 사람이 읽는 소요시간. "350ms" / "1.2초" / "1분 5초".
 * null/음수면 null.
 */
export function formatDuration(ms: number | null | undefined): string | null {
  if (ms == null || ms < 0) return null;
  if (ms < 1000) return `${ms}ms`;
  const sec = ms / 1000;
  if (sec < 60) return `${sec.toFixed(1)}초`;
  const min = Math.floor(sec / 60);
  const restSec = Math.round(sec % 60);
  return restSec === 0 ? `${min}분` : `${min}분 ${restSec}초`;
}

/**
 * 실행 종료 상태 코드 → 아이콘 (messaging.md 종료 사유 대응).
 * SUCCESS ✅ / PARTIAL ⚠️ / FAILED ❌ / STOPPED·CANCELLED ⏹️ / 그 외(진행중 등) 🔄
 */
export function statusIcon(code: string | null | undefined): string {
  switch ((code ?? "").toUpperCase()) {
    case "SUCCESS":
    case "COMPLETED":
      return "✅";
    case "PARTIAL":
    case "PARTIAL_SUCCESS":
      return "⚠️";
    case "FAILED":
    case "ERROR":
    case "TIMEOUT":
      return "❌";
    case "STOPPED":
    case "CANCELLED":
    case "CANCELED":
      return "⏹️";
    default:
      return "🔄";
  }
}

/**
 * 결과키 표시명 폴백 (messaging.md RESULT 표시명 폴백 체인).
 * labels[key]가 있으면 표시명, 없으면 원본 key 그대로(중첩/배열 key는 경로 그대로).
 */
export function resultKeyLabel(
  key: string,
  labels: Record<string, string> | null | undefined,
): string {
  const label = labels?.[key];
  return label && label.trim() ? label : key;
}

/**
 * 결과값 표시 폴백 (messaging.md). 값이 없거나 null이면 "값 없음",
 * 문자열/숫자/불리언은 그대로, 객체/배열은 JSON 요약.
 */
export function resultValueDisplay(value: unknown): string {
  if (value == null) return "값 없음";
  // 빈 문자열(공백만 포함)도 "값 없음"으로 본다(화면 공백 방지). 0/false 는 유효한 값이라 그대로 표시.
  if (typeof value === "string") return value.trim() === "" ? "값 없음" : value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}
