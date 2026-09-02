package com.testforge;

import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.AuthProfile;
import com.testforge.entity.spec.enums.EndpointStatus;
import com.testforge.entity.spec.enums.SpecStatus;
import com.testforge.repository.spec.ApiEndpointRepository;
import com.testforge.repository.spec.ApiSpecRepository;
import com.testforge.repository.spec.AuthProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
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
class SpecQueryIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApiSpecRepository specRepository;

    @Autowired
    private ApiEndpointRepository endpointRepository;

    @Autowired
    private AuthProfileRepository authProfileRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        endpointRepository.deleteAll();
        authProfileRepository.deleteAll();
        specRepository.deleteAll();
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

        mockMvc.perform(get("/api/v1/specs"))
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

        mockMvc.perform(get("/api/v1/specs/{id}", spec.getId()))
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
        mockMvc.perform(get("/api/v1/specs/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SPEC_NOT_FOUND"));
    }

    // ── detail: 삭제된 스펙 → 404 ──
    @Test
    void detail_deletedSpec_returns404() throws Exception {
        ApiSpec spec = newSpec("gone", "https://gone.example.com", SpecStatus.ACTIVE);
        spec.setDeletedAt(LocalDateTime.now());
        spec = specRepository.save(spec);

        mockMvc.perform(get("/api/v1/specs/{id}", spec.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SPEC_NOT_FOUND"));
    }

    // ── deactivate: ACTIVE → INACTIVE ──
    @Test
    void deactivate_setsInactive() throws Exception {
        ApiSpec spec = specRepository.save(
                newSpec("svc", "https://svc.example.com", SpecStatus.ACTIVE));

        mockMvc.perform(patch("/api/v1/specs/{id}/deactivate", spec.getId()))
                .andExpect(status().isNoContent());

        ApiSpec reloaded = specRepository.findById(spec.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SpecStatus.INACTIVE);
    }

    // ── activate: INACTIVE → ACTIVE ──
    @Test
    void activate_fromInactive_setsActive() throws Exception {
        ApiSpec spec = specRepository.save(
                newSpec("svc", "https://svc.example.com", SpecStatus.INACTIVE));

        mockMvc.perform(patch("/api/v1/specs/{id}/activate", spec.getId()))
                .andExpect(status().isNoContent());

        ApiSpec reloaded = specRepository.findById(spec.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SpecStatus.ACTIVE);
    }

    // ── delete: 소프트 삭제 → DELETED_AT 설정 + 목록/상세에서 사라짐 ──
    @Test
    void delete_softDeletesSpec() throws Exception {
        ApiSpec spec = specRepository.save(
                newSpec("svc", "https://svc.example.com", SpecStatus.ACTIVE));

        mockMvc.perform(delete("/api/v1/specs/{id}", spec.getId()))
                .andExpect(status().isNoContent());

        ApiSpec reloaded = specRepository.findById(spec.getId()).orElseThrow();
        assertThat(reloaded.getDeletedAt()).isNotNull();

        // 삭제 후 상세 조회는 404
        mockMvc.perform(get("/api/v1/specs/{id}", spec.getId()))
                .andExpect(status().isNotFound());
    }

    // ── delete: 없는 ID → 404 ──
    @Test
    void delete_unknownId_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/specs/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SPEC_NOT_FOUND"));
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
