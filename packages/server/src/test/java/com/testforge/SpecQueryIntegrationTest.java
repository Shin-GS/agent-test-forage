package com.testforge;

import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.AuthProfile;
import com.testforge.entity.spec.enums.EndpointStatus;
import com.testforge.entity.spec.enums.SpecStatus;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.repository.spec.ApiEndpointRepository;
import com.testforge.repository.spec.ApiSpecRepository;
import com.testforge.repository.spec.AuthProfileRepository;
import com.testforge.support.TestAuthSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스펙 조회/관리 API 통합 테스트 (H2). 목록/상세/404/상태 전이를 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestAuthSupport.class)
class SpecQueryIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApiSpecRepository specRepository;

    @Autowired
    private ApiEndpointRepository endpointRepository;

    @Autowired
    private AuthProfileRepository authProfileRepository;

    @Autowired
    private TestAuthSupport testAuth;

    private MockMvc mockMvc;
    private static final long USER_ID = 1L;
    private static final long ADMIN_ID = 2L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        endpointRepository.deleteAll();
        authProfileRepository.deleteAll();
        specRepository.deleteAll();
        testAuth.ensureUser(USER_ID, UserRole.USER);
        testAuth.ensureUser(ADMIN_ID, UserRole.ADMIN);
    }

    // ── list: 삭제 스펙 제외 + name 오름차순 + apiCount(ACTIVE만) ──
    @Test
    void list_excludesDeleted_countsActiveEndpoints() throws Exception {
        // beta: ACTIVE 1개 + DEPRECATED 1개 → apiCount는 1
        ApiSpec beta = newSpec("beta-service", "https://beta.example.com", SpecStatus.ACTIVE);
        beta = specRepository.save(beta);
        endpointRepository.save(newEndpoint(beta.getId(), "GET", "/api/v1/a", EndpointStatus.ACTIVE));
        endpointRepository.save(newEndpoint(beta.getId(), "POST", "/api/v1/a", EndpointStatus.DEPRECATED));

        // alpha: ACTIVE 2개 → apiCount는 2
        ApiSpec alpha = newSpec("alpha-service", "https://alpha.example.com", SpecStatus.ACTIVE);
        alpha = specRepository.save(alpha);
        endpointRepository.save(newEndpoint(alpha.getId(), "GET", "/api/v1/b", EndpointStatus.ACTIVE));
        endpointRepository.save(newEndpoint(alpha.getId(), "GET", "/api/v1/c", EndpointStatus.ACTIVE));

        // deleted: 소프트 삭제 → 목록 제외
        ApiSpec deleted = newSpec("zeta-service", "https://zeta.example.com", SpecStatus.ACTIVE);
        deleted.setDeletedAt(LocalDateTime.now());
        specRepository.save(deleted);

        mockMvc.perform(get("/api/v1/specs").with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                // 삭제 제외 → 2건, name 오름차순 → alpha 먼저
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("alpha-service"))
                .andExpect(jsonPath("$[0].apiCount").value(2))
                .andExpect(jsonPath("$[0].status.code").value("ACTIVE"))
                .andExpect(jsonPath("$[0].status.description").value("정상"))
                .andExpect(jsonPath("$[1].name").value("beta-service"))
                .andExpect(jsonPath("$[1].apiCount").value(1));
    }

    // ── detail: 엔드포인트/프로필/진단/capabilities 포함 ──
    @Test
    void detail_includesEndpointsProfilesAndCapabilities() throws Exception {
        ApiSpec spec = newSpec("demo-shop", "https://shop.example.com", SpecStatus.ACTIVE);
        spec.setServiceDescription("온라인 쇼핑몰 API");
        spec.setServiceDomain("commerce");
        spec.setServiceCapabilities("[\"signup\",\"order\"]");
        spec.setServiceNotes("staging");
        spec.setClientLang("java");
        spec.setClientVersion("0.0.1");
        spec.setSchemaVersion("1");
        spec = specRepository.save(spec);

        endpointRepository.save(newEndpoint(spec.getId(), "GET", "/api/v1/users", EndpointStatus.ACTIVE));
        authProfileRepository.save(new AuthProfile(spec.getId(), "일반", "https://shop.example.com/login"));

        mockMvc.perform(get("/api/v1/specs/{id}", spec.getId()).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("demo-shop"))
                .andExpect(jsonPath("$.serviceInfo.description").value("온라인 쇼핑몰 API"))
                .andExpect(jsonPath("$.serviceInfo.capabilities.length()").value(2))
                .andExpect(jsonPath("$.serviceInfo.capabilities[0]").value("signup"))
                .andExpect(jsonPath("$.endpoints.length()").value(1))
                .andExpect(jsonPath("$.endpoints[0].method").value("GET"))
                .andExpect(jsonPath("$.endpoints[0].status.code").value("ACTIVE"))
                .andExpect(jsonPath("$.authProfiles.length()").value(1))
                .andExpect(jsonPath("$.authProfiles[0].name").value("일반"))
                .andExpect(jsonPath("$.diagnostics.clientLang").value("java"))
                .andExpect(jsonPath("$.diagnostics.schemaVersion").value("1"));
    }

    // ── detail: 없는 ID → 404 ──
    @Test
    void detail_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/specs/{id}", 999999L).with(testAuth.as(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SPEC_NOT_FOUND"));
    }

    // ── detail: 삭제된 스펙 → 404 ──
    @Test
    void detail_deletedSpec_returns404() throws Exception {
        ApiSpec spec = newSpec("gone", "https://gone.example.com", SpecStatus.ACTIVE);
        spec.setDeletedAt(LocalDateTime.now());
        spec = specRepository.save(spec);

        mockMvc.perform(get("/api/v1/specs/{id}", spec.getId()).with(testAuth.as(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SPEC_NOT_FOUND"));
    }

    // ── deactivate: ACTIVE → INACTIVE ──
    @Test
    void deactivate_setsInactive() throws Exception {
        ApiSpec spec = specRepository.save(
                newSpec("svc", "https://svc.example.com", SpecStatus.ACTIVE));

        mockMvc.perform(patch("/api/v1/specs/{id}/deactivate", spec.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isNoContent());

        ApiSpec reloaded = specRepository.findById(spec.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SpecStatus.INACTIVE);
    }

    // ── activate: INACTIVE → ACTIVE ──
    @Test
    void activate_fromInactive_setsActive() throws Exception {
        ApiSpec spec = specRepository.save(
                newSpec("svc", "https://svc.example.com", SpecStatus.INACTIVE));

        mockMvc.perform(patch("/api/v1/specs/{id}/activate", spec.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isNoContent());

        ApiSpec reloaded = specRepository.findById(spec.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SpecStatus.ACTIVE);
    }

    // ── delete: 소프트 삭제 → DELETED_AT 설정 + 목록/상세에서 사라짐 ──
    @Test
    void delete_softDeletesSpec() throws Exception {
        ApiSpec spec = specRepository.save(
                newSpec("svc", "https://svc.example.com", SpecStatus.ACTIVE));

        mockMvc.perform(delete("/api/v1/specs/{id}", spec.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isNoContent());

        ApiSpec reloaded = specRepository.findById(spec.getId()).orElseThrow();
        assertThat(reloaded.getDeletedAt()).isNotNull();

        // 삭제 후 상세 조회는 404
        mockMvc.perform(get("/api/v1/specs/{id}", spec.getId()).with(testAuth.as(USER_ID)))
                .andExpect(status().isNotFound());
    }

    // ── delete: 없는 ID → 404 ──
    @Test
    void delete_unknownId_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/specs/{id}", 999999L)
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SPEC_NOT_FOUND"));
    }

    // ── list: 공용(기본)은 ACTIVE만 반환 (INACTIVE 제외) ──
    @Test
    void list_public_returnsActiveOnly() throws Exception {
        specRepository.save(newSpec("active-svc", "https://a.example.com", SpecStatus.ACTIVE));
        specRepository.save(newSpec("inactive-svc", "https://i.example.com", SpecStatus.INACTIVE));

        mockMvc.perform(get("/api/v1/specs").with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("active-svc"));
    }

    // ── list: 비-admin이 includeInactive=true를 줘도 조용히 무시 → ACTIVE만 ──
    @Test
    void list_nonAdmin_includeInactiveIgnored() throws Exception {
        specRepository.save(newSpec("active-svc", "https://a.example.com", SpecStatus.ACTIVE));
        specRepository.save(newSpec("inactive-svc", "https://i.example.com", SpecStatus.INACTIVE));

        mockMvc.perform(get("/api/v1/specs").param("includeInactive", "true").with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("active-svc"));
    }

    // ── list: ADMIN + includeInactive=true → 전체(INACTIVE 포함, 삭제 제외) ──
    @Test
    void list_admin_includeInactive_returnsAll() throws Exception {
        specRepository.save(newSpec("active-svc", "https://a.example.com", SpecStatus.ACTIVE));
        specRepository.save(newSpec("inactive-svc", "https://i.example.com", SpecStatus.INACTIVE));
        ApiSpec deleted = newSpec("deleted-svc", "https://d.example.com", SpecStatus.INACTIVE);
        deleted.setDeletedAt(LocalDateTime.now());
        specRepository.save(deleted);

        mockMvc.perform(get("/api/v1/specs").param("includeInactive", "true")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isOk())
                // 삭제는 항상 제외 → 2건 (active + inactive)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("active-svc"))
                .andExpect(jsonPath("$[1].name").value("inactive-svc"));
    }

    // ── detail: INACTIVE 스펙도 상세 조회 가능 (삭제만 404) ──
    @Test
    void detail_inactiveSpec_isAccessible() throws Exception {
        ApiSpec spec = specRepository.save(
                newSpec("inactive-svc", "https://i.example.com", SpecStatus.INACTIVE));

        mockMvc.perform(get("/api/v1/specs/{id}", spec.getId()).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("INACTIVE"));
    }

    // ── 관리 액션: 비-admin은 403 (deactivate/activate/delete) ──
    @Test
    void managementActions_nonAdmin_forbidden() throws Exception {
        ApiSpec spec = specRepository.save(
                newSpec("svc", "https://svc.example.com", SpecStatus.ACTIVE));

        mockMvc.perform(patch("/api/v1/specs/{id}/deactivate", spec.getId()).with(testAuth.as(USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(patch("/api/v1/specs/{id}/activate", spec.getId()).with(testAuth.as(USER_ID)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/specs/{id}", spec.getId()).with(testAuth.as(USER_ID)))
                .andExpect(status().isForbidden());

        // 상태/삭제가 변경되지 않았는지 확인
        ApiSpec reloaded = specRepository.findById(spec.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SpecStatus.ACTIVE);
        assertThat(reloaded.getDeletedAt()).isNull();
    }

    // ── 멱등: 이미 ACTIVE에 activate → no-op 204 ──
    @Test
    void activate_alreadyActive_isNoOp() throws Exception {
        ApiSpec spec = specRepository.save(
                newSpec("svc", "https://svc.example.com", SpecStatus.ACTIVE));

        mockMvc.perform(patch("/api/v1/specs/{id}/activate", spec.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isNoContent());

        assertThat(specRepository.findById(spec.getId()).orElseThrow().getStatus())
                .isEqualTo(SpecStatus.ACTIVE);
    }

    // ── 멱등: 이미 INACTIVE에 deactivate → no-op 204 ──
    @Test
    void deactivate_alreadyInactive_isNoOp() throws Exception {
        ApiSpec spec = specRepository.save(
                newSpec("svc", "https://svc.example.com", SpecStatus.INACTIVE));

        mockMvc.perform(patch("/api/v1/specs/{id}/deactivate", spec.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isNoContent());

        assertThat(specRepository.findById(spec.getId()).orElseThrow().getStatus())
                .isEqualTo(SpecStatus.INACTIVE);
    }

    // ── INACTIVE 스펙도 소프트 삭제 허용 (ACTIVE 강제 아님) ──
    @Test
    void delete_inactiveSpec_allowed() throws Exception {
        ApiSpec spec = specRepository.save(
                newSpec("svc", "https://svc.example.com", SpecStatus.INACTIVE));

        mockMvc.perform(delete("/api/v1/specs/{id}", spec.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isNoContent());

        assertThat(specRepository.findById(spec.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    // ── helpers ──

    private ApiSpec newSpec(String name, String baseUrl, SpecStatus status) {
        ApiSpec spec = new ApiSpec(baseUrl);
        spec.setName(name);
        spec.setStatus(status);
        return spec;
    }

    private ApiEndpoint newEndpoint(Long specId, String method, String path, EndpointStatus status) {
        ApiEndpoint endpoint = new ApiEndpoint(specId, method, path);
        endpoint.setSummary(method + " " + path);
        endpoint.setStatus(status);
        return endpoint;
    }
}
