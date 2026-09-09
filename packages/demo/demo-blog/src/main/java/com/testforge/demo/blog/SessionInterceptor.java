package com.testforge.demo.blog;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * BLOG_SESSION 쿠키를 검증하는 인터셉터. 인증이 필요한 경로(글 작성/삭제, 댓글 작성)에만 적용된다.
 * 쿠키가 없거나 유효하지 않으면 401을 반환한다.
 *
 * <p>레시피 최초 실행 시 세션 미보유 상태에서 401이 발생하면,
 * 에이전트는 인증 프로필의 loginPageUrl로 사용자를 안내한다.
 */
public class SessionInterceptor implements HandlerInterceptor {

    private final BlogStore store;

    public SessionInterceptor(BlogStore store) {
        this.store = store;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS preflight(OPTIONS)는 인증 대상이 아니다(쿠키/인증 헤더가 없을 수 있음).
        // 스프링 CORS 처리기가 OPTIONS 응답을 만들도록 통과시킨다.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
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
