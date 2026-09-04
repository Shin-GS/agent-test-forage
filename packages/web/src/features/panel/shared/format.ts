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
