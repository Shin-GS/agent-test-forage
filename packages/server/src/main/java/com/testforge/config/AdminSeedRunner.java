package com.testforge.config;

import com.testforge.entity.user.AppUser;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.repository.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 서버 기동 시 최초 관리자 계정을 seed 한다(db/user.md 최초 관리자 계정 생성).
 *
 * <ul>
 *   <li>ADMIN 역할 계정이 이미 하나라도 있으면 스킵(멱등).</li>
 *   <li>{@code ai-test-forge.admin-seed}의 username/password 중 하나라도 비면 스킵(로그 경고,
 *       약한 기본 계정 자동 생성 방지 — 기본값 없음).</li>
 *   <li>둘 다 있으면 BCrypt로 해시하여 ADMIN 계정을 생성한다.</li>
 * </ul>
 */
@Component
public class AdminSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminSeedProperties properties;

    public AdminSeedRunner(AppUserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AdminSeedProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 이미 관리자 존재 → 스킵 (멱등)
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            log.info("Admin seed skipped: an ADMIN account already exists");
            return;
        }

        String username = properties.getUsername();
        String password = properties.getPassword();
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            log.warn("Admin seed skipped: ADMIN_SEED_USERNAME/ADMIN_SEED_PASSWORD not configured. "
                    + "Create the first admin manually (see db/user.md).");
            return;
        }

        AppUser admin = new AppUser(
                username.trim(),
                passwordEncoder.encode(password),
                "관리자",
                UserRole.ADMIN);
        AppUser saved = userRepository.save(admin);
        log.info("Admin seed created: userId={}, username={}", saved.getId(), saved.getUsername());
    }
}
