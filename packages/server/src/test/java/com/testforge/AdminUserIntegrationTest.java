package com.testforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.entity.user.AppUser;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.entity.user.enums.UserStatus;
import com.testforge.common.error.ApiException;
import com.testforge.repository.user.AppUserRepository;
import com.testforge.service.user.AdminUserService;
import com.testforge.support.TestAuthSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 사용자 관리 API 통합 테스트 (H2). 검증/생성/목록/역할·상태·비밀번호 변경,
 * 자기보호 + 마지막 ACTIVE ADMIN 보호, 404, 비밀번호 미노출을 검증한다(admin.md B / auth.md).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestAuthSupport.class)
class AdminUserIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private TestAuthSupport testAuth;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    /** 세션 관리자(요청 주체). TestAuthSupport가 APP_USER에 동일 id·ACTIVE·ADMIN으로 심는다. */
    private static final long ADMIN_ID = 1L;
    private static final long OTHER_ADMIN_ID = 2L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        userRepository.deleteAll();
        // 세션 주체(ADMIN). 자기보호/마지막 ADMIN 보호 테스트에서 target.id == CurrentUser.id 판정에 사용.
        testAuth.ensureUser(ADMIN_ID, UserRole.ADMIN);
    }

    @AfterEach
    void tearDown() {
        // 서비스 직접 호출 테스트가 심은 SecurityContext 정리
        testAuth.clear();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** 테스트 대상 사용자 저장 (실제 생성 id 확보). */
    private AppUser saveUser(String username, UserRole role, UserStatus status) {
        AppUser user = new AppUser(username, passwordEncoder.encode("password1"), "name-" + username, role);
        user.setStatus(status);
        return userRepository.save(user);
    }

    // ── 생성: 성공 ──
    @Test
    void create_success() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "newuser", "password", "password8",
                                "name", "새 사용자", "role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.name").value("새 사용자"))
                .andExpect(jsonPath("$.role.code").value("USER"))
                .andExpect(jsonPath("$.status.code").value("ACTIVE"))
                // 비밀번호 해시 미노출
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(userRepository.findByUsername("newuser")).isPresent();
    }

    // ── 생성: name 없이도 성공 (선택) ──
    @Test
    void create_withoutName_success() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "noname", "password", "password8", "role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("noname"));
    }

    // ── 생성: username 3자 미만 → 400 ──
    @Test
    void create_usernameTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "ab", "password", "password8", "role", "USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 생성: username 50자 초과 → 400 ──
    @Test
    void create_usernameTooLong_returns400() throws Exception {
        String longName = "a".repeat(51);
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", longName, "password", "password8", "role", "USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 생성: username 내부 공백 → 400 ──
    @Test
    void create_usernameWithWhitespace_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "us er", "password", "password8", "role", "USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 생성: 아이디 중복 → 400 ──
    @Test
    void create_duplicateUsername_returns400() throws Exception {
        saveUser("dup", UserRole.USER, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/admin/users")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "dup", "password", "password8", "role", "USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 생성: 비밀번호 8자 미만 → 400 ──
    @Test
    void create_passwordTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "shortpw", "password", "1234567", "role", "USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 목록: 검색(q) + 비밀번호 미노출 ──
    @Test
    void list_searchByUsername_noPassword() throws Exception {
        saveUser("alice", UserRole.USER, UserStatus.ACTIVE);
        saveUser("bob", UserRole.USER, UserStatus.ACTIVE);

        // 대소문자 무시 부분일치
        mockMvc.perform(get("/api/v1/admin/users").param("q", "ALI")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    // ── 목록: q 없으면 전체 (세션 admin 포함) ──
    @Test
    void list_all_returnsEveryone() throws Exception {
        saveUser("alice", UserRole.USER, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isOk())
                // 세션 admin(user1) + alice = 2
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ── 목록: lastActiveAdmin 게이팅 힌트 (유일 ACTIVE ADMIN만 true, 검색 필터와 무관) ──
    @Test
    void list_lastActiveAdminFlag() throws Exception {
        // 세션 admin(ADMIN_ID)이 유일한 ACTIVE ADMIN. alice(USER)는 false.
        saveUser("alice", UserRole.USER, UserStatus.ACTIVE);
        // INACTIVE ADMIN은 ACTIVE 카운트에서 제외되므로 세션 admin은 여전히 유일 ACTIVE ADMIN이자 true.
        saveUser("dormant", UserRole.ADMIN, UserStatus.INACTIVE);

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isOk())
                // 세션 admin: 유일 ACTIVE ADMIN → true
                .andExpect(jsonPath("$[?(@.role.code == 'ADMIN' && @.status.code == 'ACTIVE')].lastActiveAdmin")
                        .value(org.hamcrest.Matchers.hasItem(true)))
                // alice(USER): false
                .andExpect(jsonPath("$[?(@.username == 'alice')].lastActiveAdmin")
                        .value(org.hamcrest.Matchers.hasItem(false)))
                // INACTIVE ADMIN(dormant): false (ACTIVE 아님)
                .andExpect(jsonPath("$[?(@.username == 'dormant')].lastActiveAdmin")
                        .value(org.hamcrest.Matchers.hasItem(false)));
    }

    // ── 목록: ACTIVE ADMIN 2명이면 아무도 lastActiveAdmin 아님 ──
    @Test
    void list_twoActiveAdmins_noLastActiveAdmin() throws Exception {
        // 세션 admin(ADMIN_ID) + secondadmin = ACTIVE ADMIN 2명 → 둘 다 false
        saveUser("secondadmin", UserRole.ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/admin/users")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isOk())
                // 모든 행의 lastActiveAdmin이 false (ACTIVE ADMIN 2명이라 유일 관리자 없음)
                .andExpect(jsonPath("$[?(@.lastActiveAdmin == true)]")
                        .value(org.hamcrest.Matchers.empty()));
    }

    // ── 역할 변경: USER → ADMIN 성공 ──
    @Test
    void updateRole_promote_success() throws Exception {
        AppUser target = saveUser("promote", UserRole.USER, UserStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", target.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role.code").value("ADMIN"));

        assertThat(userRepository.findById(target.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.ADMIN);
    }

    // ── 상태 변경: ACTIVE → INACTIVE 성공 ──
    @Test
    void updateStatus_deactivate_success() throws Exception {
        AppUser target = saveUser("deact", UserRole.USER, UserStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", target.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "INACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("INACTIVE"));

        assertThat(userRepository.findById(target.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.INACTIVE);
    }

    // ── 자기보호: 본인 역할 강등 → 400 ──
    @Test
    void updateRole_selfDemotion_returns400() throws Exception {
        // 세션 admin(ADMIN_ID)을 본인이 강등 시도. 다른 ACTIVE admin(OTHER_ADMIN)이 있어 마지막 보호는 안 걸리게 함.
        testAuth.ensureUser(OTHER_ADMIN_ID, UserRole.ADMIN);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", ADMIN_ID)
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        assertThat(userRepository.findById(ADMIN_ID).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    // ── 자기보호: 본인 비활성 → 400 ──
    @Test
    void updateStatus_selfDeactivate_returns400() throws Exception {
        testAuth.ensureUser(OTHER_ADMIN_ID, UserRole.ADMIN);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", ADMIN_ID)
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "INACTIVE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        assertThat(userRepository.findById(ADMIN_ID).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    // ── 마지막 ACTIVE ADMIN 보호: 유일한 ACTIVE ADMIN 강등 → 400 ──
    // 세션 주체(ADMIN_ID)가 유일한 ACTIVE ADMIN이며 본인을 강등 시도.
    // 자기보호와 마지막 ACTIVE ADMIN 보호가 둘 다 걸린다(admin.md: 둘 다 발동). 결과는 400.
    @Test
    void updateRole_lastActiveAdminDemotion_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", ADMIN_ID)
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        assertThat(userRepository.findById(ADMIN_ID).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    // ── 마지막 ACTIVE ADMIN 보호(자기보호와 독립): 서비스 레이어 직접 검증 ──
    // HTTP 경로에서는 세션 주체가 항상 ACTIVE ADMIN이라 "타인이 유일한 ACTIVE ADMIN" 상황을 만들 수 없다
    // (recheck 필터가 INACTIVE 세션을 401 처리). 따라서 서비스에 직접 SecurityContext를 심어
    // "요청자(USER 세션) ≠ 대상(유일한 ACTIVE ADMIN)"인 순수 마지막 ADMIN 보호를 검증한다.
    @Test
    void updateRole_lastActiveAdmin_independentOfSelf_serviceLevel() {
        AppUser onlyAdmin = saveUser("onlyadmin", UserRole.ADMIN, UserStatus.ACTIVE);
        // 세션 admin(ADMIN_ID)을 삭제하여 onlyAdmin이 유일한 ACTIVE ADMIN이 되게 한다.
        userRepository.deleteById(ADMIN_ID);

        // 요청자는 대상이 아닌 다른 사용자(자기보호 미발동). 마지막 ACTIVE ADMIN 보호만 발동해야 한다.
        testAuth.setContext(9999L, UserRole.ADMIN);

        assertThatThrownBy(() -> adminUserService.updateRole(onlyAdmin.getId(), UserRole.USER))
                .isInstanceOf(ApiException.class);

        assertThat(userRepository.findById(onlyAdmin.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.ADMIN);
    }

    // ── 마지막 ACTIVE ADMIN 보호(비활성, 서비스 레벨): 유일한 ACTIVE ADMIN 비활성 → 400 ──
    @Test
    void updateStatus_lastActiveAdmin_independentOfSelf_serviceLevel() {
        AppUser onlyAdmin = saveUser("onlyadmin2", UserRole.ADMIN, UserStatus.ACTIVE);
        userRepository.deleteById(ADMIN_ID);
        testAuth.setContext(9999L, UserRole.ADMIN);

        assertThatThrownBy(() -> adminUserService.updateStatus(onlyAdmin.getId(), UserStatus.INACTIVE))
                .isInstanceOf(ApiException.class);

        assertThat(userRepository.findById(onlyAdmin.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    // ── 마지막 ACTIVE ADMIN 보호: ACTIVE ADMIN 2명이면 타인 강등 통과 ──
    @Test
    void updateRole_twoActiveAdmins_demotionAllowed() throws Exception {
        // 세션 admin(ADMIN_ID, ACTIVE) + 대상 admin(ACTIVE) = ACTIVE ADMIN 2명
        AppUser targetAdmin = saveUser("secondadmin", UserRole.ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", targetAdmin.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role.code").value("USER"));
    }

    // ── 마지막 ACTIVE ADMIN 보호: INACTIVE ADMIN은 카운트 제외 → 강등 통과 ──
    @Test
    void updateRole_inactiveAdminNotCounted_demotionAllowed() throws Exception {
        // 세션 admin(ADMIN_ID)이 유일한 ACTIVE ADMIN. 대상은 INACTIVE ADMIN.
        // INACTIVE ADMIN은 마지막 ACTIVE ADMIN 카운트에서 제외되므로 강등 가능(400 아님).
        AppUser inactiveAdmin = saveUser("inactiveadmin", UserRole.ADMIN, UserStatus.INACTIVE);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", inactiveAdmin.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role.code").value("USER"));
    }

    // ── 멱등: 이미 같은 상태 재적용은 200(보호 규칙 미발동) ──
    @Test
    void updateStatus_sameStatus_isIdempotent() throws Exception {
        // 세션 admin이 유일한 ACTIVE ADMIN이지만, ACTIVE→ACTIVE 재적용은 관리자 수를 줄이지 않아 통과.
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", ADMIN_ID)
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("ACTIVE"));
    }

    // ── 대상 없음: 역할 변경 404 ──
    @Test
    void updateRole_unknownUser_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", 999999L)
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    // ── 대상 없음: 상태 변경 404 ──
    @Test
    void updateStatus_unknownUser_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/status", 999999L)
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "INACTIVE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    // ── 대상 없음: 비밀번호 변경 404 ──
    @Test
    void updatePassword_unknownUser_returns404() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/password", 999999L)
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "password8"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    // ── 비밀번호 변경: 성공(204) + 해시 실제 갱신 ──
    @Test
    void updatePassword_success() throws Exception {
        AppUser target = saveUser("pwuser", UserRole.USER, UserStatus.ACTIVE);
        String oldHash = target.getPassword();

        mockMvc.perform(patch("/api/v1/admin/users/{id}/password", target.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "newpassword9"))))
                .andExpect(status().isNoContent());

        AppUser reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getPassword()).isNotEqualTo(oldHash);
        assertThat(passwordEncoder.matches("newpassword9", reloaded.getPassword())).isTrue();
    }

    // ── 비밀번호 변경: 8자 미만 → 400 ──
    @Test
    void updatePassword_tooShort_returns400() throws Exception {
        AppUser target = saveUser("pwshort", UserRole.USER, UserStatus.ACTIVE);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/password", target.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "1234567"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 비밀번호 변경: 본인도 허용(204) ──
    @Test
    void updatePassword_self_allowed() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{id}/password", ADMIN_ID)
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "selfnewpw8"))))
                .andExpect(status().isNoContent());
    }

    // ── 권한: 비-admin(USER)은 403 ──
    @Test
    void nonAdmin_forbidden() throws Exception {
        long userId = 3L;
        testAuth.ensureUser(userId, UserRole.USER);

        mockMvc.perform(get("/api/v1/admin/users").with(testAuth.as(userId, UserRole.USER)))
                .andExpect(status().isForbidden());
    }
}
