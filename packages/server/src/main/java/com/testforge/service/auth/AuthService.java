package com.testforge.service.auth;

import com.testforge.common.error.ApiException;
import com.testforge.common.error.ErrorCode;
import com.testforge.entity.user.AppUser;
import com.testforge.entity.user.enums.UserStatus;
import com.testforge.repository.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 로그인 검증 로직. 아이디 조회 → bcrypt 비밀번호 검증 → 상태(ACTIVE) 확인을 수행하고,
 * 성공 시 LAST_LOGIN_AT을 갱신한다. 실패 원인(없는 아이디/비번 불일치/비활성)을 구분해 노출하지 않고
 * 동일 메시지로 401을 던진다(auth.md: 아이디/비번 구분 노출 금지).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 로그인 실패 공통 메시지 (원인 미구분) */
    private static final String LOGIN_FAILED_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 아이디/비밀번호로 사용자를 인증한다. 성공 시 LAST_LOGIN_AT을 갱신하고 사용자 엔티티를 반환한다.
     * 없는 아이디/비번 불일치/INACTIVE 모두 동일한 401로 처리한다.
     */
    @Transactional
    public AppUser authenticate(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isEmpty()) {
            throw loginFailed();
        }

        AppUser user = userRepository.findByUsername(username.trim())
                .orElseThrow(this::loginFailed);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw loginFailed();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw loginFailed();
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Login success: userId={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    /** 로그인 실패 예외 (401, 원인 미구분 공통 메시지) */
    private ApiException loginFailed() {
        return new ApiException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, LOGIN_FAILED_MESSAGE);
    }
}
