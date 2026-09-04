package com.testforge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.repository.user.AppUserRepository;
import com.testforge.security.JsonAuthErrorHandlers;
import com.testforge.security.SessionUserRecheckFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 세션 쿠키 기반 인증/인가 설정 (auth.md). JWT/OAuth 미사용, 서버 인메모리 HttpSession + 세션 쿠키.
 *
 * <p>인가 규칙:
 * <ul>
 *   <li>permitAll: 로그인, 헬스체크, actuator health, 스펙 등록(라이브러리 토큰 인증)</li>
 *   <li>ADMIN 전용: {@code /api/v1/admin/**} (미달 시 403)</li>
 *   <li>그 외 {@code /api/v1/**}: 인증 필수 (미인증 401)</li>
 * </ul>
 *
 * <p>SSE 함정 대비: async 재디스패치(DispatcherType.ASYNC/ERROR)는 permitAll로 열어 SSE 스트림
 * 재디스패치 시 Access Denied가 나지 않게 한다.
 */
@Configuration
public class SecurityConfig {

    private final List<String> allowedOrigins;

    public SecurityConfig(
            @Value("${ai-test-forge.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /** 비밀번호 인코더 (bcrypt). 로그인 검증과 seed 해시에 공통 사용. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AppUserRepository userRepository) throws Exception {
        // JSON 에러 응답 직렬화용 (컨텍스트 ObjectMapper 빈에 의존하지 않도록 자체 생성)
        ObjectMapper objectMapper = new ObjectMapper();
        SessionUserRecheckFilter recheckFilter = new SessionUserRecheckFilter(userRepository);

        http
                // SPA + SameSite=Lax + 프록시 same-origin 조합으로 방어 → CSRF 토큰 미도입
                .csrf(AbstractHttpConfigurer::disable)
                // 기존 CORS 정책(credentials 허용 + 명시 origin)을 Security 필터에도 적용
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 세션은 필요 시 생성(로그인 시). Security가 세션을 강제로 만들지는 않음
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // SSE 재디스패치(async/error)는 인가 재평가 없이 통과 (Access Denied 방지)
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        // 공개 엔드포인트
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // 스펙 등록: 라이브러리 X-TestForge-Token 인증 (세션 인증 제외)
                        .requestMatchers("/api/v1/specs/register").permitAll()
                        // 관리자 전용 API
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 그 외 API는 인증 필수
                        .requestMatchers("/api/v1/**").authenticated()
                        // 그 외(정적 등)는 허용
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(JsonAuthErrorHandlers.entryPoint(objectMapper))
                        .accessDeniedHandler(JsonAuthErrorHandlers.accessDeniedHandler(objectMapper)))
                // 인증된 세션의 STATUS/ROLE을 매 요청 재확인
                .addFilterBefore(recheckFilter, UsernamePasswordAuthenticationFilter.class)
                // 폼 로그인/HTTP Basic 비활성화 (자체 /auth/login 사용)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /** Security 필터용 CORS 설정 (WebCorsConfig와 동일한 origin/credentials 정책). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
