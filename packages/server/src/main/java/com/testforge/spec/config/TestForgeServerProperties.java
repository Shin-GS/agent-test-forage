package com.testforge.spec.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 스펙 등록 기능의 서버 측 설정.
 *
 * <pre>
 * ai-test-forge:
 *   register-token: ${TESTFORGE_TOKEN}
 *   stale-after-minutes: 5
 *   delete-after-hours: 24
 * </pre>
 */
@ConfigurationProperties(prefix = "ai-test-forge")
public class TestForgeServerProperties {

    /** register/heartbeat의 X-TestForge-Token 헤더에 기대하는 공유 시크릿 */
    private String registerToken;

    /** ACTIVE 스펙이 STALE로 전이되기까지 heartbeat 없는 시간 (분) */
    private long staleAfterMinutes = 5L;

    /** 스펙이 소프트 삭제되기까지 heartbeat 없는 시간 (시간, INACTIVE 제외) */
    private long deleteAfterHours = 24L;

    public String getRegisterToken() {
        return registerToken;
    }

    public void setRegisterToken(String registerToken) {
        this.registerToken = registerToken;
    }

    public long getStaleAfterMinutes() {
        return staleAfterMinutes;
    }

    public void setStaleAfterMinutes(long staleAfterMinutes) {
        this.staleAfterMinutes = staleAfterMinutes;
    }

    public long getDeleteAfterHours() {
        return deleteAfterHours;
    }

    public void setDeleteAfterHours(long deleteAfterHours) {
        this.deleteAfterHours = deleteAfterHours;
    }
}
