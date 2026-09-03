package com.testforge.client;

import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * ai-test-forge 관련 origin 을 CORS 허용에 자동 등록.
 *
 * 레시피 실행 시 FE 브라우저가 이 외부 서버 API를 직접 호출하므로, 다음 origin 을 허용한다:
 * <ul>
 *   <li>server-url — ai-test-forge 서버(BE) origin</li>
 *   <li>web-origins — 레시피를 실행하는 웹 앱(FE) origin 목록 (실제 호출 주체)</li>
 * </ul>
 * 쿠키 전달(allowCredentials)을 허용한다. allowCredentials=true 이므로 와일드카드("*")는 쓸 수 없어
 * 명시적 origin 만 등록한다.
 *
 * 전제: 세션 쿠키가 SameSite=None; Secure 여야 크로스 도메인 전송됨 (외부 서버 소관).
 */
public class TestForgeCorsConfig implements WebMvcConfigurer {

    private final TestForgeProperties properties;

    public TestForgeCorsConfig(TestForgeProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = new ArrayList<>();
        if (properties.getServerUrl() != null && !properties.getServerUrl().isBlank()) {
            origins.add(properties.getServerUrl());
        }
        if (properties.getWebOrigins() != null) {
            for (String origin : properties.getWebOrigins()) {
                if (origin != null && !origin.isBlank()) {
                    origins.add(origin);
                }
            }
        }
        if (origins.isEmpty()) {
            return;
        }
        registry.addMapping("/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
