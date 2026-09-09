package com.testforge.demo.bank;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * BANK_SESSION 쿠키를 검증하는 인터셉터. 상태를 바꾸는 요청(이체 등)에만 인증을 요구한다.
 * 조회(GET)와 CORS preflight(OPTIONS)는 항상 통과시키고, 그 외 메서드(POST 등)만 세션을 검증한다.
 * 쿠키가 없거나 유효하지 않으면 401을 반환한다.
 *
 * <p>레시피 최초 실행 시 세션 미보유 상태에서 401이 발생하면,
 * 에이전트는 인증 프로필의 loginPageUrl로 사용자를 안내한다.
 */
public class SessionInterceptor implements HandlerInterceptor {

    private final BankStore store;

    public SessionInterceptor(BankStore store) {
        this.store = store;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 조회(GET)와 CORS preflight(OPTIONS)는 인증 대상이 아니라 항상 통과시킨다.
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        String token = extractSessionToken(request);
        if (store.isValidSession(token)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }

    private String extractSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthController.SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
