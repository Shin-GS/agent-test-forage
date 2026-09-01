package com.testforge.entity.recipe.enums;

import com.testforge.common.EnumColumn;

/**
 * 레시피 유효성 검증 상태 (RECIPE.VALIDATION_STATUS).
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum ValidationStatus implements EnumColumn {

    /** 검증 통과: 모든 스텝 참조가 유효 */
    VALID("유효"),
    /** 검증 실패: 참조가 깨짐(엔드포인트 없음/DEPRECATED 등). 저장은 허용하되 경고 보존 */
    INVALID("무효"),
    /** 미검증: 아직 검증되지 않음 */
    UNVALIDATED("미검증");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    ValidationStatus(String description) {
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
