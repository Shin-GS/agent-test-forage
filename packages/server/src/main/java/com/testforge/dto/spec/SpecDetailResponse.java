package com.testforge.dto.spec;

import com.testforge.dto.common.StatusView;

import java.util.List;

/**
 * 스펙 상세. (admin.md "스펙 관리 > 상세" 소비용)
 * 서비스 설명 + 엔드포인트 목록 + 인증 프로필 + 진단 정보를 함께 내린다.
 */
public record SpecDetailResponse(
        // 스펙 ID
        Long id,
        // 표시용 서비스 이름
        String name,
        // 서버 도메인 (식별 키)
        String baseUrl,
        // 생명주기 상태 (code + description)
        StatusView status,
        // 서비스 설명 메타 (description/domain/capabilities/notes)
        ServiceInfo serviceInfo,
        // 엔드포인트 목록
        List<EndpointItem> endpoints,
        // 인증 프로필 목록
        List<AuthProfileItem> authProfiles,
        // 라이브러리 진단 정보
        Diagnostics diagnostics) {

    /**
     * 서비스 설명 메타.
     * capabilities는 DB에 JSON 문자열로 저장되어 있으나 응답에서는 파싱된 배열로 내린다.
     */
    public record ServiceInfo(
            // 서비스 설명
            String description,
            // 서비스 도메인 영역
            String domain,
            // 기능 키워드 배열
            List<String> capabilities,
            // 주의사항
            String notes,
            // 관리자가 메타를 수정했는지 여부 (yml 덮어쓰기 방지 상태)
            boolean adminEdited) {
    }

    /** 상세 화면의 엔드포인트 한 줄 */
    public record EndpointItem(
            // 엔드포인트 ID
            Long id,
            // HTTP 메서드
            String method,
            // 경로
            String path,
            // API 설명 (매칭 힌트)
            String summary,
            // 생명주기 상태 (code + description)
            StatusView status,
            // 목록 제외 여부 (@TestForgeExclude)
            boolean excluded,
            // 실행 전 확인 필요 여부 (@TestForgeConfirm)
            boolean confirmRequired) {
    }

    /** 인증 프로필 한 줄 */
    public record AuthProfileItem(
            // 프로필 이름 (예: 일반/관리자)
            String name,
            // 401/403 시 안내할 로그인 페이지 URL
            String loginPageUrl) {
    }

    /** 등록 라이브러리 진단 정보 */
    public record Diagnostics(
            // 등록한 라이브러리 언어 (예: java)
            String clientLang,
            // 등록한 라이브러리 버전 (예: 0.0.1)
            String clientVersion,
            // 마지막 등록에 사용된 계약(body) 버전
            String schemaVersion) {
    }
}
