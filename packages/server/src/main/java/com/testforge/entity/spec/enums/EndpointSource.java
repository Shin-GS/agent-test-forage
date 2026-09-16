package com.testforge.entity.spec.enums;

import com.testforge.common.EnumColumn;

/**
 * 개별 엔드포인트의 등록 출처 (API_ENDPOINT.SOURCE).
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum EndpointSource implements EnumColumn {

    /** 라이브러리가 스펙을 수집하여 자동 등록한 API */
    LIBRARY("라이브러리 자동 등록"),
    /** 관리자가 수동으로 등록한 API */
    MANUAL("관리자 수동 등록");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    EndpointSource(String description) {
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
