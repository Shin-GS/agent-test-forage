package com.testforge.demo.hr;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HR_SESSION 쿠키를 검증하는 인터셉터. 인증이 필요한 경로(직원 등록, 휴가 신청/승인)에만 적용된다.
 * 쿠키가 없거나 유효하지 않으면 401을 반환한다.
 *
 * <p>같은 경로에서 GET(공개 조회)과 POST(인증 쓰기)가 공존하므로,
 * 경로가 아니라 HTTP 메서드로 분리한다. GET/OPTIONS 은 항상 통과시키고 쓰기(POST)만 보호한다.
 *
 * <p>레시피 최초 실행 시 세션 미보유 상태에서 401이 발생하면,
 * 에이전트는 인증 프로필의 loginPageUrl로 사용자를 안내한다.
 */
public class SessionInterceptor implements HandlerInterceptor {

    private final HrStore store;

    public SessionInterceptor(HrStore store) {
        this.store = store;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // GET(공개 조회) 및 CORS preflight(OPTIONS)는 인증 대상이 아니므로 항상 통과시킨다.
        // 같은 경로에 GET 공개 조회와 POST 인증 쓰기가 공존하기 때문에 메서드로 분리한다.
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
