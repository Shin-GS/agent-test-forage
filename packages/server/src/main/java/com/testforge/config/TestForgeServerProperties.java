package com.testforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 스펙 등록 기능의 서버 측 설정.
 *
 * <pre>
 * ai-test-forge:
 *   register-token: ${TESTFORGE_TOKEN}
 * </pre>
 */
@ConfigurationProperties(prefix = "ai-test-forge")
public class TestForgeServerProperties {

    /** register의 X-TestForge-Token 헤더에 기대하는 공유 시크릿 */
    private String registerToken;

    public String getRegisterToken() {
        return registerToken;
    }

    public void setRegisterToken(String registerToken) {
        this.registerToken = registerToken;
    }
}
