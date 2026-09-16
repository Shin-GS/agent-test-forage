package com.testforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.AuthProfile;
import com.testforge.entity.spec.enums.AuthProfileStatus;
import com.testforge.entity.spec.enums.EndpointSource;
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

        mockMvc.perform(post("/api/v1/specs")
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
        mockMvc.perform(post("/api/v1/specs")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isOk());

        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(BASE_URL).orElseThrow();
        Long endpointIdBefore = endpointRepository.findByApiSpecId(spec.getId()).get(0).getId();

        // Re-register with the same endpoint → PK must be preserved.
        String second = objectMapper.writeValueAsString(
                registerBody(specJson(List.of("GET /api/v1/users"))));
        mockMvc.perform(post("/api/v1/specs")
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
        mockMvc.perform(post("/api/v1/specs")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isOk());

        // Re-register without POST → POST endpoint should become DEPRECATED, not deleted.
        String second = objectMapper.writeValueAsString(
                registerBody(specJson(List.of("GET /api/v1/users"))));
        mockMvc.perform(post("/api/v1/specs")
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

    // ── register: MANUAL endpoint preserved on re-register (not deprecated), PK kept ──
    @Test
    void register_manualEndpoint_preservedOnReRegister() throws Exception {
        // 최초 등록으로 스펙 생성.
        register(registerBody(specJson(List.of("GET /api/v1/users"))));
        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(BASE_URL).orElseThrow();

        // 관리자가 수동 등록한 엔드포인트를 직접 삽입 (라이브러리 스펙에는 없는 method+path).
        ApiEndpoint manual = new ApiEndpoint(spec.getId(), "POST", "/api/v1/manual-only");
        manual.setSource(EndpointSource.MANUAL);
        manual.setStatus(EndpointStatus.ACTIVE);
        Long manualIdBefore = endpointRepository.save(manual).getId();

        // 그 method+path를 포함하지 않는 스펙으로 재등록.
        register(registerBody(specJson(List.of("GET /api/v1/users"))));

        ApiEndpoint after = endpointRepository.findByApiSpecId(spec.getId()).stream()
                .filter(e -> "/api/v1/manual-only".equals(e.getPath())).findFirst().orElseThrow();
        // MANUAL 행은 ACTIVE·source=MANUAL로 보존되고 PK가 유지되어야 한다.
        assertThat(after.getId()).isEqualTo(manualIdBefore);
        assertThat(after.getStatus()).isEqualTo(EndpointStatus.ACTIVE);
        assertThat(after.getSource()).isEqualTo(EndpointSource.MANUAL);
    }

    // ── register: MANUAL endpoint promoted to LIBRARY when library registers same method+path ──
    @Test
    void register_manualEndpoint_promotedToLibraryOnOverlap() throws Exception {
        // 최초 등록으로 스펙 생성.
        register(registerBody(specJson(List.of("GET /api/v1/users"))));
        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(BASE_URL).orElseThrow();

        // 관리자가 수동 등록한 엔드포인트 (나중에 라이브러리가 같은 method+path를 등록).
        ApiEndpoint manual = new ApiEndpoint(spec.getId(), "POST", "/api/v1/orders");
        manual.setSource(EndpointSource.MANUAL);
        manual.setStatus(EndpointStatus.ACTIVE);
        manual.setSummary("manual-summary");
        Long manualIdBefore = endpointRepository.save(manual).getId();

        // 라이브러리가 같은 POST /api/v1/orders를 등록 → 같은 행이 라이브러리 값으로 갱신.
        register(registerBody(specJson(List.of("GET /api/v1/users", "POST /api/v1/orders"))));

        ApiEndpoint after = endpointRepository.findByApiSpecId(spec.getId()).stream()
                .filter(e -> "/api/v1/orders".equals(e.getPath())).findFirst().orElseThrow();
        // PK 유지 + source=LIBRARY로 승격 + 라이브러리 값으로 갱신.
        assertThat(after.getId()).isEqualTo(manualIdBefore);
        assertThat(after.getSource()).isEqualTo(EndpointSource.LIBRARY);
        assertThat(after.getStatus()).isEqualTo(EndpointStatus.ACTIVE);
        assertThat(after.getSummary()).isEqualTo("post /api/v1/orders");
    }

    // ── register: LIBRARY endpoint removed → DEPRECATED (source-based branch) ──
    @Test
    void register_libraryEndpointRemoved_marksDeprecated() throws Exception {
        // 라이브러리가 두 엔드포인트를 등록 (둘 다 source=LIBRARY).
        register(registerBody(specJson(List.of("GET /api/v1/users", "POST /api/v1/users"))));
        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(BASE_URL).orElseThrow();
        ApiEndpoint postBefore = endpointRepository.findByApiSpecId(spec.getId()).stream()
                .filter(e -> "POST".equals(e.getHttpMethod())).findFirst().orElseThrow();
        assertThat(postBefore.getSource()).isEqualTo(EndpointSource.LIBRARY);

        // POST 없이 재등록 → LIBRARY 행이므로 DEPRECATED로 표시.
        register(registerBody(specJson(List.of("GET /api/v1/users"))));

        ApiEndpoint post = endpointRepository.findByApiSpecId(spec.getId()).stream()
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

    // ── $ref inline: requestBody schema가 실제 properties로 펼쳐지는지 ──
    @Test
    void register_inlinesRefSchema() throws Exception {
        // POST /bookings: requestBody schema가 $ref(#/components/schemas/Booking)
        Map<String, Object> bookingSchema = Map.of(
                "type", "object",
                "required", List.of("customerName"),
                "properties", Map.of(
                        "customerName", Map.of("type", "string"),
                        "seats", Map.of("type", "integer")));
        String specJson = specWithComponents(
                Map.of("Booking", bookingSchema),
                bodyRef("/bookings", "post", "#/components/schemas/Booking"));

        register(registerBody(specJson));

        String op = operationJsonFor("POST", "/bookings");
        // $ref가 아니라 실제 properties로 인라인되어야 한다.
        assertThat(op).doesNotContain("$ref");
        assertThat(op).contains("customerName");
        assertThat(op).contains("seats");
        assertThat(op).contains("\"required\"");
    }

    // ── $ref inline: 중첩 객체($ref 안에 또 $ref)까지 펼쳐지는지 ──
    @Test
    void register_inlinesNestedRefSchema() throws Exception {
        // Order.customer → $ref(Customer). 중첩까지 인라인되어야 한다.
        Map<String, Object> customerSchema = Map.of(
                "type", "object",
                "properties", Map.of("email", Map.of("type", "string")));
        Map<String, Object> orderSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "customer", Map.of("$ref", "#/components/schemas/Customer"),
                        "amount", Map.of("type", "number")));
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("Order", orderSchema);
        components.put("Customer", customerSchema);
        String specJson = specWithComponents(
                components, bodyRef("/orders", "post", "#/components/schemas/Order"));

        register(registerBody(specJson));

        String op = operationJsonFor("POST", "/orders");
        assertThat(op).doesNotContain("$ref");
        assertThat(op).contains("customer");
        assertThat(op).contains("amount");
        // 중첩 Customer의 필드까지 펼쳐졌는지.
        assertThat(op).contains("email");
    }

    // ── $ref inline: 순환 참조 스키마 → 예외 없이 등록 완료 (무한 루프 방지) ──
    @Test
    void register_circularRefSchema_completesWithoutHang() throws Exception {
        // Node.next → $ref(Node) 자기참조. 순환 지점은 $ref로 축약되어도 등록은 성공해야 한다.
        Map<String, Object> nodeSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "value", Map.of("type", "string"),
                        "next", Map.of("$ref", "#/components/schemas/Node")));
        String specJson = specWithComponents(
                Map.of("Node", nodeSchema),
                bodyRef("/nodes", "post", "#/components/schemas/Node"));

        register(registerBody(specJson));

        // 등록이 완료되고 엔드포인트가 저장되었으면 성공. (무한 루프/스택오버플로우 미발생)
        String op = operationJsonFor("POST", "/nodes");
        assertThat(op).contains("value");
    }

    // ── $ref inline: example 값은 operationJson에 포함되지 않는지 ──
    @Test
    void register_stripsExamplesFromSchema() throws Exception {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "code", Map.of(
                                "type", "string",
                                "example", "TOP-SECRET-EXAMPLE-VALUE")),
                "example", Map.of("code", "ANOTHER-EXAMPLE"));
        String specJson = specWithComponents(
                Map.of("Coupon", schema),
                bodyRef("/coupons", "post", "#/components/schemas/Coupon"));

        register(registerBody(specJson));

        String op = operationJsonFor("POST", "/coupons");
        // 구조는 유지되지만 example 값은 제거되어야 한다.
        assertThat(op).contains("code");
        assertThat(op).doesNotContain("TOP-SECRET-EXAMPLE-VALUE");
        assertThat(op).doesNotContain("ANOTHER-EXAMPLE");
    }

    // ── token: invalid → 401 ──
    @Test
    void register_invalidToken_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(
                registerBody(specJson(List.of("GET /api/v1/users"))));

        mockMvc.perform(post("/api/v1/specs")
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

        mockMvc.perform(post("/api/v1/specs")
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

        mockMvc.perform(post("/api/v1/specs")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SPEC"));
    }

    // ── helpers ──

    /** Performs a successful register POST with the given body map. */
    private void register(Map<String, Object> body) throws Exception {
        mockMvc.perform(post("/api/v1/specs")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    /** 저장된 스펙에서 주어진 method+path 엔드포인트의 operationJson을 읽어온다. */
    private String operationJsonFor(String method, String path) {
        ApiSpec spec = specRepository.findByBaseUrlAndDeletedAtIsNull(BASE_URL).orElseThrow();
        return endpointRepository.findByApiSpecId(spec.getId()).stream()
                .filter(e -> method.equals(e.getHttpMethod()) && path.equals(e.getPath()))
                .findFirst().orElseThrow().getOperationJson();
    }

    /** 단일 경로에 JSON 요청 바디($ref 참조)를 갖는 pathItem 맵을 만든다. */
    private Map<String, Object> bodyRef(String path, String method, String schemaRef) {
        Map<String, Object> operation = Map.of(
                "summary", method + " " + path,
                "requestBody", Map.of(
                        "content", Map.of(
                                "application/json", Map.of(
                                        "schema", Map.of("$ref", schemaRef)))),
                "responses", Map.of("200", Map.of("description", "ok")));
        Map<String, Object> pathItem = new LinkedHashMap<>();
        pathItem.put(method, operation);
        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put(path, pathItem);
        return paths;
    }

    /** components.schemas + paths를 갖는 OpenAPI 3.0 스펙 JSON을 만든다. */
    private String specWithComponents(Map<String, Object> schemas, Map<String, Object> paths) {
        Map<String, Object> openapi = new LinkedHashMap<>();
        openapi.put("openapi", "3.0.1");
        openapi.put("info", Map.of("title", "demo", "version", "1.0.0"));
        openapi.put("paths", paths);
        openapi.put("components", Map.of("schemas", schemas));
        try {
            return objectMapper.writeValueAsString(openapi);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        body.put("confluence", Map.of("spaceKey", "BT"));
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
