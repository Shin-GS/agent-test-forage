package com.testforge.ai.enums;

import com.testforge.common.EnumColumn;

/**
 * AI가 선택할 수 있는 tool 종류 (intent-classification.md의 Tool Use 패턴).
 * 1회 AI 호출로 의도 분석 + 분기가 동시에 처리되며, AI(또는 목 구현)가 이 중 하나를 선택한다.
 *
 * <p>{@code INVESTIGATE}(정보 조회 agentic loop)는 다른 tool과 달리 종료되지 않고 반복 호출되는
 * 별도 흐름이라, 이번 조각(단순 분기)에서는 포함하지 않고 다음 조각에서 추가한다.
 *
 * <p>enum name()은 Java 관례(UPPER_SNAKE_CASE)를 따르고, AI/문서에서 쓰는 wire 이름은
 * 소문자 snake_case({@code execute_recipe})다. wire 이름은 {@link #wireName()}으로 제공한다.
 */
public enum ToolName implements EnumColumn {

    /** 단일 레시피 실행 (매칭 레시피 1개) */
    EXECUTE_RECIPE("레시피 실행"),
    /** 여러 레시피 순차 실행 플랜 제안 (복합 작업) */
    PROPOSE_PLAN("플랜 제안"),
    /** 서비스 선택 요청 (서비스 미지정/변경 필요) */
    SELECT_SERVICE("서비스 선택"),
    /** 유사 레시피 후보 제시 (2개 이상 매칭) */
    SHOW_CANDIDATES("후보 제시"),
    /** 모호하여 추가 정보 요청 */
    CLARIFY("재질문"),
    /** 매칭 레시피 없음 안내 */
    NO_MATCH("매칭 없음"),
    /** 일반 대화/질문 응답 */
    CHAT("일반 대화");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    ToolName(String description) {
        this.description = description;
    }

    /** AI/문서 wire 이름 (소문자 snake_case). 예: EXECUTE_RECIPE → "execute_recipe" */
    public String wireName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** DB/로그 코드값. 현재는 enum name()과 동일 */
    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
