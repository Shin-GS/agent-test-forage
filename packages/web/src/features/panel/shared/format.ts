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
