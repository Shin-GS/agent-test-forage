package com.testforge.dto.auth;

import com.testforge.entity.user.AppUser;

/**
 * 현재 로그인 사용자 정보 (auth.md /me, login 응답). 비밀번호/상태 등 민감/불필요 정보는 노출하지 않는다.
 */
public record UserResponse(
        Long id,
        String username,
        String name,
        String role) {

    /** 엔티티 → 응답 매핑 */
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getName(), user.getRole().name());
    }
}
