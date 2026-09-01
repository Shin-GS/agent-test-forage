package com.testforge.entity.execution.enums;

import com.testforge.common.EnumColumn;

/**
 * 실행 전체 상태 (EXECUTION.STATUS, db/execution.md).
 * SSE {@code execution_complete.outcome}(messaging.md)와 대응하되, 서버 저장 관점의 상태다:
 * outcome의 SUCCESS/STOPPED/CANCELLED/FAILED 중 CANCELLED는 실행 레코드를 남기지 않는
 * "취소"(레시피 시작 전 폐기)라 여기 없고, 부분 성공을 PARTIAL로 구분한다.
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
    /** 사용자 중지 (이어서 실행 가능) */
    STOPPED("중지됨");

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
