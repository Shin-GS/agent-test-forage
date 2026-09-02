package com.testforge.entity.execution.enums;

import com.testforge.common.EnumColumn;

/**
 * 플랜 내 레시피별 실행 상태 (EXECUTION_RECIPE.STATUS, db/execution.md).
 * 단일 실행이면 이 레코드는 1건이며 EXECUTION.STATUS와 동조한다.
 */
public enum ExecutionRecipeStatus implements EnumColumn {

    /** 대기 (아직 시작 전) */
    PENDING("대기"),
    /** 실행 중 */
    RUNNING("실행 중"),
    /** 성공 */
    SUCCESS("성공"),
    /** 조건 미충족으로 스킵 */
    SKIPPED("스킵"),
    /** 실패 */
    FAILED("실패"),
    /** 사용자 중지 */
    STOPPED("중지됨"),
    /** 사용자 취소 */
    CANCELLED("취소됨");

    private final String description;

    ExecutionRecipeStatus(String description) {
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
