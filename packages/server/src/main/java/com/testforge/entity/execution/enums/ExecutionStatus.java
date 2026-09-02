package com.testforge.entity.execution.enums;

import com.testforge.common.EnumColumn;

/**
 * 실행 전체 상태 (EXECUTION.STATUS, db/execution.md).
 * messaging.md {@code execution_complete.outcome}와 대응하며, 부분 성공을 PARTIAL로 추가 구분한다.
 *
 * <p><b>중지와 취소는 상태로 구분한다.</b> 히스토리는 "무슨 일이 있었나"의 기록이므로, 사용자가
 * [중지]한 것(STOPPED)과 [취소]한 것(CANCELLED)을 다른 사건으로 남긴다. 이렇게 하면 사용자가
 * 히스토리에서 "이건 내가 취소한 것", "이건 중지한 것"을 구분해 볼 수 있고, 필터/집계도 가능하다.
 * (재개(이어서 실행) 로직의 세분은 그 기능 도입 시 다룬다 — 지금은 재개 미구현이라 두 상태 모두
 * "처음부터 다시"만 제공한다.)
 */
public enum ExecutionStatus implements EnumColumn {

    /** 실행 중 */
    RUNNING("실행 중"),
    /** 전체 성공 */
    SUCCESS("성공"),
    /** 부분 성공/실패 (일부 스텝 실패 또는 중지로 일부만 완료) */
    PARTIAL("부분 완료"),
    /** 실패로 중단 */
    FAILED("실패"),
    /** 사용자 중지 (현재까지 진행분 보존, 히스토리에 남음) */
    STOPPED("중지됨"),
    /** 사용자 취소 (사용자가 실행을 물렸음, 히스토리에 남음) */
    CANCELLED("취소됨");

    private final String description;

    ExecutionStatus(String description) {
        this.description = description;
    }

    /** 종료 상태 여부 (RUNNING이 아니면 종료) */
    public boolean isTerminal() {
        return this != RUNNING;
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
