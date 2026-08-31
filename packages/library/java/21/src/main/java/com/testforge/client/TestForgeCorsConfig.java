package com.testforge.client;

import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ai-test-forge 도메인을 CORS 허용에 자동 등록.
 *
 * 레시피 실행 시 FE 브라우저가 이 외부 서버 API를 직접 호출하므로,
 * server-url을 allowedOrigins에 추가하고 쿠키 전달(allowCredentials)을 허용한다.
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
        if (properties.getServerUrl() == null || properties.getServerUrl().isBlank()) {
            return;
        }
        registry.addMapping("/**")
                .allowedOrigins(properties.getServerUrl())
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
