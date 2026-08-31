package com.testforge.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.spec.entity.ApiEndpoint;
import com.testforge.spec.entity.ApiSpec;
import com.testforge.spec.entity.EndpointStatus;
import com.testforge.spec.entity.SpecStatus;
import com.testforge.spec.repository.ApiEndpointRepository;
import com.testforge.spec.repository.ApiSpecRepository;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
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

    // ── heartbeat: matching hash → none ──
    @Test
    void heartbeat_matchingHash_returnsNone() throws Exception {
        Map<String, Object> reg = registerBody(specJson(List.of("GET /api/v1/users")));
        mockMvc.perform(post("/api/v1/specs/register")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk());

        Map<String, Object> hb = new LinkedHashMap<>();
        hb.put("schemaVersion", "1");
        hb.put("baseUrl", BASE_URL);
        hb.put("specHash", reg.get("specHash"));

        mockMvc.perform(post("/api/v1/specs/heartbeat")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hb)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("none"));
    }

    // ── heartbeat: unknown baseUrl / mismatch → resend ──
    @Test
    void heartbeat_unknownOrMismatch_returnsResend() throws Exception {
        Map<String, Object> hbUnknown = new LinkedHashMap<>();
        hbUnknown.put("schemaVersion", "1");
        hbUnknown.put("baseUrl", "https://unknown.example.com");
        hbUnknown.put("specHash", "deadbeef");

        mockMvc.perform(post("/api/v1/specs/heartbeat")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hbUnknown)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("resend"));
    }

    // ── heartbeat: STALE spec returns to ACTIVE on matching heartbeat ──
    @Test
    void heartbeat_staleSpec_returnsToActive() throws Exception {
        Map<String, Object> reg = registerBody(specJson(List.of("GET /api/v1/users")));
        mockMvc.perform(post("/api/v1/specs/register")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk());

        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(BASE_URL).orElseThrow();
        spec.setStatus(SpecStatus.STALE);
        specRepository.save(spec);

        Map<String, Object> hb = new LinkedHashMap<>();
        hb.put("schemaVersion", "1");
        hb.put("baseUrl", BASE_URL);
        hb.put("specHash", reg.get("specHash"));

        mockMvc.perform(post("/api/v1/specs/heartbeat")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hb)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("none"));

        ApiSpec reloaded = specRepository.findByBaseUrlAndDeletedAtIsNull(BASE_URL).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SpecStatus.ACTIVE);
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
