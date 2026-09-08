package com.testforge.entity.investigation.enums;

import com.testforge.common.EnumColumn;

/**
 * 조회 루프 내 소스별 조회 단계 상태 (INVESTIGATION_STEP.STATUS, db/investigation.md).
 * {@code SKIPPED}는 조회를 실제 수행하지 못한 경우(미등록 커넥터 source, confluence인데 spaceKey 미연결,
 * 중복 (source, query) 캐시 재사용)이며 이 역시 조회 카운터를 소비한다.
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum InvestigationStepStatus implements EnumColumn {

    /** 조회 진행 중 */
    RUNNING("진행 중"),
    /** 조회 성공(근거 발견) */
    SUCCESS("성공"),
    /** 조회 실패(못 찾음/타임아웃 등) */
    FAILED("실패"),
    /** 스킵(미지원/중복 등, 카운터는 소비) */
    SKIPPED("스킵");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    InvestigationStepStatus(String description) {
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
