package com.testforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.AuthProfile;
import com.testforge.entity.spec.enums.AuthProfileStatus;
import com.testforge.entity.spec.enums.EndpointStatus;
import com.testforge.repository.spec.ApiEndpointRepository;
import com.testforge.repository.spec.ApiSpecRepository;
import com.testforge.repository.spec.AuthProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class SpecRegistrationIntegrationTest {

    private static final String TOKEN_HEADER = "X-TestForge-Token";
    private static final String VALID_TOKEN = "test-token";
    private static final String BASE_URL = "https://shop-api.example.com";

    @Autowired
    private WebApplicationContext context;

    // Local Jackson 2 mapper for building request JSON (Boot 4 uses Jackson 3 beans).
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        authProfileRepository.deleteAll();
        endpointRepository.deleteAll();
        specRepository.deleteAll();
    }

    // ── register: new ──
    @Test
    void register_new_createsSpecAndEndpoints() throws Exception {
        String body = objectMapper.writeValueAsString(
                registerBody(specJson(List.of("GET /api/v1/users", "POST /api/v1/users"))));

        mockMvc.perform(post("/api/v1/specs/register")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specId").exists())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(BASE_URL).orElseThrow();
        List<ApiEndpoint> endpoints = endpointRepository.findByApiSpecId(spec.getId());
        assertThat(endpoints).hasSize(2);
        assertThat(endpoints).allMatch(e -> e.getStatus() == EndpointStatus.ACTIVE);
        assertThat(spec.getClientLang()).isEqualTo("java");
        assertThat(spec.getSchemaVersion()).isEqualTo("1");
    }

    // ── register: re-register upsert keeps endpoint PK ──
    @Test
    void register_reRegister_keepsEndpointPk() throws Exception {
        String first = objectMapper.writeValueAsString(
                registerBody(specJson(List.of("GET /api/v1/users"))));
        mockMvc.perform(post("/api/v1/specs/register")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isOk());

        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(BASE_URL).orElseThrow();
        Long endpointIdBefore = endpointRepository.findByApiSpecId(spec.getId()).get(0).getId();

        // Re-register with the same endpoint → PK must be preserved.
        String second = objectMapper.writeValueAsString(
                registerBody(specJson(List.of("GET /api/v1/users"))));
        mockMvc.perform(post("/api/v1/specs/register")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(second))
                .andExpect(status().isOk());

        List<ApiEndpoint> after = endpointRepository.findByApiSpecId(spec.getId());
        assertThat(after).hasSize(1);
        assertThat(after.get(0).getId()).isEqualTo(endpointIdBefore);
    }

    // ── register: endpoint removed → DEPRECATED ──
    @Test
    void register_endpointRemoved_marksDeprecated() throws Exception {
        String first = objectMapper.writeValueAsString(
                registerBody(specJson(List.of("GET /api/v1/users", "POST /api/v1/users"))));
        mockMvc.perform(post("/api/v1/specs/register")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isOk());

        // Re-register without POST → POST endpoint should become DEPRECATED, not deleted.
        String second = objectMapper.writeValueAsString(
                registerBody(specJson(List.of("GET /api/v1/users"))));
        mockMvc.perform(post("/api/v1/specs/register")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(second))
                .andExpect(status().isOk());

        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(BASE_URL).orElseThrow();
        List<ApiEndpoint> endpoints = endpointRepository.findByApiSpecId(spec.getId());
        assertThat(endpoints).hasSize(2);
        ApiEndpoint post = endpoints.stream()
                .filter(e -> "POST".equals(e.getHttpMethod())).findFirst().orElseThrow();
        assertThat(post.getStatus()).isEqualTo(EndpointStatus.DEPRECATED);
    }

    // ── register: re-register upsert keeps auth profile PK ──
    @Test
    void register_reRegister_keepsAuthProfilePk() throws Exception {
        Map<String, Object> first = registerBody(specJson(List.of("GET /api/v1/users")));
        first.put("authProfiles", List.of(
                Map.of("name", "default", "loginPageUrl", "https://a.example.com/login")));
        register(first);

        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(BASE_URL).orElseThrow();
        Long profileIdBefore = authProfileRepository.findByApiSpecId(spec.getId()).get(0).getId();

        // Re-register with same profile name but changed loginPageUrl → PK kept, URL updated.
        Map<String, Object> second = registerBody(specJson(List.of("GET /api/v1/users")));
        second.put("authProfiles", List.of(
                Map.of("name", "default", "loginPageUrl", "https://b.example.com/login")));
        register(second);

        List<AuthProfile> after = authProfileRepository.findByApiSpecId(spec.getId());
        assertThat(after).hasSize(1);
        assertThat(after.get(0).getId()).isEqualTo(profileIdBefore);
        assertThat(after.get(0).getLoginPageUrl()).isEqualTo("https://b.example.com/login");
        assertThat(after.get(0).getStatus()).isEqualTo(AuthProfileStatus.ACTIVE);
    }

    // ── register: auth profile removed → INACTIVE, then revived → ACTIVE ──
    @Test
    void register_authProfileRemovedThenRevived_softUpsert() throws Exception {
        Map<String, Object> first = registerBody(specJson(List.of("GET /api/v1/users")));
        first.put("authProfiles", List.of(
                Map.of("name", "default", "loginPageUrl", "https://a.example.com/login"),
                Map.of("name", "admin", "loginPageUrl", "https://a.example.com/admin-login")));
        register(first);

        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(BASE_URL).orElseThrow();
        Long adminIdBefore = authProfileRepository.findByApiSpecId(spec.getId()).stream()
                .filter(p -> "admin".equals(p.getName())).findFirst().orElseThrow().getId();

        // Re-register without "admin" → admin becomes INACTIVE, not deleted.
        Map<String, Object> second = registerBody(specJson(List.of("GET /api/v1/users")));
        second.put("authProfiles", List.of(
                Map.of("name", "default", "loginPageUrl", "https://a.example.com/login")));
        register(second);

        List<AuthProfile> afterRemove = authProfileRepository.findByApiSpecId(spec.getId());
        assertThat(afterRemove).hasSize(2);
        AuthProfile admin = afterRemove.stream()
                .filter(p -> "admin".equals(p.getName())).findFirst().orElseThrow();
        assertThat(admin.getStatus()).isEqualTo(AuthProfileStatus.INACTIVE);
        assertThat(admin.getId()).isEqualTo(adminIdBefore);

        // Re-register with "admin" again → revived to ACTIVE, same PK.
        Map<String, Object> third = registerBody(specJson(List.of("GET /api/v1/users")));
        third.put("authProfiles", List.of(
                Map.of("name", "default", "loginPageUrl", "https://a.example.com/login"),
                Map.of("name", "admin", "loginPageUrl", "https://a.example.com/admin-login")));
        register(third);

        List<AuthProfile> afterRevive = authProfileRepository.findByApiSpecId(spec.getId());
        assertThat(afterRevive).hasSize(2);
        AuthProfile revived = afterRevive.stream()
                .filter(p -> "admin".equals(p.getName())).findFirst().orElseThrow();
        assertThat(revived.getStatus()).isEqualTo(AuthProfileStatus.ACTIVE);
        assertThat(revived.getId()).isEqualTo(adminIdBefore);
    }

    // ── extensions: @TestForgeConfirm / @TestForgeExclude 확장 파싱 ──
    // 라이브러리(TestForgeOperationCustomizer)가 만드는 형식과 동일한 확장을 스펙에 넣어,
    // 서버 파서가 confirmRequired/excluded로 매핑하는지 검증한다.
    @Test
    void register_parsesTestForgeExtensions() throws Exception {
        // POST /pay: x-test-forge-confirm {message}, GET /admin: x-test-forge-exclude true
        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("/pay", Map.of("post", Map.of(
                "summary", "pay",
                "responses", Map.of("200", Map.of("description", "ok")),
                "x-test-forge-confirm", Map.of("message", "실제 결제가 발생합니다"))));
        paths.put("/admin", Map.of("get", Map.of(
                "summary", "admin",
                "responses", Map.of("200", Map.of("description", "ok")),
                "x-test-forge-exclude", true)));
        Map<String, Object> openapi = new LinkedHashMap<>();
        openapi.put("openapi", "3.0.1");
        openapi.put("info", Map.of("title", "demo", "version", "1.0.0"));
        openapi.put("paths", paths);
        String specJson = objectMapper.writeValueAsString(openapi);

        register(registerBody(specJson));

        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(BASE_URL).orElseThrow();
        List<ApiEndpoint> endpoints = endpointRepository.findByApiSpecId(spec.getId());

        ApiEndpoint pay = endpoints.stream()
                .filter(e -> "/pay".equals(e.getPath())).findFirst().orElseThrow();
        assertThat(pay.isConfirmRequired()).isTrue();
        assertThat(pay.getConfirmMessage()).isEqualTo("실제 결제가 발생합니다");

        ApiEndpoint admin = endpoints.stream()
                .filter(e -> "/admin".equals(e.getPath())).findFirst().orElseThrow();
        assertThat(admin.isExcluded()).isTrue();
    }

    // ── token: invalid → 401 ──
    @Test
    void register_invalidToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(
                registerBody(specJson(List.of("GET /api/v1/users"))));

        mockMvc.perform(post("/api/v1/specs/register")
                        .header(TOKEN_HEADER, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ── schema version: unsupported → 400 ──
    @Test
    void register_unsupportedSchemaVersion_returns400() throws Exception {
        Map<String, Object> reg = registerBody(specJson(List.of("GET /api/v1/users")));
        reg.put("schemaVersion", "999");

        mockMvc.perform(post("/api/v1/specs/register")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_SCHEMA_VERSION"));
    }

    // ── invalid spec json → 400 ──
    @Test
    void register_invalidSpecJson_returns400() throws Exception {
        Map<String, Object> reg = registerBody("not a valid openapi doc {{{");

        mockMvc.perform(post("/api/v1/specs/register")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SPEC"));
    }

    // ── helpers ──

    /** Performs a successful register POST with the given body map. */
    private void register(Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/api/v1/specs/register")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private Map<String, Object> registerBody(String specJson) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", "1");
        body.put("client", Map.of("lang", "java", "version", "0.0.1"));
        body.put("name", "demo-shop");
        body.put("baseUrl", BASE_URL);
        body.put("specJson", specJson);
        body.put("specHash", "hash-" + Integer.toHexString(specJson.hashCode()));
        body.put("serviceInfo", Map.of(
                "description", "shop api",
                "domain", "commerce",
                "capabilities", List.of("signup", "order"),
                "notes", "staging"));
        body.put("jira", Map.of("projectKey", "SHOP"));
        body.put("authProfiles", List.of(
                Map.of("name", "default", "loginPageUrl", "https://shop-api.example.com/login")));
        return body;
    }

    /**
     * Builds a minimal OpenAPI 3.0 JSON with the given "METHOD /path" operations.
     */
    private String specJson(List<String> methodPaths) {
        Map<String, Object> paths = new LinkedHashMap<>();
        for (String mp : methodPaths) {
            String[] parts = mp.split(" ", 2);
            String method = parts[0].toLowerCase();
            String path = parts[1];
            @SuppressWarnings("unchecked")
            Map<String, Object> pathItem = (Map<String, Object>) paths
                    .computeIfAbsent(path, k -> new LinkedHashMap<String, Object>());
            pathItem.put(method, Map.of(
                    "summary", method + " " + path,
                    "responses", Map.of("200", Map.of("description", "ok"))));
        }
        Map<String, Object> openapi = new LinkedHashMap<>();
        openapi.put("openapi", "3.0.1");
        openapi.put("info", Map.of("title", "demo", "version", "1.0.0"));
        openapi.put("paths", paths);
        try {
            return objectMapper.writeValueAsString(openapi);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
