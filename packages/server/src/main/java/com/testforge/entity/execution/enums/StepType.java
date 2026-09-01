package com.testforge.entity.execution.enums;

import com.testforge.common.EnumColumn;

/**
 * 스텝 타입 (EXECUTION_STEP.STEP_TYPE, structure.md "스텝 타입").
 * 실행 스텝의 스냅샷 표기용이다(레시피 정의의 스텝 종류를 그대로 기록).
 */
public enum StepType implements EnumColumn {

    /** 외부 서버 API 호출 */
    API("API 호출"),
    /** JavaScript 실행 (데이터 가공/조건 판단) */
    SCRIPT("스크립트"),
    /** 다른 레시피를 서브레시피로 호출 */
    RECIPE("서브레시피"),
    /** 실행 중 사용자에게 데이터 요청 (액션 피커) */
    USER_INPUT("사용자 입력");

    private final String description;

    StepType(String description) {
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
