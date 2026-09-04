// 레시피 저장 전 클라이언트 유효성 검증 (authoring.md 유효성 검증 표 일부).
// 서버도 검증(순환참조 등)하지만, 필수 필드 미매핑 등은 FE 에서 선제 안내한다.

import type { RecipeStep } from "../../api/types";
import type { RecipeFormState } from "./recipeForm";

export interface RecipeValidationResult {
  valid: boolean;
  /** 사용자에게 보여줄 에러 메시지 목록 */
  messages: string[];
  /** 메타 필드 에러 */
  meta: { name?: boolean; description?: boolean; apiSpecId?: boolean };
  /** 에러가 있는 스텝 인덱스 */
  errorStepIndexes: number[];
  /** stepIndex → 값 소스 미지정 매핑 인덱스 목록 */
  stepMappingErrors: Record<number, number[]>;
}

/**
 * 매핑 값 누락 여부: 필드명이 지정된 매핑인데 값 소스 값이 비어있는지.
 * ai_generate 는 값이 불필요하므로 항상 통과.
 * (요청 필드 매핑은 수동 추가이므로 스키마 required 대신 "필드명이 있으면 값도 필요" 규칙 적용)
 */
function isMissingMappingValue(source: string, value: string | undefined): boolean {
  if (source === "ai_generate") return false;
  return !value || value.trim() === "";
}

export function validateRecipe(form: RecipeFormState): RecipeValidationResult {
  const messages: string[] = [];
  const meta: RecipeValidationResult["meta"] = {};
  const errorStepIndexes: number[] = [];
  const stepMappingErrors: Record<number, number[]> = {};

  // 메타 필수
  if (!form.name.trim()) {
    meta.name = true;
    messages.push("레시피명은 필수입니다");
  }
  if (!form.description.trim()) {
    meta.description = true;
    messages.push("설명은 필수입니다");
  }
  if (form.apiSpecId == null) {
    meta.apiSpecId = true;
    messages.push("대상 서비스를 선택해주세요");
  }

  // 스텝 최소 1개
  if (form.steps.length === 0) {
    messages.push("스텝을 최소 1개 이상 추가해주세요");
  }

  form.steps.forEach((step: RecipeStep, index) => {
    if (step.type === "api") {
      if (step.endpointId == null) {
        errorStepIndexes.push(index);
        messages.push(`스텝 ${index + 1}: 엔드포인트를 선택해주세요`);
      }
      // 요청 필드 매핑: 필드명을 지정했는데 값 소스 값이 비어있는 경우 (ai_generate 제외)
      const missing: number[] = [];
      (step.requestMappings ?? []).forEach((m, mi) => {
        if (m.field.trim() && isMissingMappingValue(m.source, m.value)) missing.push(mi);
      });
      if (missing.length > 0) {
        stepMappingErrors[index] = missing;
        if (!errorStepIndexes.includes(index)) errorStepIndexes.push(index);
        messages.push(`스텝 ${index + 1}: 요청 필드의 값을 입력해주세요 (또는 AI 생성 선택)`);
      }
    } else if (step.type === "recipe") {
      if (step.recipeId == null) {
        errorStepIndexes.push(index);
        messages.push(`스텝 ${index + 1}: 대상 레시피를 선택해주세요`);
      }
    } else if (step.type === "script") {
      if (!step.code.trim()) {
        errorStepIndexes.push(index);
        messages.push(`스텝 ${index + 1}: 코드를 입력해주세요`);
      }
    }
  });

  return {
    valid: messages.length === 0,
    messages,
    meta,
    errorStepIndexes,
    stepMappingErrors,
  };
}
