package com.testforge.utils;

/**
 * 대화방 임시 제목 파생/절단 헬퍼.
 *
 * <p>제목 결정 규칙:
 * <ul>
 *   <li>요청 title이 있으면(레시피명 등) 공백 정리 후 그대로 사용하되 안전 절단</li>
 *   <li>없으면 첫 메시지 content 앞부분으로 임시 제목을 파생(20자 이내)</li>
 * </ul>
 * 절단은 char가 아닌 코드포인트 기준으로 세어 한글/이모지가 깨지지 않게 한다.
 * 이 임시 제목은 추후 AI 요약으로 교체된다(다음 조각).
 */
public final class ConversationTitleUtil {

    /** content 기반 임시 제목의 표시 한도 (코드포인트 기준) */
    private static final int TEMPORARY_TITLE_MAX = 20;
    /** CONVERSATION.TITLE 컬럼 한도 (엔티티 length=200) — 지정 title도 이 한도 내로 절단 */
    private static final int TITLE_COLUMN_MAX = 200;
    /** 절단 표시 문자 (U+2026 HORIZONTAL ELLIPSIS) */
    private static final String ELLIPSIS = "\u2026";
    /** content가 비어 결과가 없을 때의 기본 제목 */
    private static final String DEFAULT_TITLE = "새 대화";

    private ConversationTitleUtil() {
    }

    /**
     * 제목을 결정한다. requestedTitle이 비어 있지 않으면 그것을(컬럼 한도로 절단),
     * 없으면 content 앞부분으로 임시 제목을 파생한다.
     */
    public static String resolveTitle(String requestedTitle, String content) {
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            return truncateByCodePoints(normalize(requestedTitle), TITLE_COLUMN_MAX);
        }
        return deriveTemporaryTitle(content);
    }

    /**
     * 첫 메시지 content 앞부분으로 임시 제목을 만든다.
     * 개행/연속 공백을 단일 공백으로 정리하고 20자 이내로 코드포인트 안전 절단하며,
     * 원문이 한도를 초과하면 "…"를 덧붙인다. 결과가 비면 기본값을 반환한다.
     */
    public static String deriveTemporaryTitle(String content) {
        String normalized = normalize(content);
        if (normalized.isEmpty()) {
            return DEFAULT_TITLE;
        }
        return truncateByCodePoints(normalized, TEMPORARY_TITLE_MAX);
    }

    /** 개행/탭/연속 공백을 단일 공백으로 정리하고 앞뒤를 trim한다. null이면 빈 문자열. */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    /**
     * 코드포인트 기준으로 maxCodePoints 이내로 절단한다.
     * 원문이 한도를 초과하면 (한도-1)개까지 자르고 "…"를 붙여 총 길이가 한도를 넘지 않게 한다.
     * char 기준 substring을 쓰지 않아 한글/이모지(서로게이트 페어)가 깨지지 않는다.
     */
    private static String truncateByCodePoints(String value, int maxCodePoints) {
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= maxCodePoints) {
            return value;
        }
        int keep = maxCodePoints - 1; // "…" 한 자리 확보
        int endIndex = value.offsetByCodePoints(0, keep);
        return value.substring(0, endIndex) + ELLIPSIS;
    }
}
