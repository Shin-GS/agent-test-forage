package com.testforge.client;

import com.testforge.client.openapi.TestForgeOperationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * AI Test Forge 클라이언트 자동 설정.
 *
 * 외부 서버가 이 라이브러리를 의존성에 추가하면 별도 설정 없이 동작:
 * - 스펙 자동 등록 (앱 기동 시 1회, 재기동 시 재등록으로 갱신)
 * - CORS 자동 허용
 * - 어노테이션 → OpenAPI 확장 필드 (springdoc 있을 때만)
 *
 * ai-test-forge.enabled=false 로 비활성화 가능.
 */
@AutoConfiguration
@EnableConfigurationProperties(TestForgeProperties.class)
@ConditionalOnProperty(prefix = "ai-test-forge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TestForgeAutoConfiguration {

    @Bean
    public SpecRegistrationService specRegistrationService(TestForgeProperties properties) {
        return new SpecRegistrationService(properties);
    }

    @Bean
    public TestForgeCorsConfig testForgeCorsConfig(TestForgeProperties properties) {
        return new TestForgeCorsConfig(properties);
    }

    @Bean
    @ConditionalOnClass(name = "org.springdoc.core.customizers.OperationCustomizer")
    public TestForgeOperationCustomizer testForgeOperationCustomizer() {
        return new TestForgeOperationCustomizer();
    }
}
