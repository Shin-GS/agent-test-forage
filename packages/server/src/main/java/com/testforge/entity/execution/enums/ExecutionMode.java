package com.testforge.entity.execution.enums;

import com.testforge.common.EnumColumn;

/**
 * 실행 모드 (EXECUTION.MODE, execution.md "실행 모드").
 * 플랜(TYPE=PLAN)은 항상 AUTO다(plan.md: 승인 후 자동 진행).
 */
public enum ExecutionMode implements EnumColumn {

    /** 자동 실행: 기본값 + AI 판단으로 진행, 판단 불가한 것만 중간에 질문 */
    AUTO("자동 실행"),
    /** 직접 입력하며 실행: 매 사용자 입력 스텝마다 액션 피커로 질문 */
    MANUAL("직접 입력");

    private final String description;

    ExecutionMode(String description) {
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
