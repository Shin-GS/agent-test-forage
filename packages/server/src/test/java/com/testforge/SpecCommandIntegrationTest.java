package com.testforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.enums.EndpointSource;
import com.testforge.entity.spec.enums.EndpointStatus;
import com.testforge.entity.spec.enums.SpecStatus;
import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.recipe.enums.Visibility;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.repository.spec.ApiEndpointRepository;
import com.testforge.repository.spec.ApiSpecRepository;
import com.testforge.repository.spec.AuthProfileRepository;
import com.testforge.repository.recipe.RecipeRepository;
import com.testforge.support.TestAuthSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 수동 스펙/엔드포인트 쓰기 API 통합 테스트 (H2).
 * 생성/수정/복제/삭제 + 권한(비-admin 403) + 중복 400 + LIBRARY 메타-only + GET 바디 미저장을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestAuthSupport.class)
class SpecCommandIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApiSpecRepository specRepository;

    @Autowired
    private ApiEndpointRepository endpointRepository;

    @Autowired
    private AuthProfileRepository authProfileRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private TestAuthSupport testAuth;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private static final long USER_ID = 1L;
    private static final long ADMIN_ID = 2L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        recipeRepository.deleteAll();
        endpointRepository.deleteAll();
        authProfileRepository.deleteAll();
        specRepository.deleteAll();
        testAuth.ensureUser(USER_ID, UserRole.USER);
        testAuth.ensureUser(ADMIN_ID, UserRole.ADMIN);
    }

    // ── 스펙 수동 생성: 201 + adminEdited + ACTIVE + 프로필 저장 ──
    @Test
    void createManualSpec_persistsWithAdminEdited() throws Exception {
        String body = """
                {
                  "name": "manual-shop",
                  "baseUrl": "https://manual.example.com",
                  "description": "수동 등록 서비스",
                  "domain": "commerce",
                  "capabilities": ["signup", "order"],
                  "notes": "staging",
                  "authProfiles": [{"name": "일반", "loginPageUrl": "https://manual.example.com/login"}]
                }
                """;

        String response = mockMvc.perform(post("/api/v1/specs/manual")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();
        ApiSpec saved = specRepository.findById(id).orElseThrow();
        assertThat(saved.getName()).isEqualTo("manual-shop");
        assertThat(saved.isAdminEdited()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(SpecStatus.ACTIVE);
        assertThat(authProfileRepository.findByApiSpecId(id)).hasSize(1);
    }

    // ── 스펙 수동 생성: 잘못된 baseUrl 스킴 → 400 ──
    @Test
    void createManualSpec_invalidBaseUrl_returns400() throws Exception {
        String body = """
                {"name": "bad", "baseUrl": "ftp://nope.example.com"}
                """;
        mockMvc.perform(post("/api/v1/specs/manual")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 스펙 수동 생성: baseUrl 병합 → 신규 생성 안 하고 기존 스펙에 병합 ──
    @Test
    void createManualSpec_sameBaseUrl_merges() throws Exception {
        ApiSpec existing = new ApiSpec("https://merge.example.com");
        existing.setName("old-name");
        existing.setStatus(SpecStatus.INACTIVE);
        existing = specRepository.save(existing);

        String body = """
                {"name": "new-name", "baseUrl": "https://merge.example.com"}
                """;
        String response = mockMvc.perform(post("/api/v1/specs/manual")
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();
        assertThat(id).isEqualTo(existing.getId());
        ApiSpec merged = specRepository.findById(id).orElseThrow();
        assertThat(merged.getName()).isEqualTo("new-name");
        assertThat(merged.getStatus()).isEqualTo(SpecStatus.ACTIVE);
    }

    // ── 스펙 수동 생성: 비-admin → 403 ──
    @Test
    void createManualSpec_nonAdmin_forbidden() throws Exception {
        String body = """
                {"name": "x", "baseUrl": "https://x.example.com"}
                """;
        mockMvc.perform(post("/api/v1/specs/manual")
                        .with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ── 스펙 수정: baseUrl 무시 + 메타 반영 ──
    @Test
    void updateSpec_ignoresBaseUrl_appliesMeta() throws Exception {
        ApiSpec spec = new ApiSpec("https://keep.example.com");
        spec.setName("keep");
        spec = specRepository.save(spec);

        String body = """
                {"name": "renamed", "baseUrl": "https://changed.example.com", "domain": "billing"}
                """;
        mockMvc.perform(patch("/api/v1/specs/{id}", spec.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        ApiSpec reloaded = specRepository.findById(spec.getId()).orElseThrow();
        assertThat(reloaded.getBaseUrl()).isEqualTo("https://keep.example.com");
        assertThat(reloaded.getName()).isEqualTo("renamed");
        assertThat(reloaded.getServiceDomain()).isEqualTo("billing");
        assertThat(reloaded.isAdminEdited()).isTrue();
    }

    // ── 엔드포인트 생성: MANUAL + operationJson 직렬화 (POST는 requestBody 포함) ──
    @Test
    void createEndpoint_manualWithBody() throws Exception {
        Long specId = saveSpec("https://ep.example.com");

        String body = """
                {
                  "method": "post",
                  "path": "/api/v1/users",
                  "summary": "create user",
                  "parameters": [{"name": "trace", "in": "query", "type": "string", "required": false}],
                  "requestBody": {"contentType": "application/json",
                    "fields": [{"name": "email", "type": "string", "required": true}]},
                  "headers": [{"name": "X-Req-Id", "required": true}],
                  "responses": [{"statusCode": "201", "description": "created",
                    "headers": [{"name": "Location", "description": "url"}]}],
                  "excluded": true,
                  "confirmRequired": true,
                  "confirmMessage": "확인?"
                }
                """;
        String response = mockMvc.perform(post("/api/v1/specs/{id}/endpoints", specId)
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long epId = objectMapper.readTree(response).get("id").asLong();
        ApiEndpoint saved = endpointRepository.findById(epId).orElseThrow();
        assertThat(saved.getSource()).isEqualTo(EndpointSource.MANUAL);
        assertThat(saved.getHttpMethod()).isEqualTo("POST");
        assertThat(saved.isExcluded()).isTrue();
        assertThat(saved.isConfirmRequired()).isTrue();
        assertThat(saved.getConfirmMessage()).isEqualTo("확인?");
        // operationJson: requestBody + 응답 헤더는 responses.headers, 요청 헤더는 parameters[in:header]
        String json = saved.getOperationJson();
        assertThat(json).contains("\"requestBody\"");
        assertThat(json).contains("\"in\":\"header\"");
        assertThat(json).contains("\"Location\"");
        assertThat(json).doesNotContain("\"example\"");
    }

    // ── 엔드포인트 생성: GET은 requestBody 미저장 ──
    @Test
    void createEndpoint_get_omitsRequestBody() throws Exception {
        Long specId = saveSpec("https://getep.example.com");

        String body = """
                {
                  "method": "GET",
                  "path": "/api/v1/items",
                  "requestBody": {"contentType": "application/json",
                    "fields": [{"name": "ignored", "type": "string"}]}
                }
                """;
        String response = mockMvc.perform(post("/api/v1/specs/{id}/endpoints", specId)
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long epId = objectMapper.readTree(response).get("id").asLong();
        ApiEndpoint saved = endpointRepository.findById(epId).orElseThrow();
        assertThat(saved.getOperationJson()).doesNotContain("requestBody");
    }

    // ── 엔드포인트 생성: (method,path) 중복 → 400 ──
    @Test
    void createEndpoint_duplicate_returns400() throws Exception {
        Long specId = saveSpec("https://dup.example.com");
        endpointRepository.save(newEndpoint(specId, "GET", "/api/v1/dup", EndpointSource.MANUAL));

        String body = """
                {"method": "GET", "path": "/api/v1/dup"}
                """;
        mockMvc.perform(post("/api/v1/specs/{id}/endpoints", specId)
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 엔드포인트 수정: LIBRARY는 메타만 반영, 스키마(path)는 무시 ──
    @Test
    void updateEndpoint_library_metaOnly() throws Exception {
        Long specId = saveSpec("https://lib.example.com");
        ApiEndpoint lib = newEndpoint(specId, "GET", "/api/v1/lib", EndpointSource.LIBRARY);
        lib.setOperationJson("{\"summary\":\"orig\"}");
        lib = endpointRepository.save(lib);

        String body = """
                {"method": "POST", "path": "/api/v1/hacked", "excluded": true, "confirmRequired": true,
                 "confirmMessage": "메타"}
                """;
        mockMvc.perform(patch("/api/v1/specs/{id}/endpoints/{eid}", specId, lib.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        ApiEndpoint reloaded = endpointRepository.findById(lib.getId()).orElseThrow();
        // 스키마 무시: method/path/operationJson 유지
        assertThat(reloaded.getHttpMethod()).isEqualTo("GET");
        assertThat(reloaded.getPath()).isEqualTo("/api/v1/lib");
        assertThat(reloaded.getOperationJson()).isEqualTo("{\"summary\":\"orig\"}");
        // 메타는 반영
        assertThat(reloaded.isExcluded()).isTrue();
        assertThat(reloaded.isConfirmRequired()).isTrue();
    }

    // ── 엔드포인트 삭제: MANUAL + 참조 레시피 없음 → 하드 삭제(행 제거) + 같은 method+path 재생성 성공 ──
    @Test
    void deleteEndpoint_manualUnreferenced_hardDeletesAndAllowsRecreate() throws Exception {
        Long specId = saveSpec("https://del.example.com");
        ApiEndpoint ep = endpointRepository.save(
                newEndpoint(specId, "GET", "/api/v1/del", EndpointSource.MANUAL));

        mockMvc.perform(delete("/api/v1/specs/{id}/endpoints/{eid}", specId, ep.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isNoContent());

        // 행이 실제로 사라짐
        assertThat(endpointRepository.findById(ep.getId())).isEmpty();

        // 같은 method+path 재생성 성공 (유니크 충돌 없음)
        String body = """
                {"method": "GET", "path": "/api/v1/del"}
                """;
        mockMvc.perform(post("/api/v1/specs/{id}/endpoints", specId)
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    // ── 엔드포인트 삭제: MANUAL + 참조 레시피 있음 → DEPRECATED로 보존(행 존재) ──
    @Test
    void deleteEndpoint_manualReferenced_softDeprecates() throws Exception {
        Long specId = saveSpec("https://delref.example.com");
        ApiEndpoint ep = endpointRepository.save(
                newEndpoint(specId, "GET", "/api/v1/delref", EndpointSource.MANUAL));
        saveRecipeReferencing(specId, ep.getId());

        mockMvc.perform(delete("/api/v1/specs/{id}/endpoints/{eid}", specId, ep.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isNoContent());

        ApiEndpoint reloaded = endpointRepository.findById(ep.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EndpointStatus.DEPRECATED);
    }

    // ── 엔드포인트 삭제: LIBRARY → 참조 없어도 DEPRECATED로 보존 ──
    @Test
    void deleteEndpoint_library_softDeprecates() throws Exception {
        Long specId = saveSpec("https://dellib.example.com");
        ApiEndpoint ep = endpointRepository.save(
                newEndpoint(specId, "GET", "/api/v1/dellib", EndpointSource.LIBRARY));

        mockMvc.perform(delete("/api/v1/specs/{id}/endpoints/{eid}", specId, ep.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isNoContent());

        ApiEndpoint reloaded = endpointRepository.findById(ep.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(EndpointStatus.DEPRECATED);
    }

    // ── 엔드포인트 복제: 사본은 MANUAL, path suffix로 충돌 회피 ──
    @Test
    void duplicateEndpoint_createsManualCopy() throws Exception {
        Long specId = saveSpec("https://copy.example.com");
        ApiEndpoint src = newEndpoint(specId, "GET", "/api/v1/src", EndpointSource.LIBRARY);
        src.setOperationJson("{\"summary\":\"s\"}");
        src = endpointRepository.save(src);

        String response = mockMvc.perform(
                        post("/api/v1/specs/{id}/endpoints/{eid}/duplicate", specId, src.getId())
                                .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long copyId = objectMapper.readTree(response).get("id").asLong();
        ApiEndpoint copy = endpointRepository.findById(copyId).orElseThrow();
        assertThat(copy.getSource()).isEqualTo(EndpointSource.MANUAL);
        assertThat(copy.getPath()).isEqualTo("/api/v1/src-copy");
        assertThat(copy.getOperationJson()).isEqualTo("{\"summary\":\"s\"}");
    }

    // ── 엔드포인트 복제: 같은 원본 2번 복제 → 기본 path 자동 suffix 충돌 회피(-copy, -copy-2) ──
    @Test
    void duplicateEndpoint_autoSuffixAvoidsCollision() throws Exception {
        Long specId = saveSpec("https://copy2.example.com");
        ApiEndpoint src = endpointRepository.save(
                newEndpoint(specId, "GET", "/api/v1/dup", EndpointSource.LIBRARY));

        String first = mockMvc.perform(
                        post("/api/v1/specs/{id}/endpoints/{eid}/duplicate", specId, src.getId())
                                .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ApiEndpoint firstCopy = endpointRepository
                .findById(objectMapper.readTree(first).get("id").asLong()).orElseThrow();
        assertThat(firstCopy.getPath()).isEqualTo("/api/v1/dup-copy");

        String second = mockMvc.perform(
                        post("/api/v1/specs/{id}/endpoints/{eid}/duplicate", specId, src.getId())
                                .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        ApiEndpoint secondCopy = endpointRepository
                .findById(objectMapper.readTree(second).get("id").asLong()).orElseThrow();
        assertThat(secondCopy.getPath()).isEqualTo("/api/v1/dup-copy-2");
    }

    // ── 엔드포인트 단건 조회: 조회는 공용(로그인 필수). admin/non-admin 모두 200, 비로그인만 401 ──
    @Test
    void getEndpoint_returnsDetail_forAdminAndNonAdmin() throws Exception {
        Long specId = saveSpec("https://one.example.com");
        ApiEndpoint ep = newEndpoint(specId, "POST", "/api/v1/one", EndpointSource.MANUAL);
        ep.setOperationJson("{\"summary\":\"one\"}");
        ep = endpointRepository.save(ep);

        // admin 조회: 200 + operationJson 포함
        mockMvc.perform(get("/api/v1/specs/{id}/endpoints/{eid}", specId, ep.getId())
                        .with(testAuth.as(ADMIN_ID, UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("POST"))
                .andExpect(jsonPath("$.source.code").value("MANUAL"))
                .andExpect(jsonPath("$.operationJson").value("{\"summary\":\"one\"}"));

        // non-admin(USER) 조회: 200 + operationJson 포함 (403 아님)
        mockMvc.perform(get("/api/v1/specs/{id}/endpoints/{eid}", specId, ep.getId())
                        .with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("POST"))
                .andExpect(jsonPath("$.operationJson").value("{\"summary\":\"one\"}"));

        // 비로그인 조회: 401 (인증 필요)
        mockMvc.perform(get("/api/v1/specs/{id}/endpoints/{eid}", specId, ep.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── 회귀 확인: non-admin은 쓰기 계열(생성/수정/삭제)에 여전히 403 ──
    @Test
    void endpointWrites_nonAdmin_stillForbidden() throws Exception {
        Long specId = saveSpec("https://write.example.com");
        ApiEndpoint ep = endpointRepository.save(
                newEndpoint(specId, "GET", "/api/v1/write", EndpointSource.MANUAL));

        // 생성 (POST)
        mockMvc.perform(post("/api/v1/specs/{id}/endpoints", specId)
                        .with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\": \"GET\", \"path\": \"/api/v1/new\"}"))
                .andExpect(status().isForbidden());

        // 수정 (PATCH)
        mockMvc.perform(patch("/api/v1/specs/{id}/endpoints/{eid}", specId, ep.getId())
                        .with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"excluded\": true}"))
                .andExpect(status().isForbidden());

        // 삭제 (DELETE)
        mockMvc.perform(delete("/api/v1/specs/{id}/endpoints/{eid}", specId, ep.getId())
                        .with(testAuth.as(USER_ID)))
                .andExpect(status().isForbidden());
    }

    // ── helpers ──

    private Long saveSpec(String baseUrl) {
        ApiSpec spec = new ApiSpec(baseUrl);
        spec.setName("svc");
        spec.setStatus(SpecStatus.ACTIVE);
        return specRepository.save(spec).getId();
    }

    private ApiEndpoint newEndpoint(Long specId, String method, String path, EndpointSource source) {
        ApiEndpoint ep = new ApiEndpoint(specId, method, path);
        ep.setSummary(method + " " + path);
        ep.setStatus(EndpointStatus.ACTIVE);
        ep.setSource(source);
        return ep;
    }

    /** 지정 endpointId를 type=api 스텝으로 참조하는 미삭제 레시피를 저장한다. */
    private void saveRecipeReferencing(Long specId, Long endpointId) {
        Recipe recipe = new Recipe(ADMIN_ID, specId, "ref-recipe");
        recipe.setVisibility(Visibility.COMMON);
        recipe.setStepsJson("[{\"type\":\"api\",\"endpointId\":" + endpointId + "}]");
        recipeRepository.save(recipe);
    }
}
