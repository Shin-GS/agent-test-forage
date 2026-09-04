package com.testforge.security;

import com.testforge.common.error.ApiException;
import com.testforge.entity.user.enums.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 현재 인증된 사용자를 SecurityContext에서 도출하는 헬퍼. 컨트롤러가 요청 파라미터/바디의 userId를
 * 신뢰하지 않고 세션 주체에서 식별자/역할을 얻도록 한다(auth.md: userId/role은 세션에서 도출).
 *
 * <p>SecurityConfig가 /api/v1/** 를 인증 필수로 막으므로 보호된 컨트롤러 진입 시점엔 주체가 항상 존재한다.
 * 방어적으로 미인증이면 401(UNAUTHORIZED)로 처리한다.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** 현재 세션 주체. 미인증이면 401. */
    public static AppUserPrincipal require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new ApiException(
                    com.testforge.common.error.ErrorCode.UNAUTHORIZED,
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }
        return principal;
    }

    /** 현재 세션 사용자 ID. 미인증이면 401. */
    public static Long id() {
        return require().getId();
    }

    /** 현재 세션 사용자 역할. 미인증이면 401. */
    public static UserRole role() {
        return require().getRole();
    }
}
