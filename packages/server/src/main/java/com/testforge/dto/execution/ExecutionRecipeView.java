package com.testforge.dto.execution;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 레시피 실행 뷰 (EXECUTION_RECIPE) + 스텝 목록. 스냅샷(recipeSnapshot)은 FE가 스텝을 실행하는 데
 * 필요한 정의(스텝/변수/결과)를 담아 내린다.
 *
 * <p>{@code resultLabels}는 결과키 → 사람말 표시명 맵으로, 실행 당시 스냅샷의 결과 정의(④)에서
 * label이 등록된 key만 담는다(messaging.md RESULT.resultLabels와 동일 계약). 상세 드릴다운에서
 * 원본 key 대신 표시명을 보여주기 위한 것이며, label 미등록 key는 미포함 → FE가 원본 key로 폴백한다.
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
        Map<String, String> resultLabels,
        List<ExecutionStepView> steps,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {
}
