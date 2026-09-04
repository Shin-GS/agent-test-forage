package com.testforge.controller.auth;

import com.testforge.dto.auth.LoginRequest;
import com.testforge.dto.auth.UserResponse;
import com.testforge.entity.user.AppUser;
import com.testforge.security.AppUserPrincipal;
import com.testforge.security.CurrentUser;
import com.testforge.service.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 엔드포인트 (auth.md). 아이디/비밀번호 로그인 → 서버 세션 + 세션 쿠키(TESTFORGE_SESSION),
 * 로그아웃 → 세션 무효화 + 쿠키 삭제, /me → 현재 세션 사용자 조회.
 *
 * <p>JWT/Bearer 미사용. 인증은 세션 쿠키로 유지되며 SecurityContext는 세션에 저장된다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    // SecurityContext를 세션에 저장/로드하는 표준 저장소 (세션 쿠키가 이 세션을 가리킨다)
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 로그인. 성공 시 세션을 생성하고 SecurityContext를 세션에 저장한 뒤(→ 세션 쿠키 발급),
     * 사용자 정보를 반환한다. 실패(없는 아이디/비번 불일치/INACTIVE)는 401.
     */
    @PostMapping("/login")
    public UserResponse login(@RequestBody LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        AppUser user = authService.authenticate(request.username(), request.password());

        // 세션 고정 공격 방지: 로그인 시 새 세션으로 교체
        HttpSession existing = httpRequest.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        httpRequest.getSession(true);

        // 인증 주체를 SecurityContext에 세팅하고 세션에 저장 → 이후 요청은 세션 쿠키로 인증됨
        AppUserPrincipal principal = AppUserPrincipal.from(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return UserResponse.from(user);
    }

    /** 로그아웃. 세션을 무효화하고 SecurityContext를 비운다(쿠키는 무효 세션을 가리키게 됨). */
    @PostMapping("/logout")
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /** 현재 로그인 사용자 조회. 비로그인은 SecurityConfig에서 401 처리된다. */
    @GetMapping("/me")
    public UserResponse me() {
        AppUserPrincipal principal = CurrentUser.require();
        return new UserResponse(principal.getId(), principal.getUsername(),
                principal.getName(), principal.getRole().name());
    }
}
