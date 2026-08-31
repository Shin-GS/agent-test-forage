package com.testforge.common.error;

/**
 * 에러 응답 바디 { error: { code, message } }에 실리는 안정적인 에러 코드.
 */
public enum ErrorCode {
    /** 지원하지 않는 등록 계약(schema) 버전 */
    UNSUPPORTED_SCHEMA_VERSION,
    /** OpenAPI 스펙 본문이 유효하지 않음 */
    INVALID_SPEC,
    /** 인증 실패 (등록 토큰 불일치/누락) */
    UNAUTHORIZED,
    /** 요청 형식/필수값 오류 */
    INVALID_REQUEST,
    /** 서버 내부 오류 */
    INTERNAL_ERROR
}
