package com.testforge.entity.spec.enums;

import com.testforge.common.EnumColumn;

/**
 * 등록된 스펙의 생명주기 상태 (API_SPEC.STATUS).
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum SpecStatus implements EnumColumn {

    /** 정상: heartbeat가 정상 수신되는 상태 */
    ACTIVE("정상"),
    /** 5분 이상 heartbeat 없음. 다음 heartbeat 수신 시 ACTIVE로 복귀 */
    STALE("응답 없음(5분+)"),
    /** 관리자가 수동으로 비활성화. 자동 삭제 대상에서 제외 */
    INACTIVE("관리자 수동 비활성");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    SpecStatus(String description) {
        this.description = description;
    }

    /** DB 저장 코드값. 현재는 enum name()과 동일 */
    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
