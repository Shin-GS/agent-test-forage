package com.testforge.service.recipe;

import com.testforge.entity.recipe.enums.ValidationStatus;

/**
 * 레시피 스텝 검증 결과. VALID/INVALID 상태와 실패 상세 메시지를 담는다.
 * 순환 참조처럼 저장 자체를 거부해야 하는 경우는 결과가 아니라 예외(ApiException.recipeCycle)로 처리된다.
 */
public record RecipeValidationResult(
        // 검증 상태 (VALID / INVALID)
        ValidationStatus status,
        // 실패 상세 (VALID면 null)
        String message) {

    /** 검증 통과 결과 */
    public static RecipeValidationResult valid() {
        return new RecipeValidationResult(ValidationStatus.VALID, null);
    }

    /** 검증 실패 결과 (경고성 — 저장은 허용, 메시지 보존) */
    public static RecipeValidationResult invalid(String message) {
        return new RecipeValidationResult(ValidationStatus.INVALID, message);
    }
}
