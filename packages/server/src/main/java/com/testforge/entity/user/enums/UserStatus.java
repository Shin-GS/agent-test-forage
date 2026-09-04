package com.testforge.entity.user.enums;

import com.testforge.common.EnumColumn;

/**
 * 사용자 계정 상태. 관리자가 계정을 비활성화하면 INACTIVE가 되며,
 * 인증 필터가 매 요청마다 상태를 재확인해 INACTIVE면 세션을 폐기한다(auth.md).
 */
public enum UserStatus implements EnumColumn {
    /** 활성 계정: 로그인/요청 가능 */
    ACTIVE("활성"),
    /** 비활성 계정: 관리자가 비활성화. 다음 요청부터 401 + 세션 폐기 */
    INACTIVE("비활성");

    private final String description;

    UserStatus(String description) {
        this.description = description;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
