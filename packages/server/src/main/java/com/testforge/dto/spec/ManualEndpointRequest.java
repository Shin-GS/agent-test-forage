package com.testforge.dto.spec;

import java.util.List;

/**
 * 관리자 수동 엔드포인트 생성/수정 요청. 폼 입력(메서드/경로/파라미터/바디/헤더/응답)을 받아
 * 서버가 OpenAPI Operation JSON으로 직렬화하여 저장한다.
 *
 * <p>메타 필드(excluded/confirmRequired/confirmMessage)는 operationJson이 아니라 엔티티 컬럼에 저장한다.
 * LIBRARY 출처 엔드포인트 수정 시에는 스키마 필드(method/path/parameters/requestBody/responses)를 무시하고
 * 메타만 반영한다(조용히 무시, 거부 아님).
 */
public record ManualEndpointRequest(
        // HTTP 메서드 (GET/POST/PUT/PATCH/DELETE)
        String method,
        // 경로 (/ 로 시작)
        String path,
        // API 설명 (매칭 힌트)
        String summary,
        // 경로/쿼리 파라미터 목록
        List<ParameterInput> parameters,
        // 요청 바디 (GET/DELETE는 무시)
        RequestBodyInput requestBody,
        // 요청 헤더 목록
        List<HeaderInput> headers,
        // 응답 목록
        List<ResponseInput> responses,
        // @TestForgeExclude — 목록 제외 여부 (메타)
        Boolean excluded,
        // @TestForgeConfirm — 실행 전 확인 필요 여부 (메타)
        Boolean confirmRequired,
        // 실행 전 확인 메시지 (메타)
        String confirmMessage
) {

    /** 경로/쿼리 파라미터. {@code in}은 path 또는 query. */
    public record ParameterInput(
            // 파라미터 이름
            String name,
            // 위치: path / query
            String in,
            // 타입 (string/integer/boolean 등)
            String type,
            // 필수 여부
            Boolean required,
            // 설명
            String description) {
    }

    /** 요청 헤더 (요청 parameters[in:header]로 직렬화) */
    public record HeaderInput(
            // 헤더 이름
            String name,
            // 필수 여부
            Boolean required,
            // 설명
            String description) {
    }

    /** 요청 바디 (contentType + 필드 목록) */
    public record RequestBodyInput(
            // 컨텐츠 타입 (예: application/json)
            String contentType,
            // 바디 필드 목록
            List<FieldInput> fields) {
    }

    /** 요청 바디 필드 (object schema property로 직렬화) */
    public record FieldInput(
            // 필드 이름
            String name,
            // 타입 (string/integer/boolean 등)
            String type,
            // 필수 여부
            Boolean required,
            // 설명
            String description) {
    }

    /** 응답 (statusCode + 설명 + 응답 헤더) */
    public record ResponseInput(
            // 상태 코드 (예: 200)
            String statusCode,
            // 설명
            String description,
            // 응답 헤더 목록 (responses.{code}.headers로 직렬화)
            List<HeaderInput> headers) {
    }
}
