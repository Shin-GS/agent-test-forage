package com.testforge.entity.execution.enums;

import com.testforge.common.EnumColumn;

/**
 * 실행 유형 (EXECUTION.TYPE). 모든 실행은 내부적으로 플랜이지만(plan.md: "단일 레시피 =
 * 레시피 1개짜리 플랜"), 표시/집계를 위해 단일과 복합을 구분한다.
 */
public enum ExecutionType implements EnumColumn {

    /** 단일 레시피 실행 (EXECUTION_RECIPE 1건) */
    SINGLE("단일 레시피"),
    /** 복합 플랜 실행 (EXECUTION_RECIPE N건) */
    PLAN("플랜");

    private final String description;

    ExecutionType(String description) {
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
