package com.testforge.service.recipe;

import com.testforge.common.error.ApiException;
import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.enums.EndpointStatus;
import com.testforge.entity.recipe.Recipe;
import com.testforge.repository.spec.ApiEndpointRepository;
import com.testforge.repository.recipe.RecipeRepository;
import com.testforge.utils.RecipeJsonUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 레시피 스텝 정의의 유효성을 검증한다.
 *
 * <p>검증 축:
 * <ul>
 *   <li>스텝 타입별 필수 필드 존재 (api=endpointId, recipe=recipeId, script=code, userInput=variables)</li>
 *   <li>type=api의 endpointId가 존재하고 ACTIVE인지 (없음/DEPRECATED면 INVALID + 메시지) — 경고성, 저장 허용</li>
 *   <li>type=recipe의 recipeId 존재 확인 + 서브레시피 순환 참조(A→B→A) 차단 — 순환이면 저장 거부(400)</li>
 * </ul>
 *
 * <p>순환 참조는 저장 자체를 막아야 하므로 {@link ApiException#recipeCycle}로 던지고,
 * 그 외 참조 깨짐은 저장을 허용하되 {@link RecipeValidationResult#invalid} 메시지로 남긴다.
 */
@Component
public class RecipeValidator {

    /** 스텝 타입 코드 (structure.md의 4종) */
    private static final String TYPE_API = "api";
    private static final String TYPE_SCRIPT = "script";
    private static final String TYPE_RECIPE = "recipe";
    private static final String TYPE_USER_INPUT = "userInput";

    private final ApiEndpointRepository endpointRepository;
    private final RecipeRepository recipeRepository;

    public RecipeValidator(ApiEndpointRepository endpointRepository,
                           RecipeRepository recipeRepository) {
        this.endpointRepository = endpointRepository;
        this.recipeRepository = recipeRepository;
    }

    /**
     * 스텝 JSON을 검증한다.
     *
     * @param selfRecipeId  수정 중인 레시피 ID (신규 생성이면 null). 순환 감지 시작 노드로 사용
     * @param apiSpecId     레시피 대상 스펙 ID
     * @param stepsJson     스텝 목록 JSON 문자열
     * @return VALID 또는 INVALID(메시지 포함) 결과
     * @throws ApiException 필수 필드 누락(400) 또는 순환 참조(400)
     */
    public RecipeValidationResult validate(Long selfRecipeId, Long apiSpecId, String stepsJson) {
        List<Map<String, Object>> steps;
        try {
            steps = RecipeJsonUtil.parseSteps(stepsJson);
        } catch (IllegalArgumentException e) {
            throw ApiException.invalidRecipe(e.getMessage());
        }

        // 참조 깨짐(경고성) 메시지를 모아 INVALID로 보고
        List<String> warnings = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            String type = asString(step.get("type"));
            if (type == null || type.isBlank()) {
                throw ApiException.invalidRecipe("Step[" + i + "] is missing 'type'");
            }

            switch (type) {
                case TYPE_API -> validateApiStep(i, step, warnings);
                case TYPE_SCRIPT -> requireField(i, step, "code", type);
                case TYPE_RECIPE -> validateRecipeStep(i, step, warnings);
                case TYPE_USER_INPUT -> requireField(i, step, "variables", type);
                default -> throw ApiException.invalidRecipe(
                        "Step[" + i + "] has unknown type: " + type);
            }
        }

        // 순환 참조 검사: 이 레시피가 서브레시피 그래프에서 자기 자신으로 돌아오는지 확인
        detectCycle(selfRecipeId, stepsJson);

        if (warnings.isEmpty()) {
            return RecipeValidationResult.valid();
        }
        return RecipeValidationResult.invalid(String.join("; ", warnings));
    }

    /** type=api: endpointId 필수 + 존재/ACTIVE 확인 (없음/DEPRECATED는 경고) */
    private void validateApiStep(int index, Map<String, Object> step, List<String> warnings) {
        Long endpointId = asLong(step.get("endpointId"));
        if (endpointId == null) {
            throw ApiException.invalidRecipe("Step[" + index + "] (api) is missing 'endpointId'");
        }
        ApiEndpoint endpoint = endpointRepository.findById(endpointId).orElse(null);
        if (endpoint == null) {
            warnings.add("Step[" + index + "] references missing endpointId=" + endpointId);
        } else if (endpoint.getStatus() == EndpointStatus.DEPRECATED) {
            warnings.add("Step[" + index + "] references deprecated endpointId=" + endpointId);
        }
    }

    /** type=recipe: recipeId 필수 + 존재 확인 (없으면 경고). 순환은 detectCycle에서 별도 처리 */
    private void validateRecipeStep(int index, Map<String, Object> step, List<String> warnings) {
        Long recipeId = asLong(step.get("recipeId"));
        if (recipeId == null) {
            throw ApiException.invalidRecipe("Step[" + index + "] (recipe) is missing 'recipeId'");
        }
        boolean exists = recipeRepository.findByIdAndDeletedAtIsNull(recipeId).isPresent();
        if (!exists) {
            warnings.add("Step[" + index + "] references missing recipeId=" + recipeId);
        }
    }

    /** 지정 필드가 비어있으면 400 */
    private void requireField(int index, Map<String, Object> step, String field, String type) {
        Object value = step.get(field);
        boolean blank = value == null || (value instanceof String s && s.isBlank());
        if (blank) {
            throw ApiException.invalidRecipe(
                    "Step[" + index + "] (" + type + ") is missing '" + field + "'");
        }
    }

    /**
     * 서브레시피 순환 참조 감지 (DFS + 방문셋).
     * 현재 레시피(selfRecipeId)의 스텝에서 시작해 서브레시피 그래프를 따라가며,
     * 자기 자신 또는 이미 방문 중인 노드로 다시 도달하면 순환으로 판단해 400을 던진다.
     * 신규 생성(selfRecipeId == null)은 아직 참조 대상이 될 수 없으므로 자기순환은 불가하지만,
     * 서브레시피들 간 기존 순환은 여전히 감지한다.
     */
    private void detectCycle(Long selfRecipeId, String stepsJson) {
        Set<Long> visiting = new HashSet<>();
        if (selfRecipeId != null) {
            visiting.add(selfRecipeId);
        }
        List<Long> children = subRecipeIds(stepsJson);
        for (Long child : children) {
            walk(child, selfRecipeId, visiting);
        }
    }

    /** child 레시피를 방문하며 순환 여부를 확인 (재귀 DFS) */
    private void walk(Long recipeId, Long rootId, Set<Long> visiting) {
        if (recipeId == null) {
            return;
        }
        if (visiting.contains(recipeId)) {
            throw ApiException.recipeCycle(
                    "Sub-recipe cycle detected involving recipeId=" + recipeId);
        }
        Recipe recipe = recipeRepository.findByIdAndDeletedAtIsNull(recipeId).orElse(null);
        if (recipe == null) {
            // 존재하지 않는 참조는 경고(위에서 처리)이지 순환이 아니므로 여기선 무시
            return;
        }
        visiting.add(recipeId);
        for (Long child : subRecipeIds(recipe.getStepsJson())) {
            walk(child, rootId, visiting);
        }
        visiting.remove(recipeId);
    }

    /** 스텝 JSON에서 type=recipe 스텝의 recipeId 목록을 추출 */
    private List<Long> subRecipeIds(String stepsJson) {
        List<Long> ids = new ArrayList<>();
        List<Map<String, Object>> steps;
        try {
            steps = RecipeJsonUtil.parseSteps(stepsJson);
        } catch (IllegalArgumentException e) {
            return ids;
        }
        for (Map<String, Object> step : steps) {
            if (TYPE_RECIPE.equals(asString(step.get("type")))) {
                Long id = asLong(step.get("recipeId"));
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /** Object를 문자열로 (null 허용) */
    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /** Object를 Long으로 (숫자/문자열 모두 허용, 실패 시 null) */
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
}
