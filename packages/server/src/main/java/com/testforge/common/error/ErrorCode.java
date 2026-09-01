package com.testforge.common.error;

import com.testforge.common.EnumColumn;

/**
 * 에러 응답 바디 { error: { code, message } }에 실리는 안정적인 에러 코드.
 *
 * <p>{@link EnumColumn}을 구현하여 코드값({@link #getCode()})과 사람이 읽는 한글 설명
 * ({@link #getDescription()})을 함께 노출한다. getCode()는 enum name()과 동일하다.
 * 응답 계약(현재 code=name())은 그대로 유지하며, description은 로그/문서용으로만 사용한다.
 */
public enum ErrorCode implements EnumColumn {
    /** 지원하지 않는 등록 계약(schema) 버전 */
    UNSUPPORTED_SCHEMA_VERSION("지원하지 않는 등록 계약 버전"),
    /** OpenAPI 스펙 본문이 유효하지 않음 */
    INVALID_SPEC("유효하지 않은 스펙 본문"),
    /** 인증 실패 (등록 토큰 불일치/누락) */
    UNAUTHORIZED("인증 실패"),
    /** 요청 형식/필수값 오류 */
    INVALID_REQUEST("요청 형식/필수값 오류"),
    /** 존재하지 않거나 삭제된 스펙 조회 */
    SPEC_NOT_FOUND("스펙을 찾을 수 없음"),
    /** 존재하지 않거나 삭제된 레시피 조회 */
    RECIPE_NOT_FOUND("레시피를 찾을 수 없음"),
    /** 레시피 스텝 정의가 유효하지 않음 (필수 필드 누락 등) */
    INVALID_RECIPE("유효하지 않은 레시피"),
    /** 서브레시피 순환 참조 (A→B→A) — 저장 거부 */
    RECIPE_CYCLE("레시피 순환 참조"),
    /** 서버 내부 오류 */
    INTERNAL_ERROR("서버 내부 오류");

    /** 사람이 읽는 한글 설명 */
    private final String description;

    ErrorCode(String description) {
        this.description = description;
    }

    /** DB/응답에 쓰이는 코드값. 현재는 enum name()과 동일 */
    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
