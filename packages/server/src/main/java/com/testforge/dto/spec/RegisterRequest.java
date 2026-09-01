package com.testforge.dto.spec;

import java.util.List;

/**
 * 라이브러리가 보내는 스펙 등록 요청 본문 (버전 무관 원본 형태).
 *
 * <p>이 타입은 전송 계약(wire) 형태이며 도메인 로직이 직접 소비하지 않는다.
 * {@code SpecRegistrationParser}가 {@code NormalizedSpec}으로 정규화하여
 * 도메인이 계약 변경으로부터 분리되도록 한다.
 */
public record RegisterRequest(
        // 등록 계약(body 구조) 버전. 서버가 이 값으로 파서를 분기 (예: 1)
        String schemaVersion,
        // 요청을 보낸 라이브러리 정보 (진단용)
        Client client,
        // 표시용 서비스 이름 (예: demo-shop)
        String name,
        // 서버 도메인. 식별 키 (예: https://shop-api.example.com)
        String baseUrl,
        // 원본 OpenAPI JSON 문자열
        String specJson,
        // 정규화 후 스펙 본문의 SHA-256 (예: sha256...)
        String specHash,
        // 서비스 메타 정보 (설명/도메인/기능/주의사항)
        ServiceInfo serviceInfo,
        // Jira 연동 정보
        Jira jira,
        // 인증 프로필 목록
        List<AuthProfileDto> authProfiles
) {
    /** 등록한 라이브러리 언어/버전 (진단용) */
    public record Client(
            // 언어 (예: java)
            String lang,
            // 버전 (예: 0.0.1)
            String version) {
    }

    /** 서비스 메타 정보 */
    public record ServiceInfo(
            // 서비스 설명 (예: 온라인 쇼핑몰 API)
            String description,
            // 도메인 영역 (예: 커머스)
            String domain,
            // 기능 키워드 배열 (예: [회원가입, 상품등록, 주문])
            List<String> capabilities,
            // 주의사항 (예: 스테이징)
            String notes
    ) {
    }

    /** Jira 연동 정보 */
    public record Jira(
            // Jira 프로젝트 키 (예: SHOP)
            String projectKey) {
    }

    /** 인증 프로필 항목 */
    public record AuthProfileDto(
            // 프로필 이름 (예: 일반)
            String name,
            // 로그인 페이지 URL (예: https://.../login)
            String loginPageUrl) {
    }
}
