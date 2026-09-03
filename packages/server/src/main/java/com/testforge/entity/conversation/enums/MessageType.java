package com.testforge.entity.conversation.enums;

import com.testforge.common.EnumColumn;

/**
 * 메시지 표현 타입 (MESSAGE.TYPE). 타입별 상세(payload)는 METADATA_JSON에 보관한다
 * (컬럼명은 유지하되 개념상 payloadJson. kind/schemaVersion 포함, messaging.md).
 * DB에는 {@code @Enumerated(STRING)}으로 name()이 그대로 저장된다.
 */
public enum MessageType implements EnumColumn {

    /** 일반 텍스트 (Markdown) */
    TEXT("텍스트"),
    /** 카드 UI (진입점 블록) */
    CARD("카드"),
    /** 진행 상태 표시 (레시피 실행 진행 블록) */
    PROGRESS("진행 상태"),
    /** 실행 결과 표시 (레시피 실행 결과 블록) */
    RESULT("실행 결과"),
    /** 액션 피커 (구조화 입력) */
    ACTION_PICKER("액션 피커"),
    /** 시스템 안내 */
    SYSTEM("시스템");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    MessageType(String description) {
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
