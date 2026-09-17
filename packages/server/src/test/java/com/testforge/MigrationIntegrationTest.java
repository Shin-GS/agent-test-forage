package com.testforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.testforge.dto.recipe.StepApiSpecIdMigrationResponse;
import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.recipe.RecipeVersion;
import com.testforge.entity.recipe.enums.ValidationStatus;
import com.testforge.entity.recipe.enums.Visibility;
import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.enums.EndpointStatus;
import com.testforge.repository.recipe.RecipeRepository;
import com.testforge.repository.recipe.RecipeVersionRepository;
import com.testforge.repository.spec.ApiEndpointRepository;
import com.testforge.repository.spec.ApiSpecRepository;
import com.testforge.service.recipe.MigrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * apiSpecId 역산 마이그레이션(MigrationService) 통합 테스트 (H2).
 *
 * <p>마이그레이션은 DB 데이터를 변경하는 일회성 작업이라, 실행 전 다음을 실증한다:
 * <ul>
 *   <li>RECIPE.STEPS_JSON: apiSpecId 없는 API 스텝을 endpointId로 역산 백필</li>
 *   <li>RECIPE_VERSION.SNAPSHOT_JSON: 내부 stepsJson(이중 인코딩 텍스트 필드)만 백필, 다른 필드 보존</li>
 *   <li>멱등성: 이미 apiSpecId 있는 스텝 skip, 재실행 안전</li>
 *   <li>역산 실패: 해당 RECIPE만 INVALID 마킹, RECIPE_VERSION은 상태 마킹 없음</li>
 *   <li>비-API 스텝(script/recipe 등)은 건드리지 않음</li>
 * </ul>
 *
 * <p>스냅샷은 RecipeService.snapshotOf와 동일하게 {@code {"stepsJson":"<배열 문자열>", ...}}
 * 이중 인코딩 구조로 만들어, 실제 저장 경로를 재현한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class MigrationIntegrationTest {

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private RecipeVersionRepository versionRepository;

    @Autowired
    private ApiSpecRepository specRepository;

    @Autowired
    private ApiEndpointRepository endpointRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    private Long specA;
    private Long specB;
    private Long endpointA; // specA 소속
    private Long endpointB; // specB 소속

    @BeforeEach
    void setUp() {
        versionRepository.deleteAll();
        recipeRepository.deleteAll();
        endpointRepository.deleteAll();
        specRepository.deleteAll();

        ApiSpec a = new ApiSpec("https://a.example.com");
        a.setName("svc-a");
        specA = specRepository.save(a).getId();

        ApiSpec b = new ApiSpec("https://b.example.com");
        b.setName("svc-b");
        specB = specRepository.save(b).getId();

        ApiEndpoint ea = new ApiEndpoint(specA, "POST", "/api/v1/orders");
        ea.setStatus(EndpointStatus.ACTIVE);
        endpointA = endpointRepository.save(ea).getId();

        ApiEndpoint eb = new ApiEndpoint(specB, "POST", "/api/v1/bookings");
        eb.setStatus(EndpointStatus.ACTIVE);
        endpointB = endpointRepository.save(eb).getId();
    }

    // ── RECIPE.STEPS_JSON: apiSpecId 없는 API 스텝 백필 (endpointId 소속 스펙으로 역산) ──
    @Test
    void migrate_backfillsRecipeStepsJson() {
        // 스텝0: apiSpecId 없음(endpointA→specA로 역산되어야), 스텝1: 이미 있음(skip)
        String steps = "[{\"type\":\"api\",\"endpointId\":" + endpointA + "},"
                + "{\"type\":\"api\",\"apiSpecId\":" + specB + ",\"endpointId\":" + endpointB + "}]";
        Recipe r = newRecipe("멀티", specA, steps);
        Long id = recipeRepository.save(r).getId();

        StepApiSpecIdMigrationResponse res = migrationService.migrateStepApiSpecId();

        assertThat(res.patchedSteps()).isEqualTo(1);
        assertThat(res.failedSteps()).isEmpty();

        List<?> saved = parseSteps(recipeRepository.findById(id).orElseThrow().getStepsJson());
        assertThat(stepApiSpecId(saved, 0)).isEqualTo(specA); // 역산 주입
        assertThat(stepApiSpecId(saved, 1)).isEqualTo(specB); // 기존 값 보존
    }

    // ── RECIPE_VERSION.SNAPSHOT_JSON: 내부 stepsJson(이중 인코딩)만 백필, 다른 필드 보존 ──
    @Test
    void migrate_backfillsSnapshotInnerStepsJson_preservingOtherFields() {
        Recipe r = newRecipe("스냅샷대상", specA, "[]");
        Long id = recipeRepository.save(r).getId();

        // snapshotOf와 동일 구조: stepsJson은 "배열 문자열"(이중 인코딩) 텍스트 필드
        String innerSteps = "[{\"type\":\"api\",\"endpointId\":" + endpointA + "}]";
        ObjectNode snap = mapper.createObjectNode();
        snap.put("name", "스냅샷대상");
        snap.put("visibility", "PRIVATE");
        snap.put("stepsJson", innerSteps); // 텍스트 필드로 넣음
        snap.put("versionNo", 1);
        versionRepository.save(new RecipeVersion(id, 1, snap.toString()));

        StepApiSpecIdMigrationResponse res = migrationService.migrateStepApiSpecId();
        assertThat(res.failedSteps()).isEmpty();
        assertThat(res.patchedSteps()).isEqualTo(1); // 스냅샷 내부 스텝 1건

        RecipeVersion reloaded = versionRepository.findByRecipeIdOrderByVersionNoDesc(id).get(0);
        JsonNode root = readTree(reloaded.getSnapshotJson());
        // 다른 필드 보존
        assertThat(root.get("name").asText()).isEqualTo("스냅샷대상");
        assertThat(root.get("versionNo").asInt()).isEqualTo(1);
        // stepsJson은 여전히 텍스트 필드(이중 인코딩 유지)
        assertThat(root.get("stepsJson").isTextual()).isTrue();
        // 내부 stepsJson에 apiSpecId 백필됨
        List<?> innerParsed = parseSteps(root.get("stepsJson").asText());
        assertThat(stepApiSpecId(innerParsed, 0)).isEqualTo(specA);
    }

    // ── 멱등성: 재실행 시 추가 변경 없음 ──
    @Test
    void migrate_isIdempotent() {
        String steps = "[{\"type\":\"api\",\"endpointId\":" + endpointA + "}]";
        Recipe r = newRecipe("멱등", specA, steps);
        recipeRepository.save(r);

        StepApiSpecIdMigrationResponse first = migrationService.migrateStepApiSpecId();
        assertThat(first.patchedSteps()).isEqualTo(1);

        StepApiSpecIdMigrationResponse second = migrationService.migrateStepApiSpecId();
        assertThat(second.patchedSteps()).isZero(); // 이미 채워져 skip
        assertThat(second.failedSteps()).isEmpty();
    }

    // ── 역산 실패: RECIPE만 INVALID 마킹, RECIPE_VERSION은 상태 마킹 없음 ──
    @Test
    void migrate_unresolvableEndpoint_marksRecipeInvalidOnly() {
        // 존재하지 않는 endpointId → 역산 실패
        String steps = "[{\"type\":\"api\",\"endpointId\":999999}]";
        Recipe r = newRecipe("깨진참조", specA, steps);
        r.setValidationStatus(ValidationStatus.VALID);
        Long id = recipeRepository.save(r).getId();

        // 스냅샷에도 같은 깨진 참조
        ObjectNode snap = mapper.createObjectNode();
        snap.put("name", "깨진참조");
        snap.put("stepsJson", steps);
        snap.put("versionNo", 1);
        versionRepository.save(new RecipeVersion(id, 1, snap.toString()));

        StepApiSpecIdMigrationResponse res = migrationService.migrateStepApiSpecId();

        // RECIPE 본문 실패 1건 + 스냅샷 실패 1건 = 2건
        assertThat(res.failedSteps()).hasSize(2);
        assertThat(res.patchedSteps()).isZero();

        Recipe reloaded = recipeRepository.findById(id).orElseThrow();
        assertThat(reloaded.getValidationStatus()).isEqualTo(ValidationStatus.INVALID);
        assertThat(reloaded.getValidationMessage()).contains("endpointId=999999");
    }

    // ── 비-API 스텝은 건드리지 않음 ──
    @Test
    void migrate_ignoresNonApiSteps() {
        String steps = "[{\"type\":\"script\",\"code\":\"return 1;\"},"
                + "{\"type\":\"recipe\",\"recipeId\":123}]";
        Recipe r = newRecipe("비API", specA, steps);
        Long id = recipeRepository.save(r).getId();

        StepApiSpecIdMigrationResponse res = migrationService.migrateStepApiSpecId();

        assertThat(res.patchedSteps()).isZero();
        assertThat(res.failedSteps()).isEmpty();
        List<?> saved = parseSteps(recipeRepository.findById(id).orElseThrow().getStepsJson());
        assertThat(stepApiSpecId(saved, 0)).isNull(); // script 스텝엔 apiSpecId 없음
    }

    // ── helpers ──

    private Recipe newRecipe(String name, Long apiSpecId, String stepsJson) {
        Recipe r = new Recipe(1L, apiSpecId, name);
        r.setVisibility(Visibility.PRIVATE);
        r.setStepsJson(stepsJson);
        r.setValidationStatus(ValidationStatus.VALID);
        return r;
    }

    private List<?> parseSteps(String json) {
        try {
            return mapper.readValue(json, List.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Long stepApiSpecId(List<?> steps, int index) {
        Object step = steps.get(index);
        Object v = ((java.util.Map<String, Object>) step).get("apiSpecId");
        if (v == null) {
            return null;
        }
        return ((Number) v).longValue();
    }
}
