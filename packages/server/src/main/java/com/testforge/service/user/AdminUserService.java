package com.testforge.service.user;

import com.testforge.common.error.ApiException;
import com.testforge.dto.user.AdminUserSummaryResponse;
import com.testforge.dto.user.CreateUserRequest;
import com.testforge.entity.user.AppUser;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.entity.user.enums.UserStatus;
import com.testforge.repository.user.AppUserRepository;
import com.testforge.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 관리자 사용자 관리(admin.md 사용자 관리 B) 로직. 검증/보호 규칙을 전부 이 서비스에 둔다
 * (컨트롤러는 얇게 유지). 권한(ADMIN)은 SecurityConfig의 /api/v1/admin/** 가드가 강제하므로
 * 여기서는 도메인 규칙(username/비밀번호/자기보호/마지막 ACTIVE ADMIN 보호)만 담당한다.
 *
 * <p>userId/role은 클라이언트 값이 아니라 세션(CurrentUser)에서 도출한다(auth.md 위조 금지).
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    /** username 길이 하한 (trim 후) */
    private static final int USERNAME_MIN = 3;
    /** username 길이 상한 (trim 후) */
    private static final int USERNAME_MAX = 50;
    /** name 길이 상한 */
    private static final int NAME_MAX = 100;
    /** 비밀번호 길이 하한 */
    private static final int PASSWORD_MIN = 8;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 사용자 목록 (username 오름차순). {@code q}는 username 부분일치(대소문자 무시).
     * 페이징 없이 전체 조회 후 서비스에서 필터링한다(소수 전제). 비밀번호 해시는 응답에 미포함.
     *
     * @param q username 검색어 (null/blank면 전체)
     */
    @Transactional(readOnly = true)
    public List<AdminUserSummaryResponse> list(String q) {
        String needle = (q == null) ? "" : q.trim().toLowerCase(Locale.ROOT);
        // ACTIVE ADMIN이 정확히 1명이면 그 계정이 "마지막 ACTIVE ADMIN". 검색 필터와 무관하게
        // 전체 기준으로 카운트해 각 행의 게이팅 힌트를 계산한다(FE 검색 시 부정확 방지).
        long activeAdmins = userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
        return userRepository.findAllByOrderByUsernameAsc().stream()
                .filter(u -> needle.isEmpty()
                        || u.getUsername().toLowerCase(Locale.ROOT).contains(needle))
                .map(u -> AdminUserSummaryResponse.from(u, isLastActiveAdmin(u, activeAdmins)))
                .toList();
    }

    /**
     * 사용자 생성. username(trim 후 3~50자·공백 불가·중복 불가), password(8자+), name(선택·100자 이하)을
     * 검증한 뒤 bcrypt 해시로 저장한다.
     *
     * @return 생성된 사용자 요약(비밀번호 미포함)
     */
    @Transactional
    public AdminUserSummaryResponse create(CreateUserRequest request) {
        String username = normalizeUsername(request.username());
        validateUsername(username);
        if (userRepository.existsByUsername(username)) {
            throw ApiException.invalidRequest("Username already exists: " + username);
        }

        String name = normalizeName(request.name());
        validateName(name);
        validatePassword(request.password());
        if (request.role() == null) {
            throw ApiException.invalidRequest("Role is required");
        }

        String hashed = passwordEncoder.encode(request.password());
        AppUser user = userRepository.save(new AppUser(username, hashed, name, request.role()));
        log.info("Admin created user: userId={}, role={}", user.getId(), user.getRole());
        return toResponse(user);
    }

    /**
     * 역할 변경 (USER ↔ ADMIN). 대상 없으면 404.
     *
     * <p>보호 규칙(강등 = ADMIN→USER 인 경우에만 검증. 같은 값 재적용/승격은 통과):
     * <ul>
     *   <li>본인 강등: 차단(400)</li>
     *   <li>마지막 ACTIVE ADMIN 강등: 차단(400)</li>
     * </ul>
     */
    @Transactional
    public AdminUserSummaryResponse updateRole(Long userId, UserRole newRole) {
        AppUser target = requireUser(userId);
        if (newRole == null) {
            throw ApiException.invalidRequest("Role is required");
        }

        // 멱등: 같은 역할 재적용은 변경 없이 통과
        if (target.getRole() == newRole) {
            return toResponse(target);
        }

        // 강등(ADMIN→USER)일 때만 보호 규칙 검증. 승격(USER→ADMIN)은 관리자 수를 줄이지 않으므로 통과.
        boolean isDemotion = target.getRole() == UserRole.ADMIN && newRole == UserRole.USER;
        if (isDemotion) {
            guardSelf(target, "role");
            guardLastActiveAdmin(target, "role");
        }

        target.setRole(newRole);
        userRepository.save(target);
        log.info("Admin changed user role: userId={}, newRole={}", userId, newRole);
        return toResponse(target);
    }

    /**
     * 상태 변경 (ACTIVE ↔ INACTIVE). 대상 없으면 404.
     *
     * <p>보호 규칙(비활성 = ACTIVE→INACTIVE 인 경우에만 검증. 같은 값 재적용/활성화는 통과):
     * <ul>
     *   <li>본인 비활성: 차단(400)</li>
     *   <li>마지막 ACTIVE ADMIN 비활성: 차단(400)</li>
     * </ul>
     */
    @Transactional
    public AdminUserSummaryResponse updateStatus(Long userId, UserStatus newStatus) {
        AppUser target = requireUser(userId);
        if (newStatus == null) {
            throw ApiException.invalidRequest("Status is required");
        }

        // 멱등: 같은 상태 재적용은 변경 없이 통과
        if (target.getStatus() == newStatus) {
            return toResponse(target);
        }

        // 비활성(ACTIVE→INACTIVE)일 때만 보호 규칙 검증. 활성화(INACTIVE→ACTIVE)는 관리자 수를 줄이지 않으므로 통과.
        boolean isDeactivation = target.getStatus() == UserStatus.ACTIVE
                && newStatus == UserStatus.INACTIVE;
        if (isDeactivation) {
            guardSelf(target, "status");
            guardLastActiveAdmin(target, "status");
        }

        target.setStatus(newStatus);
        userRepository.save(target);
        log.info("Admin changed user status: userId={}, newStatus={}", userId, newStatus);
        return toResponse(target);
    }

    /**
     * 비밀번호 지정 (관리자가 새 값 직접 지정). 대상 없으면 404, 8자 미만이면 400.
     * 본인 비밀번호 변경도 허용된다. 기존 세션에 영향을 주지 않는다(세션 재확인은 STATUS/ROLE만).
     */
    @Transactional
    public void updatePassword(Long userId, String newPassword) {
        AppUser target = requireUser(userId);
        validatePassword(newPassword);
        target.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(target);
        log.info("Admin reset user password: userId={}", userId);
    }

    // ── 검증 헬퍼 ──

    /** username 정규화: null 방어 + trim */
    private String normalizeUsername(String raw) {
        return raw == null ? "" : raw.trim();
    }

    /** name 정규화: null은 그대로 유지(선택), 값이 있으면 trim */
    private String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** username 규칙: trim 후 3~50자, 내부 공백 불가 */
    private void validateUsername(String username) {
        if (username.length() < USERNAME_MIN || username.length() > USERNAME_MAX) {
            throw ApiException.invalidRequest(
                    "Username must be between " + USERNAME_MIN + " and " + USERNAME_MAX + " characters");
        }
        // trim 후에도 내부 공백(스페이스/탭 등)이 있으면 거부
        if (containsWhitespace(username)) {
            throw ApiException.invalidRequest("Username must not contain whitespace");
        }
    }

    /** name 규칙: 선택(null 허용), 입력 시 100자 이하 */
    private void validateName(String name) {
        if (name != null && name.length() > NAME_MAX) {
            throw ApiException.invalidRequest("Name must be at most " + NAME_MAX + " characters");
        }
    }

    /** 비밀번호 규칙: null 불가 + 최소 8자 */
    private void validatePassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN) {
            throw ApiException.invalidRequest(
                    "Password must be at least " + PASSWORD_MIN + " characters");
        }
    }

    /** 문자열에 공백 문자가 하나라도 있는지 */
    private boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** 대상 사용자 조회 (없으면 404) */
    private AppUser requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.userNotFound(userId));
    }

    /** 단건 응답 매핑 (ACTIVE ADMIN 수를 조회해 게이팅 힌트 계산) */
    private AdminUserSummaryResponse toResponse(AppUser user) {
        long activeAdmins = userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
        return AdminUserSummaryResponse.from(user, isLastActiveAdmin(user, activeAdmins));
    }

    /**
     * 이 계정이 "유일한 ACTIVE ADMIN"인지. 대상이 ACTIVE ADMIN이고 전체 ACTIVE ADMIN이 1명일 때만 true.
     * (INACTIVE ADMIN은 카운트에서 제외되므로 대상이 ACTIVE가 아니거나 ADMIN이 아니면 false.)
     */
    private boolean isLastActiveAdmin(AppUser user, long activeAdmins) {
        return user.getRole() == UserRole.ADMIN
                && user.getStatus() == UserStatus.ACTIVE
                && activeAdmins <= 1;
    }

    /** 본인 보호: 대상이 세션 사용자 본인이면 차단(400) */
    private void guardSelf(AppUser target, String field) {
        if (target.getId().equals(CurrentUser.id())) {
            throw ApiException.invalidRequest(
                    "Cannot change your own " + field + " (self-protection)");
        }
    }

    /**
     * 마지막 ACTIVE ADMIN 보호: 대상이 현재 ACTIVE ADMIN이고, ACTIVE ADMIN 수가 1 이하면 차단(400).
     * INACTIVE ADMIN은 카운트에서 제외되므로, 대상이 이미 INACTIVE거나 ADMIN이 아니면 보호 대상이 아니다.
     */
    private void guardLastActiveAdmin(AppUser target, String field) {
        long activeAdmins = userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE);
        if (isLastActiveAdmin(target, activeAdmins)) {
            throw ApiException.invalidRequest(
                    "Cannot change " + field + " of the last active admin");
        }
    }
}
