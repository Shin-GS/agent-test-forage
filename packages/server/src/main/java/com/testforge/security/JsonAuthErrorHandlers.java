package com.testforge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.util.Map;

/**
 * 인증/인가 실패를 GlobalExceptionHandler와 동일한 {@code { error: { code, message } }} 형식으로 응답한다.
 * Security 필터 체인에서 발생하는 401/403은 컨트롤러에 도달하지 못하므로 여기서 직접 JSON을 쓴다.
 *
 * <ul>
 *   <li>미인증(401): {@link AuthenticationEntryPoint} → UNAUTHORIZED</li>
 *   <li>권한 부족(403): {@link AccessDeniedHandler} → 관리자 전용 리소스 접근 거부</li>
 * </ul>
 */
public final class JsonAuthErrorHandlers {

    private JsonAuthErrorHandlers() {
    }

    /** 미인증 → 401 UNAUTHORIZED */
    public static AuthenticationEntryPoint entryPoint(ObjectMapper mapper) {
        return (HttpServletRequest request, HttpServletResponse response,
                AuthenticationException ex) ->
                write(mapper, response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Authentication required");
    }

    /** 권한 부족 → 403 */
    public static AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) {
        return (HttpServletRequest request, HttpServletResponse response,
                AccessDeniedException ex) ->
                write(mapper, response, HttpStatus.FORBIDDEN, ErrorCode.UNAUTHORIZED, "Access denied");
    }

    private static void write(ObjectMapper mapper, HttpServletResponse response,
                              HttpStatus status, ErrorCode code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = Map.of("error", Map.of("code", code.name(), "message", message));
        mapper.writeValue(response.getWriter(), body);
    }
}
