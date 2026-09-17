package com.testforge.dto.recipe;

import java.util.List;

/**
 * apiSpecId 역산 마이그레이션(일회성) 결과 요약. 정본: docs/db/recipe.md apiSpecId 역산 마이그레이션.
 *
 * <p>레시피 계열(RECIPE.STEPS_JSON + RECIPE_VERSION.SNAPSHOT_JSON 내부 stepsJson)의 API 스텝에
 * {@code apiSpecId}가 없으면 {@code endpointId}로 역산해 주입한 결과를 담는다. 실행 히스토리
 * (EXECUTION_RECIPE)는 대상이 아니다.
 *
 * @param processedRecipes 순회한 RECIPE 개수(소프트삭제 포함, RECIPE_VERSION 순회 건수는 제외)
 * @param patchedSteps     apiSpecId를 새로 주입한 API 스텝 총 개수(RECIPE + RECIPE_VERSION 합산)
 * @param failedSteps      endpointId 역산에 실패해 건너뛴 스텝 목록
 */
public record StepApiSpecIdMigrationResponse(
        int processedRecipes,
        int patchedSteps,
        List<FailedStep> failedSteps) {

    /**
     * 역산 실패 스텝 1건. RECIPE_VERSION 소속 스텝은 {@code versionNo}가 채워진다(RECIPE 본문이면 null).
     *
     * @param recipeId  스텝이 속한 레시피 ID
     * @param stepIndex stepsJson 배열 내 인덱스
     * @param endpointId 역산 시도한 endpointId(없으면 null)
     * @param versionNo RECIPE_VERSION 스냅샷의 버전 번호(RECIPE 본문 스텝이면 null)
     */
    public record FailedStep(
            Long recipeId,
            int stepIndex,
            Long endpointId,
            Integer versionNo) {
    }
}
