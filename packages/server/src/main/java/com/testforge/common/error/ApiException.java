package com.testforge.common.error;

import org.springframework.http.HttpStatus;

/**
 * 안정적인 에러 코드와 HTTP 상태를 함께 담는 도메인 예외.
 * message는 사용자에게 노출되므로 내부 구현 정보를 포함하지 않는다.
 */
public class ApiException extends RuntimeException {

    /** 응답에 실릴 에러 코드 */
    private final ErrorCode code;
    /** 응답 HTTP 상태 */
    private final HttpStatus status;

    public ApiException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /** 지원하지 않는 등록 계약 버전 (400) */
    public static ApiException unsupportedSchemaVersion(String schemaVersion) {
        return new ApiException(
                ErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                HttpStatus.BAD_REQUEST,
                "Unsupported schema version: " + schemaVersion);
    }

    /** 유효하지 않은 스펙 본문 (400) */
    public static ApiException invalidSpec(String detail) {
        return new ApiException(ErrorCode.INVALID_SPEC, HttpStatus.BAD_REQUEST, detail);
    }

    /** 인증 실패 (401) */
    public static ApiException unauthorized() {
        return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED,
                "Invalid or missing registration token");
    }

    /** 요청 형식/필수값 오류 (400) */
    public static ApiException invalidRequest(String detail) {
        return new ApiException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, detail);
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
