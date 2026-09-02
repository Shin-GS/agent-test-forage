package com.testforge.demo.shop;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * SHOP_SESSION 쿠키를 검증하는 인터셉터. 인증이 필요한 경로(orders/payments)에만 적용된다.
 * 쿠키가 없거나 유효하지 않으면 401을 반환한다.
 *
 * <p>레시피 최초 실행 시 세션 미보유 상태에서 401이 발생하면,
 * 에이전트는 인증 프로필의 loginPageUrl로 사용자를 안내한다.
 */
public class SessionInterceptor implements HandlerInterceptor {

    private final ShopStore store;

    public SessionInterceptor(ShopStore store) {
        this.store = store;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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
