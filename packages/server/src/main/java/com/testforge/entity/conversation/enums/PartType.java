package com.testforge.entity.conversation.enums;

import com.testforge.common.EnumColumn;

/**
 * 파트 타입 (MESSAGE_PART.TYPE). 파트는 한 턴 안의 순서 있는 화면 렌더 단위이며, 실행/조회의 사실은
 * EXECUTION/INVESTIGATION 계층에 정규화하고 파트가 FK로 참조한다(messaging.md 파트 타입).
 * 동종 파트 N개 허용(TEXT 여러 개, 한 턴에 실행 여러 번 등). 정렬은 ID 오름차순(생성순=표시순).
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum PartType implements EnumColumn {

    /** 텍스트 블록(발화 본문, Markdown). content가 곧 데이터 */
    TEXT("텍스트"),
    /** 카드 UI (플랜 제안/후보/서비스 선택/인증/재시도). cardType + payloadJson */
    CARD("카드"),
    /** 레시피 실행 진행 블록. executionId + payloadJson(kind:progress) */
    PROGRESS("실행 진행"),
    /** 레시피 실행 결과 블록. executionId + payloadJson(kind:result) */
    RESULT("실행 결과"),
    /** 정보 조회(investigate) 진행 블록. investigationId + payloadJson(kind:investigate_progress) */
    INVESTIGATE("정보 조회 진행"),
    /** 액션 피커 (구조화 입력). executionId + payloadJson(variables/stepIndex 등) */
    ACTION_PICKER("액션 피커"),
    /** 조회 참고 자료(출처 칩). payloadJson(kind:references) */
    REFERENCES("참고 자료");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    PartType(String description) {
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
