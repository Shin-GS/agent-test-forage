package com.testforge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * FE(브라우저) → 에이전트 서버(BE) 호출을 위한 CORS 설정.
 *
 * <p>채팅/대화/실행 API 는 FE dev 서버(예: http://localhost:5173)에서 호출되며,
 * SSE 구독과 세션 쿠키 전달을 위해 credentials 를 허용해야 한다.
 * credentials 를 허용하면 와일드카드 origin("*") 을 쓸 수 없으므로 명시적 origin 목록을 받는다.
 *
 * <pre>
 * ai-test-forge:
 *   cors:
 *     allowed-origins: http://localhost:5173,http://localhost:4173
 * </pre>
 *
 * <p>레시피 API 실행은 브라우저가 외부 서비스로 직접 호출하는 별개 경로이며,
 * 그 CORS 는 각 대상 서비스(라이브러리 자동 설정)가 책임진다 — 이 설정과 무관하다.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebCorsConfig(
            @Value("${ai-test-forge.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
