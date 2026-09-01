package com.testforge.entity.recipe.enums;

import com.testforge.common.EnumColumn;

/**
 * 레시피 공개 범위 (RECIPE.VISIBILITY).
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum Visibility implements EnumColumn {

    /** 공통: 관리자가 생성, 모든 사용자에게 노출 */
    COMMON("공통"),
    /** 개인: 본인만 사용 */
    PRIVATE("개인");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    Visibility(String description) {
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
