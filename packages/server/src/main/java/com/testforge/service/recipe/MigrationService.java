package com.testforge.service.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.testforge.dto.recipe.StepApiSpecIdMigrationResponse;
import com.testforge.dto.recipe.StepApiSpecIdMigrationResponse.FailedStep;
import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.recipe.RecipeVersion;
import com.testforge.entity.recipe.enums.ValidationStatus;
import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.repository.recipe.RecipeRepository;
import com.testforge.repository.recipe.RecipeVersionRepository;
import com.testforge.repository.spec.ApiEndpointRepository;
import com.testforge.utils.RecipeJsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * apiSpecId 역산 마이그레이션(일회성). 정본: docs/db/recipe.md "apiSpecId 역산 마이그레이션(일회성)".
 *
 * <p>배포 전 기존 데이터에 API 스텝의 {@code apiSpecId}를 채운다. 스텝에 {@code apiSpecId}가 없으면
 * {@code endpointId}로 {@link ApiEndpoint}의 소속 서비스를 역산해 주입한다. 역산 규칙은
 * {@code ExecutionService.resolveApiSpecId}와 동일하다(endpoint → apiSpecId).
 *
 * <p><b>보정 대상</b>: {@code RECIPE.STEPS_JSON} + {@code RECIPE_VERSION.SNAPSHOT_JSON} 내부 stepsJson.
 * 실행 히스토리({@code EXECUTION_RECIPE})는 "그때 그대로" 불변이라 <b>건드리지 않는다</b>.
 *
 * <p><b>멱등성</b>: 이미 {@code apiSpecId}가 있는 API 스텝은 skip → 재실행해도 안전.
 *
 * <p><b>역산 실패</b>: endpointId로 서비스를 역산 못 하는 스텝(endpoint 삭제 등)은 건너뛰고 로그+요약에
 * 남긴다(전체 실패 방지). 이런 스텝이 하나라도 있는 <b>RECIPE</b>는 {@code VALIDATION_STATUS=INVALID}로
 * 마킹한다(사유는 {@code VALIDATION_MESSAGE}). {@code RECIPE_VERSION}은 이력이라 상태 마킹 없이 백필만 한다.
 */
@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    /** 스텝 JSON에서 API 스텝을 식별하는 type 코드 (RecipeValidator와 동일) */
    private static final String TYPE_API = "api";

    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository recipeVersionRepository;
    private final ApiEndpointRepository endpointRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MigrationService(RecipeRepository recipeRepository,
                            RecipeVersionRepository recipeVersionRepository,
                            ApiEndpointRepository endpointRepository) {
        this.recipeRepository = recipeRepository;
        this.recipeVersionRepository = recipeVersionRepository;
        this.endpointRepository = endpointRepository;
    }

    /**
     * 모든 RECIPE(소프트삭제 포함)와 그 RECIPE_VERSION을 순회하며 API 스텝의 {@code apiSpecId}를 백필한다.
     * 실행 히스토리는 대상이 아니다. 결과 요약을 반환한다.
     */
    @Transactional
    public StepApiSpecIdMigrationResponse migrateStepApiSpecId() {
        List<Recipe> recipes = recipeRepository.findAll();
        List<FailedStep> failedSteps = new ArrayList<>();
        int patchedSteps = 0;

        for (Recipe recipe : recipes) {
            // 1) RECIPE 본문 stepsJson 백필
            PatchResult recipeResult = patchStepsJson(recipe.getStepsJson(), recipe.getId(), null);
            if (recipeResult.changed()) {
                recipe.setStepsJson(recipeResult.stepsJson());
            }
            patchedSteps += recipeResult.patchedCount();
            failedSteps.addAll(recipeResult.failed());

            // 역산 실패 스텝이 하나라도 있으면 이 레시피를 INVALID로 마킹 (이력=RECIPE_VERSION은 제외)
            if (!recipeResult.failed().isEmpty()) {
                recipe.setValidationStatus(ValidationStatus.INVALID);
                recipe.setValidationMessage(buildInvalidMessage(recipeResult.failed()));
            }

            // 2) 해당 레시피의 모든 RECIPE_VERSION 스냅샷 내부 stepsJson 백필 (상태 마킹 없음)
            List<RecipeVersion> versions =
                    recipeVersionRepository.findByRecipeIdOrderByVersionNoDesc(recipe.getId());
            for (RecipeVersion version : versions) {
                SnapshotPatchResult snapResult =
                        patchSnapshot(version.getSnapshotJson(), recipe.getId(), version.getVersionNo());
                if (snapResult.changed()) {
                    // 기존 엔티티의 스냅샷만 교체(dirty checking으로 UPDATE). versionNo/createdAt(updatable=false)은 보존.
                    version.setSnapshotJson(snapResult.snapshotJson());
                    recipeVersionRepository.save(version);
                }
                patchedSteps += snapResult.patchedCount();
                failedSteps.addAll(snapResult.failed());
            }
        }

        log.info("Migration step-api-spec-id done: processedRecipes={}, patchedSteps={}, failedSteps={}",
                recipes.size(), patchedSteps, failedSteps.size());
        return new StepApiSpecIdMigrationResponse(recipes.size(), patchedSteps, failedSteps);
    }

    /**
     * stepsJson 문자열의 API 스텝을 백필한다. {@code apiSpecId} 없는 API 스텝만 endpointId로 역산 주입한다.
     * 이미 값이 있으면 skip(멱등). 역산 실패 스텝은 failed에 기록하고 건너뛴다.
     *
     * @param versionNo RECIPE_VERSION 소속이면 버전 번호, RECIPE 본문이면 null (실패 기록용)
     */
    private PatchResult patchStepsJson(String stepsJson, Long recipeId, Integer versionNo) {
        List<Map<String, Object>> steps;
        try {
            steps = RecipeJsonUtil.parseSteps(stepsJson);
        } catch (IllegalArgumentException e) {
            // 파싱 불가한 stepsJson은 손대지 않는다(원본 보존).
            log.warn("Skip unparsable stepsJson (recipeId={}, versionNo={})", recipeId, versionNo);
            return PatchResult.unchanged();
        }
        if (steps.isEmpty()) {
            return PatchResult.unchanged();
        }

        List<FailedStep> failed = new ArrayList<>();
        int patched = 0;
        boolean changed = false;

        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            if (!TYPE_API.equals(asString(step.get("type")))) {
                continue;
            }
            if (asLong(step.get("apiSpecId")) != null) {
                continue; // 이미 있음 → skip (멱등)
            }
            Long endpointId = asLong(step.get("endpointId"));
            Long resolved = resolveApiSpecId(endpointId);
            if (resolved == null) {
                // 역산 실패: 건너뛰고 기록
                failed.add(new FailedStep(recipeId, i, endpointId, versionNo));
                log.warn("Cannot resolve apiSpecId (recipeId={}, versionNo={}, stepIndex={}, endpointId={})",
                        recipeId, versionNo, i, endpointId);
                continue;
            }
            step.put("apiSpecId", resolved);
            patched++;
            changed = true;
        }

        if (!changed) {
            return new PatchResult(false, stepsJson, patched, failed);
        }
        return new PatchResult(true, RecipeJsonUtil.toJsonString(steps), patched, failed);
    }

    /**
     * RECIPE_VERSION.SNAPSHOT_JSON을 백필한다. 스냅샷은 {@code { ..., "stepsJson": "<문자열>" }} 구조
     * (stepsJson 필드 안에 스텝 배열이 문자열로 들어있음 — 이중 인코딩). 그 내부 stepsJson만 보정하고
     * 나머지 필드(name/tags/variablesJson 등)는 그대로 보존한다.
     */
    private SnapshotPatchResult patchSnapshot(String snapshotJson, Long recipeId, Integer versionNo) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return SnapshotPatchResult.unchanged();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(snapshotJson);
        } catch (Exception e) {
            log.warn("Skip unparsable snapshotJson (recipeId={}, versionNo={})", recipeId, versionNo);
            return SnapshotPatchResult.unchanged();
        }
        if (!root.isObject()) {
            return SnapshotPatchResult.unchanged();
        }
        JsonNode stepsNode = root.get("stepsJson");
        // stepsJson은 문자열 필드(내부에 스텝 배열 문자열). 텍스트가 아니면 손대지 않는다.
        String innerStepsJson = (stepsNode != null && stepsNode.isTextual()) ? stepsNode.asText() : null;

        PatchResult inner = patchStepsJson(innerStepsJson, recipeId, versionNo);
        if (!inner.changed()) {
            // 백필 없음(멱등) 이거나 역산 실패만 있는 경우: 스냅샷 자체는 변경하지 않되 실패는 보고
            return new SnapshotPatchResult(false, snapshotJson, inner.patchedCount(), inner.failed());
        }

        // 보정된 stepsJson 문자열만 교체, 다른 필드 보존
        ObjectNode obj = (ObjectNode) root;
        obj.put("stepsJson", inner.stepsJson());
        String newSnapshot;
        try {
            newSnapshot = objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to re-serialize snapshotJson, skip (recipeId={}, versionNo={})",
                    recipeId, versionNo);
            return new SnapshotPatchResult(false, snapshotJson, 0, inner.failed());
        }
        return new SnapshotPatchResult(true, newSnapshot, inner.patchedCount(), inner.failed());
    }

    /**
     * endpointId로 소속 서비스(apiSpecId)를 역산한다. ExecutionService.resolveApiSpecId와 동일 규칙:
     * endpointId가 null이거나 endpoint를 못 찾으면 null.
     */
    private Long resolveApiSpecId(Long endpointId) {
        if (endpointId == null) {
            return null;
        }
        Optional<ApiEndpoint> endpoint = endpointRepository.findById(endpointId);
        return endpoint.map(ApiEndpoint::getApiSpecId).orElse(null);
    }

    /** 역산 실패 스텝들을 VALIDATION_MESSAGE(최대 1000자)에 담을 사유 문자열로 요약한다. */
    private String buildInvalidMessage(List<FailedStep> failed) {
        List<String> parts = new ArrayList<>();
        for (FailedStep f : failed) {
            parts.add("step[" + f.stepIndex() + "] endpointId=" + f.endpointId());
        }
        String message = "apiSpecId migration failed to resolve service for: " + String.join(", ", parts);
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** stepsJson 백필 결과 (RECIPE 본문/스냅샷 내부 공용) */
    private record PatchResult(boolean changed, String stepsJson, int patchedCount, List<FailedStep> failed) {
        static PatchResult unchanged() {
            return new PatchResult(false, null, 0, List.of());
        }
    }

    /** 스냅샷 백필 결과 */
    private record SnapshotPatchResult(boolean changed, String snapshotJson, int patchedCount,
                                       List<FailedStep> failed) {
        static SnapshotPatchResult unchanged() {
            return new SnapshotPatchResult(false, null, 0, List.of());
        }
    }
}
