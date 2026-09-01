package com.testforge.sse.enums;

import com.testforge.common.EnumColumn;

/**
 * SSE 이벤트의 성격(nature). 유실 시 복구 전략을 가른다(messaging.md).
 *
 * <ul>
 *   <li>{@link #SIGNAL}: 갱신 트리거. payload 최소. 유실돼도 재조회로 복구</li>
 *   <li>{@link #DATA}: 콘텐츠 자체. 유실 시 손실 → replay 대상</li>
 * </ul>
 */
public enum SseEventNature implements EnumColumn {

    /** 갱신 트리거 (payload 최소, 유실 시 재조회로 복구) */
    SIGNAL("신호"),
    /** 콘텐츠 자체 (유실 시 손실 → replay 대상) */
    DATA("데이터");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    SseEventNature(String description) {
        this.description = description;
    }

    /** 봉투에 실리는 코드값. 현재는 enum name()과 동일 */
    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
