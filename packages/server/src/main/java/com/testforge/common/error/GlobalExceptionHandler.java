package com.testforge.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 예외를 안정적인 { error: { code, message } } 응답으로 변환한다.
 * 스택 트레이스나 내부 구현 정보를 클라이언트에 노출하지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 도메인 예외 처리 (5xx는 ERROR, 그 외는 WARN 로그) */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("API error [{}]: {}", ex.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("API error [{}]: {}", ex.getCode(), ex.getMessage());
        }
        return build(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    /** 요청 본문 파싱 실패 → 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "Malformed request body");
    }

    /** 예상하지 못한 예외 → 500 (상세는 로그에만) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred");
    }

    /** 공통 에러 응답 바디 생성 */
    private ResponseEntity<Map<String, Object>> build(HttpStatus status, ErrorCode code, String message) {
        Map<String, Object> error = Map.of(
                "code", code.name(),
                "message", message == null ? "" : message);
        return ResponseEntity.status(status).body(Map.of("error", error));
    }
}
