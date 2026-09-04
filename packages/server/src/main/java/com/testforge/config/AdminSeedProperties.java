package com.testforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 최초 관리자 계정 seed 설정 (db/user.md). 서버 기동 시 ADMIN 계정이 하나도 없으면
 * 이 아이디/비밀번호로 관리자 계정을 자동 생성한다. 두 값 중 하나라도 비어 있으면 seed를 스킵한다
 * (약한 기본 계정 자동 생성 방지 — 기본값을 두지 않음).
 *
 * <pre>
 * ai-test-forge:
 *   admin-seed:
 *     username: ${ADMIN_SEED_USERNAME}
 *     password: ${ADMIN_SEED_PASSWORD}
 * </pre>
 */
@ConfigurationProperties(prefix = "ai-test-forge.admin-seed")
public class AdminSeedProperties {

    /** 초기 관리자 아이디 (비어 있으면 seed 스킵) */
    private String username;

    /** 초기 관리자 평문 비밀번호 (seed 시 BCrypt 해시하여 저장, 비어 있으면 seed 스킵) */
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
