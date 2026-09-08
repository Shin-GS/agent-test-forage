package com.testforge.entity.conversation.enums;

import com.testforge.common.EnumColumn;

/**
 * 파트 상태 (MESSAGE_PART.STATUS). 파트 성격별 상태머신을 하나의 enum으로 표현한다(messaging.md 파트 STATUS):
 * <ul>
 *   <li>스트리밍(TEXT): {@code STREAMING → COMPLETE / FAILED}</li>
 *   <li>실행/조회(PROGRESS/RESULT/INVESTIGATE): 참조 대상(EXECUTION/INVESTIGATION) 상태를 따름
 *       (여기선 {@code COMPLETE}/{@code FAILED}로 표현)</li>
 *   <li>인터랙티브(CARD/ACTION_PICKER): {@code PENDING → CONSUMED / CANCELLED}</li>
 *   <li>정적(REFERENCES): {@code COMPLETE}</li>
 * </ul>
 * 인터랙티브 파트의 {@code CONSUMED}는 "사용자가 이미 이 카드/피커에 응답했다"는 뜻이며, 새로고침 후에도
 * 이 상태로 복원되어 "이미 실행한 카드"가 다시 활성화되지 않는다.
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum PartStatus implements EnumColumn {

    /** 스트리밍 중 (TEXT 파트 진행) */
    STREAMING("스트리밍"),
    /** 완료 (내용 확정) */
    COMPLETE("완료"),
    /** 실패 */
    FAILED("실패"),
    /** 대기 (인터랙티브 파트: 사용자 응답 대기) */
    PENDING("대기"),
    /** 소비됨 (인터랙티브 파트: 사용자가 응답 완료) */
    CONSUMED("소비됨"),
    /** 취소됨 (인터랙티브 파트: 취소/폐기) */
    CANCELLED("취소됨");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    PartStatus(String description) {
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
