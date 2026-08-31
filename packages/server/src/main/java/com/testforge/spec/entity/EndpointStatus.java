package com.testforge.spec.entity;

import com.testforge.common.EnumColumn;

/**
 * 개별 엔드포인트의 생명주기 상태 (API_ENDPOINT.STATUS).
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum EndpointStatus implements EnumColumn {

    /** 최신 등록 스펙에 존재하는 API */
    ACTIVE("최신 스펙에 존재"),
    /** 최신 스펙에서 사라짐. 레시피 참조 보호를 위해 삭제하지 않고 보존 */
    DEPRECATED("스펙에서 사라짐");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    EndpointStatus(String description) {
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
