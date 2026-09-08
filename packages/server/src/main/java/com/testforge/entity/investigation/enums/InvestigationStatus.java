package com.testforge.entity.investigation.enums;

import com.testforge.common.EnumColumn;

/**
 * 정보 조회 루프 상태 (INVESTIGATION.STATUS, db/investigation.md). 비정상 종료(FAILED/TIMEOUT)도
 * {@code RUNNING} 잔존 없이 확정한다(서버 finally에서 상태 확정 — 유령 진행 블록 방지).
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum InvestigationStatus implements EnumColumn {

    /** 조회 루프 진행 중 */
    RUNNING("진행 중"),
    /** 정상 종료 (최종 답변 발행) */
    DONE("완료"),
    /** 전 커넥터 실패 등으로 조회 실패 */
    FAILED("실패"),
    /** 루프 타임아웃(120초 초과) */
    TIMEOUT("타임아웃");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    InvestigationStatus(String description) {
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
