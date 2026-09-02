package com.testforge.dto.common;

import java.util.List;

/**
 * 커서 기반 페이지 응답 래퍼 (무한 스크롤용).
 *
 * <p>오프셋 페이징 대신 커서를 쓰는 이유: 최신순으로 계속 항목이 쌓이는 목록(히스토리/메시지 등)에서
 * 오프셋은 뒤로 갈수록 느려지고, 새 항목 추가 시 페이지가 밀려 중복/누락이 생긴다. 커서는 인덱스를
 * 타 일정한 성능을 내고 중복/누락이 없다.
 *
 * <p>FE는 {@code nextCursor}를 그대로 다음 요청에 실어 "더 불러오기"를 이어간다. {@code hasNext}가
 * false면 마지막 페이지다({@code nextCursor}는 null). 커서는 서버가 인코딩한 <b>불투명 문자열</b>이라
 * FE가 내부 형식을 해석할 필요가 없다.
 *
 * @param items      이번 페이지 항목 (요청 정렬 순서 유지)
 * @param nextCursor 다음 페이지 커서 (없으면 null)
 * @param hasNext    다음 페이지 존재 여부
 */
public record CursorPage<T>(
        List<T> items,
        String nextCursor,
        boolean hasNext) {

    /** 다음 페이지가 없는 마지막 페이지 */
    public static <T> CursorPage<T> last(List<T> items) {
        return new CursorPage<>(items, null, false);
    }

    /** 다음 페이지가 있는 페이지 */
    public static <T> CursorPage<T> of(List<T> items, String nextCursor) {
        return new CursorPage<>(items, nextCursor, true);
    }
}
