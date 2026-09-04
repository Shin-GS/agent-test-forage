package com.testforge.security;

import com.testforge.entity.user.AppUser;
import com.testforge.entity.user.enums.UserStatus;
import com.testforge.repository.user.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 인증된 세션 사용자의 현재 STATUS/ROLE을 매 요청마다 DB로 재확인한다(auth.md 세션 상태/역할 재확인).
 *
 * <ul>
 *   <li>계정이 삭제되었거나 {@code STATUS=INACTIVE}면 세션을 폐기하고 SecurityContext를 비운다
 *       → 이후 인가 단계에서 401로 거부된다(비활성 계정 다음 요청부터 반영).</li>
 *   <li>역할(ROLE)이 바뀌었으면 현재 역할로 authentication을 교체해 다음 요청부터 새 역할로 평가한다.</li>
 * </ul>
 *
 * <p>프로토타입 방침: 세션 저장소 전체를 순회하는 즉시 강제 로그아웃은 하지 않고, "매 요청 재검증"으로
 * 반영 지연을 최소화한다. 요청당 DB 1회 조회 비용은 허용한다.
 */
public class SessionUserRecheckFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionUserRecheckFilter.class);

    private final AppUserRepository userRepository;

    public SessionUserRecheckFilter(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication auth = context.getAuthentication();

        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AppUserPrincipal principal) {
            Optional<AppUser> found = userRepository.findById(principal.getId());

            if (found.isEmpty() || found.get().getStatus() == UserStatus.INACTIVE) {
                // 계정 삭제/비활성 → 세션 폐기 + 컨텍스트 비움. 인가 단계에서 401 처리된다.
                log.info("Invalidating session for inactive/removed user: userId={}", principal.getId());
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
            } else if (found.get().getRole() != principal.getRole()) {
                // 역할 변경 → 현재 역할로 authentication 교체 (다음 처리부터 새 역할로 평가)
                AppUserPrincipal refreshed = AppUserPrincipal.from(found.get());
                var updated = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        refreshed, null, refreshed.getAuthorities());
                context.setAuthentication(updated);
                SecurityContextHolder.setContext(context);
            }
        }

        chain.doFilter(request, response);
    }
}
