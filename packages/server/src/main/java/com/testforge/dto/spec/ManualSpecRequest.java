package com.testforge.dto.spec;

import java.util.List;

/**
 * 관리자 수동 서버(스펙) 생성/수정 요청. 라이브러리 등록 계약(RegisterRequest)과 별개로,
 * 관리자 화면에서 직접 입력하는 서비스 메타 + 인증 프로필을 담는다.
 *
 * <p>생성({@code POST /specs/manual})에서는 name/baseUrl이 필수다. 수정({@code PATCH /specs/{id}})에서는
 * baseUrl이 와도 무시하며(식별 키 변경 금지), 나머지 메타만 반영한다.
 */
public record ManualSpecRequest(
        // 표시용 서비스 이름 (생성 시 필수)
        String name,
        // 서버 도메인 (생성 시 필수, http/https 스킴). 수정 시 무시됨
        String baseUrl,
        // 서비스 설명
        String description,
        // 도메인 영역
        String domain,
        // 기능 키워드 배열
        List<String> capabilities,
        // 주의사항
        String notes,
        // 인증 프로필 목록 (교체 방식)
        List<AuthProfileInput> authProfiles
) {

    /** 인증 프로필 입력 항목 */
    public record AuthProfileInput(
            // 프로필 이름 (예: 일반/관리자)
            String name,
            // 401/403 시 안내할 로그인 페이지 URL
            String loginPageUrl) {
    }
}
