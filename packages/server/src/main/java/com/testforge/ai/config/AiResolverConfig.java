package com.testforge.ai.config;

import com.testforge.ai.IntentResolver;
import com.testforge.ai.OpenAiCompatibleIntentResolver;
import com.testforge.ai.RuleBasedIntentResolver;
import com.testforge.ai.openai.OpenAiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link IntentResolver} 빈 선택 구성. 하나의 {@code @Configuration} 안에서 두 빈을 명시적으로 정의해
 * 조건 평가 순서를 안정적으로 만든다(같은 타입 {@code @Component} 간 {@code @ConditionalOnMissingBean}의
 * 스캔 순서 불확실성 회피).
 *
 * <ul>
 *   <li>{@code ai-test-forge.ai.api-key}가 설정되면 → {@link OpenAiCompatibleIntentResolver}(실제 AI)</li>
 *   <li>키가 없으면 → {@link RuleBasedIntentResolver}(규칙 기반 목) fallback</li>
 * </ul>
 *
 * <p>AI 빈이 먼저 평가되고, 목 빈은 {@code @ConditionalOnMissingBean}으로 "IntentResolver가 아직 없을
 * 때"만 등록되므로 정확히 하나만 활성화된다.
 */
@Configuration
public class AiResolverConfig {

    /** 키가 있으면 실제 AI 구현을 IntentResolver로 등록 */
    @Bean
    @ConditionalOnProperty(prefix = "ai-test-forge.ai", name = "api-key")
    public IntentResolver openAiIntentResolver(OpenAiClient client, AiSettings settings) {
        return new OpenAiCompatibleIntentResolver(client, settings);
    }

    /** 위 AI 빈이 없을 때(키 미설정)만 규칙 기반 목을 IntentResolver로 등록 */
    @Bean
    @ConditionalOnMissingBean(IntentResolver.class)
    public IntentResolver ruleBasedIntentResolver() {
        return new RuleBasedIntentResolver();
    }
}
