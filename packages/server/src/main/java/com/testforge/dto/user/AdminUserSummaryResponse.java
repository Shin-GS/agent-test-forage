package com.testforge.dto.user;

import com.testforge.dto.common.StatusView;
import com.testforge.entity.user.AppUser;

import java.time.LocalDateTime;

/**
 * 관리자 사용자 관리 목록의 한 행 (admin.md 사용자 관리 목록 소비용).
 *
 * <p>비밀번호 해시는 응답에 <b>절대 포함하지 않는다</b>(민감정보 미노출).
 * role/status는 코드+한글 설명을 함께 내리는 {@link StatusView}로 표현해
 * 프론트가 라벨을 하드코딩하지 않도록 한다(스펙 관리 목록과 동일 패턴).
 *
 * <p>{@code lastActiveAdmin}은 이 계정이 "유일한 ACTIVE ADMIN"인지 여부다. 프론트가 강등·비활성
 * 위험 액션 게이팅을 서버 기준으로 정확히 표시하도록 서버가 계산해 내려준다(검색 필터와 무관).
 * 실제 차단은 서버가 400으로 강제하며, 이 플래그는 UX 게이팅 힌트다.
 *
 * <p>예:
 * <pre>
 * {
 *   "id": 3,
 *   "username": "user1",
 *   "name": "홍길동",
 *   "role": { "code": "USER", "description": "일반 사용자" },
 *   "status": { "code": "ACTIVE", "description": "활성" },
 *   "lastLoginAt": "2026-09-16T10:00:00",
 *   "lastActiveAdmin": false
 * }
 * </pre>
 */
public record AdminUserSummaryResponse(
        // 사용자 ID (PK)
        Long id,
        // 로그인 아이디
        String username,
        // 표시 이름 (null 허용)
        String name,
        // 역할 (code + description)
        StatusView role,
        // 계정 상태 (code + description)
        StatusView status,
        // 마지막 로그인 시각 (미로그인 시 null)
        LocalDateTime lastLoginAt,
        // 유일한 ACTIVE ADMIN 여부 (강등·비활성 게이팅 힌트, 서버 계산)
        boolean lastActiveAdmin) {

    /**
     * 엔티티 → 응답 매핑. 비밀번호 해시는 담지 않는다.
     *
     * @param user            대상 사용자
     * @param lastActiveAdmin 이 계정이 유일한 ACTIVE ADMIN인지 (호출부가 전체 카운트로 판정해 전달)
     */
    public static AdminUserSummaryResponse from(AppUser user, boolean lastActiveAdmin) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                StatusView.of(user.getRole()),
                StatusView.of(user.getStatus()),
                user.getLastLoginAt(),
                lastActiveAdmin);
    }
}
