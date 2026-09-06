package com.testforge;

import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.enums.EndpointStatus;
import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.recipe.enums.ValidationStatus;
import com.testforge.entity.recipe.enums.Visibility;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.repository.spec.ApiEndpointRepository;
import com.testforge.repository.spec.ApiSpecRepository;
import com.testforge.repository.recipe.RecipeRepository;
import com.testforge.repository.recipe.RecipeVersionRepository;
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
@Import(TestAuthSupport.class)
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

    @Autowired
    private TestAuthSupport testAuth;

    private MockMvc mockMvc;
    private Long specId;
    private Long activeEndpointId;
    private Long deprecatedEndpointId;
    private static final long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        versionRepository.deleteAll();
        recipeRepository.deleteAll();
        endpointRepository.deleteAll();
        specRepository.deleteAll();
        testAuth.ensureUser(USER_ID, UserRole.USER);

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

        mockMvc.perform(post("/api/v1/recipes").with(testAuth.as(USER_ID))
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

        mockMvc.perform(post("/api/v1/recipes").with(testAuth.as(USER_ID))
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

        mockMvc.perform(post("/api/v1/recipes").with(testAuth.as(USER_ID))
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

        mockMvc.perform(post("/api/v1/recipes").with(testAuth.as(USER_ID))
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

        mockMvc.perform(put("/api/v1/recipes/{id}", idA).with(testAuth.as(USER_ID))
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
        mockMvc.perform(get("/api/v1/recipes").with(testAuth.as(USER_ID)).param("apiSpecId", specId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // visibility 필터 → COMMON 1건
        mockMvc.perform(get("/api/v1/recipes").with(testAuth.as(USER_ID))
                        .param("apiSpecId", specId.toString())
                        .param("visibility", "COMMON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("결제레시피"));

        // keyword 필터 (description LIKE) → "가입" 1건
        mockMvc.perform(get("/api/v1/recipes").with(testAuth.as(USER_ID)).param("keyword", "가입"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("회원가입"));
    }

    // ── detail: 없는 ID → 404 ──
    @Test
    void detail_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/recipes/{id}", 999999L).with(testAuth.as(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECIPE_NOT_FOUND"));
    }

    // ── update: 버전 스냅샷 + CURRENT_VERSION 증가 ──
    // 공개범위를 COMMON으로 전환하므로 ADMIN 권한이 필요하다(auth.md: 공통 설정은 ADMIN만).
    @Test
    void update_snapshotsAndIncrementsVersion() throws Exception {
        // 이 테스트는 COMMON 전환을 검증하므로 요청자를 ADMIN으로 승격한다(버전 스냅샷/증가 검증이 본질).
        testAuth.ensureUser(USER_ID, UserRole.ADMIN);
        Recipe recipe = save("원본", specId, Visibility.PRIVATE, "설명");
        Long id = recipe.getId();

        String body = "{\"name\":\"수정본\",\"description\":\"바뀐설명\",\"visibility\":\"COMMON\",\"steps\":"
                + "[{\"name\":\"조회\",\"type\":\"api\",\"endpointId\":" + activeEndpointId + "}]}";

        mockMvc.perform(put("/api/v1/recipes/{id}", id).with(testAuth.as(USER_ID, UserRole.ADMIN))
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

        mockMvc.perform(delete("/api/v1/recipes/{id}", id).with(testAuth.as(USER_ID)))
                .andExpect(status().isNoContent());

        assertThat(recipeRepository.findById(id).orElseThrow().getDeletedAt()).isNotNull();

        mockMvc.perform(get("/api/v1/recipes/{id}", id).with(testAuth.as(USER_ID)))
                .andExpect(status().isNotFound());
    }

    // ── list: 소유 격리 (남의 PRIVATE 미노출) ──
    @Test
    void list_excludesOthersPrivateRecipes() throws Exception {
        // 내 PRIVATE + 공통 + 남의 PRIVATE(owner=2)
        save("내개인", specId, Visibility.PRIVATE, "mine");
        save("공통", specId, Visibility.COMMON, "shared");
        Recipe others = new Recipe(2L, specId, "남의개인");
        others.setVisibility(Visibility.PRIVATE);
        others.setStepsJson("[]");
        others.setValidationStatus(ValidationStatus.VALID);
        recipeRepository.save(others);

        // USER_ID=1로 조회 → 내 PRIVATE + 공통 = 2건 (남의 PRIVATE 제외)
        mockMvc.perform(get("/api/v1/recipes").with(testAuth.as(USER_ID)).param("apiSpecId", specId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ── update: 공통 레시피를 non-admin이 수정 시도 → 403 ──
    @Test
    void update_commonRecipeByNonAdmin_returns403() throws Exception {
        Recipe common = save("공통레시피", specId, Visibility.COMMON, "shared");
        Long id = common.getId();

        String body = "{\"name\":\"수정시도\",\"visibility\":\"COMMON\",\"steps\":[]}";
        mockMvc.perform(put("/api/v1/recipes/{id}", id).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    // ── detail: 남의 PRIVATE 접근 → 404 (존재 은폐) ──
    @Test
    void detail_othersPrivate_returns404() throws Exception {
        Recipe others = new Recipe(2L, specId, "남의개인");
        others.setVisibility(Visibility.PRIVATE);
        others.setStepsJson("[]");
        others.setValidationStatus(ValidationStatus.VALID);
        Long id = recipeRepository.save(others).getId();

        mockMvc.perform(get("/api/v1/recipes/{id}", id).with(testAuth.as(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECIPE_NOT_FOUND"));
    }

    // ── duplicate: 공통 → 개인 사본 (PRIVATE, 요청자 소유) ──
    @Test
    void duplicate_commonRecipe_createsPrivateCopy() throws Exception {
        Recipe common = save("원본공통", specId, Visibility.COMMON, "shared");
        common.setOwnerUserId(2L); // 남이 만든 공통
        recipeRepository.save(common);
        Long id = common.getId();

        mockMvc.perform(post("/api/v1/recipes/{id}/duplicate", id).with(testAuth.as(USER_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("원본공통 (사본)"))
                .andExpect(jsonPath("$.visibility.code").value("PRIVATE"))
                .andExpect(jsonPath("$.ownerUserId").value((int) USER_ID))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    // ── versions + restore: 목록/특정버전/복원 라운드트립 ──
    @Test
    void versions_listDetailAndRestore_roundTrip() throws Exception {
        Recipe recipe = save("버전대상", specId, Visibility.PRIVATE, "v1설명");
        Long id = recipe.getId();

        // v1 → v2 수정 (스냅샷 v1 생성)
        String update1 = "{\"name\":\"버전대상\",\"description\":\"v2설명\",\"visibility\":\"PRIVATE\",\"steps\":"
                + "[{\"name\":\"조회\",\"type\":\"api\",\"endpointId\":" + activeEndpointId + "}]}";
        mockMvc.perform(put("/api/v1/recipes/{id}", id).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(update1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(2));

        // 버전 목록 → v1 1건
        mockMvc.perform(get("/api/v1/recipes/{id}/versions", id).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].versionNo").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        // 특정 버전(v1) 상세 → 스냅샷 펼침 (description=v1설명, canEdit=true)
        mockMvc.perform(get("/api/v1/recipes/{id}/versions/{v}", id, 1).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.description").value("v1설명"))
                .andExpect(jsonPath("$.canEdit").value(true));

        // v1로 복원 → 새 버전 v3, description이 v1설명으로 되돌아감
        mockMvc.perform(post("/api/v1/recipes/{id}/versions/{v}/restore", id, 1).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(3))
                .andExpect(jsonPath("$.description").value("v1설명"));

        // 복원도 이력에 남음 → 이제 v1, v2 스냅샷 2건
        assertThat(versionRepository.findByRecipeIdOrderByVersionNoDesc(id)).hasSize(2);
    }

    // ── restore: 공통 레시피를 non-admin이 복원 시도 → 403 ──
    @Test
    void restore_commonByNonAdmin_returns403() throws Exception {
        // ADMIN으로 공통 레시피를 만들고 1회 수정하여 v1 스냅샷 생성
        testAuth.ensureUser(2L, UserRole.ADMIN);
        Recipe common = new Recipe(2L, specId, "공통버전");
        common.setVisibility(Visibility.COMMON);
        common.setStepsJson("[]");
        common.setValidationStatus(ValidationStatus.VALID);
        Long id = recipeRepository.save(common).getId();
        String update = "{\"name\":\"공통버전\",\"visibility\":\"COMMON\",\"steps\":[]}";
        mockMvc.perform(put("/api/v1/recipes/{id}", id).with(testAuth.as(2L, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk());

        // 일반 사용자(USER_ID)가 복원 시도 → 403
        mockMvc.perform(post("/api/v1/recipes/{id}/versions/{v}/restore", id, 1).with(testAuth.as(USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
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

    /** 소유자를 지정해 저장하는 헬퍼 (R1 소유 격리 테스트용) */
    private Recipe saveOwnedBy(Long ownerUserId, String name, Long apiSpecId, Visibility visibility) {
        Recipe recipe = new Recipe(ownerUserId, apiSpecId, name);
        recipe.setVisibility(visibility);
        recipe.setStepsJson("[]");
        recipe.setValidationStatus(ValidationStatus.VALID);
        return recipeRepository.save(recipe);
    }

    // ── R1 AI 후보 소유 격리: findVisibleByApiSpecId는 "COMMON + 본인 PRIVATE"만, 남의 PRIVATE는 제외 ──
    @Test
    void findVisibleByApiSpecId_excludesOthersPrivate() {
        Long actor = USER_ID; // 요청자(대화방 소유자)
        saveOwnedBy(actor, "내 개인", specId, Visibility.PRIVATE);
        saveOwnedBy(actor, "공통", specId, Visibility.COMMON);
        saveOwnedBy(2L, "남의 개인", specId, Visibility.PRIVATE); // owner=2 (남)

        var visible = recipeRepository.findVisibleByApiSpecId(specId, actor);

        // 남의 PRIVATE는 후보에서 빠지고, 내 PRIVATE + COMMON만 로드된다
        assertThat(visible).extracting(Recipe::getName)
                .containsExactlyInAnyOrder("내 개인", "공통");
    }
}
