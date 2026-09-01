package com.testforge.dto.execution;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 레시피 실행 뷰 (EXECUTION_RECIPE) + 스텝 목록. 스냅샷(recipeSnapshot)은 FE가 스텝을 실행하는 데
 * 필요한 정의(스텝/변수/결과)를 담아 내린다.
 */
public record ExecutionRecipeView(
        Long id,
        Long recipeId,
        String recipeName,
        Integer recipeVersionNo,
        Integer sequence,
        StatusView status,
        Object recipeSnapshot,
        Object resultValues,
        List<ExecutionStepView> steps,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {
}
