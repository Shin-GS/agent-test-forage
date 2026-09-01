package com.testforge.dto.common;

import com.testforge.common.EnumColumn;

/**
 * 상태 enum을 응답에 내릴 때 사용하는 표현.
 * 코드값과 한글 설명을 함께 내려 프론트가 라벨을 직접 하드코딩하지 않도록 한다.
 *
 * <p>예: { "code": "ACTIVE", "description": "정상" }
 */
public record StatusView(
        // 저장 코드값 (예: ACTIVE)
        String code,
        // 사람이 읽는 한글 설명 (예: 정상)
        String description) {

    /** EnumColumn(SpecStatus/EndpointStatus 등)을 표현으로 변환 */
    public static StatusView of(EnumColumn value) {
        return new StatusView(value.getCode(), value.getDescription());
    }
}
