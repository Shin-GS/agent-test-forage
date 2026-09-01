package com.testforge.entity.execution.enums;

import com.testforge.common.EnumColumn;

/**
 * 스텝별 실행 결과 상태 (EXECUTION_STEP.STATUS, db/execution.md).
 * 진행 표시 아이콘(execution.md)과 대응: SUCCESS ✅ / FAILED ❌ / SKIPPED ⏭️ / PENDING ⬜·🔄.
 */
public enum ExecutionStepStatus implements EnumColumn {

    /** 대기/실행 중 (아직 결과 미확정) */
    PENDING("대기"),
    /** 성공 */
    SUCCESS("성공"),
    /** 실패 */
    FAILED("실패"),
    /** 조건 미충족으로 스킵 */
    SKIPPED("스킵");

    private final String description;

    ExecutionStepStatus(String description) {
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
