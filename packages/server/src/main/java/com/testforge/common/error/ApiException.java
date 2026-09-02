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

    /** 존재하지 않거나 삭제된 스펙 (404) */
    public static ApiException specNotFound(Long specId) {
        return new ApiException(ErrorCode.SPEC_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Spec not found: " + specId);
    }

    /** 존재하지 않거나 삭제된 레시피 (404) */
    public static ApiException recipeNotFound(Long recipeId) {
        return new ApiException(ErrorCode.RECIPE_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Recipe not found: " + recipeId);
    }

    /** 레시피 스텝 정의가 유효하지 않음 (400) */
    public static ApiException invalidRecipe(String detail) {
        return new ApiException(ErrorCode.INVALID_RECIPE, HttpStatus.BAD_REQUEST, detail);
    }

    /** 서브레시피 순환 참조로 저장 거부 (400) */
    public static ApiException recipeCycle(String detail) {
        return new ApiException(ErrorCode.RECIPE_CYCLE, HttpStatus.BAD_REQUEST, detail);
    }

    /** 존재하지 않거나 삭제된 대화방 (404) */
    public static ApiException conversationNotFound(Long conversationId) {
        return new ApiException(ErrorCode.CONVERSATION_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Conversation not found: " + conversationId);
    }

    /** 대화방이 이미 처리 중이라 새 요청을 받을 수 없음 (409) */
    public static ApiException conversationBusy(Long conversationId) {
        return new ApiException(ErrorCode.CONVERSATION_BUSY, HttpStatus.CONFLICT,
                "Conversation is busy: " + conversationId);
    }

    /** 존재하지 않는 실행 (404) */
    public static ApiException executionNotFound(Long executionId) {
        return new ApiException(ErrorCode.EXECUTION_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Execution not found: " + executionId);
    }

    /** 존재하지 않는 실행 스텝 (404) */
    public static ApiException executionStepNotFound(Long stepId) {
        return new ApiException(ErrorCode.EXECUTION_STEP_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Execution step not found: " + stepId);
    }

    /** 실행 중이라 요청을 수행할 수 없음 (409) */
    public static ApiException conversationExecuting(Long conversationId) {
        return new ApiException(ErrorCode.CONVERSATION_EXECUTING, HttpStatus.CONFLICT,
                "Conversation is executing: " + conversationId);
    }

    /** AI 호출 실패 (외부 AI API 오류/파싱 실패). 5xx로 처리 (외부 의존 실패) */
    public static ApiException aiCallFailed(String detail) {
        return new ApiException(ErrorCode.AI_CALL_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, detail);
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
