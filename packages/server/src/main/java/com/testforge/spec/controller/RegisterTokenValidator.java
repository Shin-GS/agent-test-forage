package com.testforge.spec.controller;

import com.testforge.common.error.ApiException;
import com.testforge.spec.config.TestForgeServerProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * X-TestForge-Token 헤더를 설정된 공유 시크릿과 비교하여 검증한다.
 * 타이밍 공격으로 토큰이 유출되지 않도록 상수 시간 비교를 사용한다.
 */
@Component
public class RegisterTokenValidator {

    private final TestForgeServerProperties properties;

    public RegisterTokenValidator(TestForgeServerProperties properties) {
        this.properties = properties;
    }

    public void validate(String presentedToken) {
        String expected = properties.getRegisterToken();
        if (expected == null || expected.isBlank()) {
            // 설정 누락: 익명 등록을 허용하지 않고 거부한다.
            throw ApiException.unauthorized();
        }
        if (presentedToken == null || !constantTimeEquals(expected, presentedToken)) {
            throw ApiException.unauthorized();
        }
    }

    /** 상수 시간 문자열 비교 (타이밍 공격 방지) */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
