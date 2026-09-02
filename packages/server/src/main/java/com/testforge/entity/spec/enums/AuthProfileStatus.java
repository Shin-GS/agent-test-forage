package com.testforge.entity.spec.enums;

import com.testforge.common.EnumColumn;

/**
 * 인증 프로필의 생명주기 상태 (AUTH_PROFILE.STATUS).
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum AuthProfileStatus implements EnumColumn {

    /** 최신 등록 스펙에 존재하는 인증 프로필 */
    ACTIVE("최신 스펙에 존재"),
    /** 최신 스펙에서 사라짐. 이력 보존을 위해 삭제하지 않고 보존 */
    INACTIVE("스펙에서 사라짐");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    AuthProfileStatus(String description) {
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
