package com.testforge;

import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.enums.EndpointStatus;
import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.recipe.enums.ValidationStatus;
import com.testforge.entity.recipe.enums.Visibility;
import com.testforge.repository.spec.ApiEndpointRepository;
import com.testforge.repository.spec.ApiSpecRepository;
import com.testforge.repository.recipe.RecipeRepository;
import com.testforge.repository.recipe.RecipeVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 레시피 CRUD/검증 API 통합 테스트 (H2).
 * 생성/검증(VALID/INVALID), 순환 참조(400), 목록 필터, 상세 404,
 * 수정 시 버전 스냅샷 + CURRENT_VERSION 증가, 소프트 삭제를 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RecipeIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private RecipeVersionRepository versionRepository;

    @Autowired
    private ApiSpecRepository specRepository;

    @Autowired
    private ApiEndpointRepository endpointRepository;

    private MockMvc mockMvc;
    private Long specId;
    private Long activeEndpointId;
    private Long deprecatedEndpointId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        versionRepository.deleteAll();
        recipeRepository.deleteAll();
        endpointRepository.deleteAll();
        specRepository.deleteAll();

        ApiSpec spec = new ApiSpec("https://svc.example.com");
        spec.setName("svc");
        spec = specRepository.save(spec);
        specId = spec.getId();

        ApiEndpoint active = new ApiEndpoint(specId, "GET", "/api/v1/users");
        active.setStatus(EndpointStatus.ACTIVE);
        activeEndpointId = endpointRepository.save(active).getId();

        ApiEndpoint deprecated = new ApiEndpoint(specId, "POST", "/api/v1/legacy");
        deprecated.setStatus(EndpointStatus.DEPRECATED);
        deprecatedEndpointId = endpointRepository.save(deprecated).getId();
    }

    // ── create: 유효한 api 스텝 → VALID ──
    @Test
    void create_validApiStep_returnsValid() throws Exception {
        String body = createBody("주문조회", Visibility.PRIVATE,
                "[{\"name\":\"조회\",\"type\":\"api\",\"endpointId\":" + activeEndpointId + "}]");

        mockMvc.perform(post("/api/v1/recipes")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("주문조회"))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.validationStatus.code").value("VALID"))
                .andExpect(jsonPath("$.validationStatus.description").value("유효"))
                .andExpect(jsonPath("$.validationMessage").doesNotExist());
    }

    // ── create: 존재하지 않는 endpointId → INVALID (저장은 됨) ──
    @Test
    void create_missingEndpoint_returnsInvalidButStored() throws Exception {
        String body = createBody("깨진레시피", Visibility.PRIVATE,
                "[{\"name\":\"조회\",\"type\":\"api\",\"endpointId\":999999}]");

        mockMvc.perform(post("/api/v1/recipes")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validationStatus.code").value("INVALID"))
                .andExpect(jsonPath("$.validationMessage").value(
                        org.hamcrest.Matchers.containsString("missing endpointId")));

        assertThat(recipeRepository.findAll()).hasSize(1);
    }

    // ── create: DEPRECATED endpointId → INVALID (경고 보존) ──
    @Test
    void create_deprecatedEndpoint_returnsInvalid() throws Exception {
        String body = createBody("구버전참조", Visibility.PRIVATE,
                "[{\"name\":\"레거시\",\"type\":\"api\",\"endpointId\":" + deprecatedEndpointId + "}]");

        mockMvc.perform(post("/api/v1/recipes")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validationStatus.code").value("INVALID"))
                .andExpect(jsonPath("$.validationMessage").value(
                        org.hamcrest.Matchers.containsString("deprecated")));
    }

    // ── create: api 스텝 endpointId 누락 → 400 (필수 필드) ──
    @Test
    void create_apiStepMissingEndpointId_returns400() throws Exception {
        String body = createBody("필드누락", Visibility.PRIVATE,
                "[{\"name\":\"조회\",\"type\":\"api\"}]");

        mockMvc.perform(post("/api/v1/recipes")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_RECIPE"));
    }

    // ── create: 서브레시피 순환 참조 → 400 (저장 거부) ──
    @Test
    void create_subRecipeCycle_returns400() throws Exception {
        // A: 자기 참조 방식이 아닌, 기존 B가 A를 참조하도록 구성해 순환 유발
        // 1) A 생성 (스텝 없음)
        Recipe recipeA = new Recipe(1L, specId, "A");
        recipeA.setStepsJson("[]");
        recipeA.setValidationStatus(ValidationStatus.VALID);
        recipeA = recipeRepository.save(recipeA);
        Long idA = recipeA.getId();

        // 2) B 생성: A를 서브레시피로 참조
        Recipe recipeB = new Recipe(1L, specId, "B");
        recipeB.setStepsJson("[{\"name\":\"callA\",\"type\":\"recipe\",\"recipeId\":" + idA + "}]");
        recipeB.setValidationStatus(ValidationStatus.VALID);
        recipeB = recipeRepository.save(recipeB);
        Long idB = recipeB.getId();

        // 3) A 수정 시도: B를 참조 → A→B→A 순환 → 400
        String body = "{\"name\":\"A\",\"visibility\":\"PRIVATE\",\"steps\":"
                + "[{\"name\":\"callB\",\"type\":\"recipe\",\"recipeId\":" + idB + "}]}";

        mockMvc.perform(put("/api/v1/recipes/{id}", idA)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("RECIPE_CYCLE"));

        // 순환으로 거부되었으므로 A는 버전 스냅샷/갱신되지 않음 (version 1 유지, 스텝 비어있음)
        Recipe reloadedA = recipeRepository.findById(idA).orElseThrow();
        assertThat(reloadedA.getCurrentVersion()).isEqualTo(1);
        assertThat(versionRepository.findByRecipeIdOrderByVersionNoDesc(idA)).isEmpty();
    }

    // ── list: apiSpecId/visibility/keyword 필터 ──
    @Test
    void list_filtersBySpecVisibilityAndKeyword() throws Exception {
        save("결제레시피", specId, Visibility.COMMON, "결제 처리");
        save("회원가입", specId, Visibility.PRIVATE, "가입 흐름");
        // 다른 스펙 소속 레시피
        ApiSpec other = specRepository.save(new ApiSpec("https://other.example.com"));
        save("타서비스", other.getId(), Visibility.COMMON, "무관");

        // apiSpecId 필터 → 2건
        mockMvc.perform(get("/api/v1/recipes").param("apiSpecId", specId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // visibility 필터 → COMMON 1건
        mockMvc.perform(get("/api/v1/recipes")
                        .param("apiSpecId", specId.toString())
                        .param("visibility", "COMMON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("결제레시피"));

        // keyword 필터 (description LIKE) → "가입" 1건
        mockMvc.perform(get("/api/v1/recipes").param("keyword", "가입"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("회원가입"));
    }

    // ── detail: 없는 ID → 404 ──
    @Test
    void detail_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/recipes/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECIPE_NOT_FOUND"));
    }

    // ── update: 버전 스냅샷 + CURRENT_VERSION 증가 ──
    @Test
    void update_snapshotsAndIncrementsVersion() throws Exception {
        Recipe recipe = save("원본", specId, Visibility.PRIVATE, "설명");
        Long id = recipe.getId();

        String body = "{\"name\":\"수정본\",\"description\":\"바뀐설명\",\"visibility\":\"COMMON\",\"steps\":"
                + "[{\"name\":\"조회\",\"type\":\"api\",\"endpointId\":" + activeEndpointId + "}]}";

        mockMvc.perform(put("/api/v1/recipes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수정본"))
                .andExpect(jsonPath("$.currentVersion").value(2))
                .andExpect(jsonPath("$.validationStatus.code").value("VALID"));

        // 이전 상태(version 1)가 스냅샷으로 남아야 함
        assertThat(versionRepository.findByRecipeIdOrderByVersionNoDesc(id))
                .hasSize(1)
                .allSatisfy(v -> assertThat(v.getVersionNo()).isEqualTo(1));
    }

    // ── delete: 소프트 삭제 → 목록/상세에서 사라짐 ──
    @Test
    void delete_softDeletesRecipe() throws Exception {
        Recipe recipe = save("삭제대상", specId, Visibility.PRIVATE, "x");
        Long id = recipe.getId();

        mockMvc.perform(delete("/api/v1/recipes/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(recipeRepository.findById(id).orElseThrow().getDeletedAt()).isNotNull();

        mockMvc.perform(get("/api/v1/recipes/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ── helpers ──

    /** 생성 요청 바디 문자열 (ownerUserId/apiSpecId 고정) */
    private String createBody(String name, Visibility visibility, String stepsJsonArray) {
        return "{"
                + "\"ownerUserId\":1,"
                + "\"apiSpecId\":" + specId + ","
                + "\"name\":\"" + name + "\","
                + "\"visibility\":\"" + visibility.name() + "\","
                + "\"steps\":" + stepsJsonArray
                + "}";
    }

    /** 리포지토리 직접 저장 헬퍼 (목록/수정/삭제 테스트 픽스처) */
    private Recipe save(String name, Long apiSpecId, Visibility visibility, String description) {
        Recipe recipe = new Recipe(1L, apiSpecId, name);
        recipe.setDescription(description);
        recipe.setVisibility(visibility);
        recipe.setStepsJson("[]");
        recipe.setValidationStatus(ValidationStatus.VALID);
        return recipeRepository.save(recipe);
    }
}
