package com.testforge.entity.user.enums;

import com.testforge.common.EnumColumn;

/**
 * 사용자 역할. 일반 사용자와 관리자 두 종류만 둔다(auth.md 권한/역할).
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 저장된다.
 */
public enum UserRole implements EnumColumn {
    /** 일반 사용자: 채팅, 레시피 실행, 개인 레시피, 히스토리 조회 */
    USER("일반 사용자"),
    /** 관리자: 일반 기능 + 공통 레시피 + 사용자 관리 + 스펙 관리 */
    ADMIN("관리자");

    private final String description;

    UserRole(String description) {
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
