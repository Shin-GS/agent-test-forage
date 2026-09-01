package com.testforge.service.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.testforge.common.error.ApiException;
import com.testforge.dto.recipe.RecipeCreateRequest;
import com.testforge.dto.recipe.RecipeDetailResponse;
import com.testforge.dto.recipe.RecipeSummaryResponse;
import com.testforge.dto.recipe.RecipeUpdateRequest;
import com.testforge.dto.common.StatusView;
import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.recipe.RecipeVersion;
import com.testforge.entity.recipe.enums.Visibility;
import com.testforge.repository.recipe.RecipeRepository;
import com.testforge.repository.recipe.RecipeVersionRepository;
import com.testforge.utils.RecipeJsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 레시피 CRUD + 검증 + 버전 스냅샷 로직. (실행 엔진은 이번 스코프 아님 — 정의/관리만)
 * 스텝/변수/결과 JSON은 문자열로 저장하고, 상세 응답에서 다시 노드로 파싱해 내린다.
 */
@Service
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);

    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;
    private final RecipeValidator validator;

    // 버전 스냅샷 직렬화용 로컬 매퍼 (SpecQueryService와 동일하게 공용 빈에 의존하지 않음)
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecipeService(RecipeRepository recipeRepository,
                         RecipeVersionRepository versionRepository,
                         RecipeValidator validator) {
        this.recipeRepository = recipeRepository;
        this.versionRepository = versionRepository;
        this.validator = validator;
    }

    /**
     * 레시피 생성 (v1). 생성 시 검증을 수행하여 VALIDATION_STATUS를 반영한다.
     * 순환 참조가 있으면 저장 전에 400으로 거부된다.
     */
    @Transactional
    public RecipeDetailResponse create(RecipeCreateRequest request) {
        validateRequiredMeta(request.ownerUserId(), request.apiSpecId(), request.name());

        Recipe recipe = new Recipe(request.ownerUserId(), request.apiSpecId(), request.name());
        recipe.setDescription(request.description());
        recipe.setVisibility(request.visibility() == null ? Visibility.PRIVATE : request.visibility());
        recipe.setTags(RecipeJsonUtil.toJsonString(request.tags()));
        recipe.setVariablesJson(RecipeJsonUtil.toJsonString(request.variables()));
        recipe.setStepsJson(RecipeJsonUtil.toJsonString(request.steps()));
        recipe.setResultDefinitionJson(RecipeJsonUtil.toJsonString(request.resultDefinition()));
        recipe.setResultTemplate(request.resultTemplate());
        recipe.setCurrentVersion(1);

        // 검증: 순환이면 여기서 400 → 저장되지 않음
        RecipeValidationResult result = validator.validate(null, recipe.getApiSpecId(), recipe.getStepsJson());
        recipe.setValidationStatus(result.status());
        recipe.setValidationMessage(result.message());

        Recipe saved = recipeRepository.save(recipe);
        log.info("Recipe created: recipeId={}, validation={}", saved.getId(), result.status());
        return toDetail(saved);
    }

    /** 미삭제 레시피 상세. 없거나 삭제된 경우 404. */
    @Transactional(readOnly = true)
    public RecipeDetailResponse detail(Long id) {
        Recipe recipe = recipeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.recipeNotFound(id));
        return toDetail(recipe);
    }

    /**
     * 필터 목록 조회 (미삭제만, name 오름차순).
     * 각 필터는 null이면 무시된다. keyword는 name/description LIKE.
     */
    @Transactional(readOnly = true)
    public List<RecipeSummaryResponse> list(Long apiSpecId, Visibility visibility,
                                            Long ownerUserId, String keyword) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        List<Recipe> recipes = recipeRepository.search(apiSpecId, visibility, ownerUserId, normalizedKeyword);
        return recipes.stream().map(this::toSummary).toList();
    }

    /**
     * 레시피 수정. 저장 직전 상태를 RecipeVersion에 VERSION_NO=현재값으로 스냅샷한 뒤
     * Recipe를 갱신하고 CURRENT_VERSION을 +1한 후 재검증한다.
     * 순환 참조가 있으면 스냅샷/갱신 전에 400으로 거부된다.
     */
    @Transactional
    public RecipeDetailResponse update(Long id, RecipeUpdateRequest request) {
        Recipe recipe = recipeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.recipeNotFound(id));

        if (request.name() == null || request.name().isBlank()) {
            throw ApiException.invalidRequest("name is required");
        }

        // 새 스텝 JSON을 먼저 검증 (순환이면 여기서 400 → 스냅샷/갱신 안 함)
        String newStepsJson = RecipeJsonUtil.toJsonString(request.steps());
        RecipeValidationResult result = validator.validate(id, recipe.getApiSpecId(), newStepsJson);

        // 저장 직전 상태를 스냅샷 (VERSION_NO = 현재 버전)
        String snapshot = snapshotOf(recipe);
        versionRepository.save(new RecipeVersion(recipe.getId(), recipe.getCurrentVersion(), snapshot));

        // Recipe 갱신
        recipe.setName(request.name());
        recipe.setDescription(request.description());
        recipe.setVisibility(request.visibility() == null ? Visibility.PRIVATE : request.visibility());
        recipe.setTags(RecipeJsonUtil.toJsonString(request.tags()));
        recipe.setVariablesJson(RecipeJsonUtil.toJsonString(request.variables()));
        recipe.setStepsJson(newStepsJson);
        recipe.setResultDefinitionJson(RecipeJsonUtil.toJsonString(request.resultDefinition()));
        recipe.setResultTemplate(request.resultTemplate());
        recipe.setCurrentVersion(recipe.getCurrentVersion() + 1);
        recipe.setValidationStatus(result.status());
        recipe.setValidationMessage(result.message());

        Recipe saved = recipeRepository.save(recipe);
        log.info("Recipe updated: recipeId={}, newVersion={}, validation={}",
                saved.getId(), saved.getCurrentVersion(), result.status());
        return toDetail(saved);
    }

    /** 소프트 삭제 (DELETED_AT = now). 참조하는 레시피는 검증에서 경고된다. */
    @Transactional
    public void softDelete(Long id) {
        Recipe recipe = recipeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.recipeNotFound(id));
        recipe.setDeletedAt(LocalDateTime.now());
        recipeRepository.save(recipe);
        log.info("Recipe soft-deleted: recipeId={}", id);
    }

    // ── helpers ──

    /** 생성 시 필수 메타(작성자/스펙/이름) 검증 */
    private void validateRequiredMeta(Long ownerUserId, Long apiSpecId, String name) {
        if (ownerUserId == null) {
            throw ApiException.invalidRequest("ownerUserId is required");
        }
        if (apiSpecId == null) {
            throw ApiException.invalidRequest("apiSpecId is required");
        }
        if (name == null || name.isBlank()) {
            throw ApiException.invalidRequest("name is required");
        }
    }

    /** 수정 이력 복원용 전체 스냅샷 JSON 생성 (메타+스텝+변수+결과) */
    private String snapshotOf(Recipe recipe) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", recipe.getName());
        node.put("description", recipe.getDescription());
        node.put("visibility", recipe.getVisibility().name());
        node.put("tags", recipe.getTags());
        node.put("variablesJson", recipe.getVariablesJson());
        node.put("stepsJson", recipe.getStepsJson());
        node.put("resultDefinitionJson", recipe.getResultDefinitionJson());
        node.put("resultTemplate", recipe.getResultTemplate());
        node.put("versionNo", recipe.getCurrentVersion());
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize recipe snapshot", e);
        }
    }

    /** 목록 행 매핑 */
    private RecipeSummaryResponse toSummary(Recipe recipe) {
        return new RecipeSummaryResponse(
                recipe.getId(),
                recipe.getName(),
                recipe.getDescription(),
                recipe.getApiSpecId(),
                StatusView.of(recipe.getVisibility()),
                RecipeJsonUtil.parseTags(recipe.getTags()),
                StatusView.of(recipe.getValidationStatus()),
                recipe.getCurrentVersion(),
                recipe.getUsageCount(),
                recipe.getLastUsedAt());
    }

    /** 상세 매핑 (JSON 문자열을 노드로 파싱해서 내림) */
    private RecipeDetailResponse toDetail(Recipe recipe) {
        Object variables = RecipeJsonUtil.toObject(recipe.getVariablesJson());
        Object steps = RecipeJsonUtil.toObject(recipe.getStepsJson());
        Object resultDefinition = RecipeJsonUtil.toObject(recipe.getResultDefinitionJson());

        return new RecipeDetailResponse(
                recipe.getId(),
                recipe.getOwnerUserId(),
                recipe.getApiSpecId(),
                recipe.getName(),
                recipe.getDescription(),
                StatusView.of(recipe.getVisibility()),
                RecipeJsonUtil.parseTags(recipe.getTags()),
                variables,
                steps,
                resultDefinition,
                recipe.getResultTemplate(),
                recipe.getCurrentVersion(),
                StatusView.of(recipe.getValidationStatus()),
                recipe.getValidationMessage(),
                recipe.getUsageCount(),
                recipe.getLastUsedAt(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt());
    }
}
