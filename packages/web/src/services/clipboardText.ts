// 턴(AI 응답) 복사용 마크다운 텍스트 조립.
// 이미 받은 part 데이터만 사용한다(AI/BE 재호출 없음).
//
// 파트별 처리:
//   TEXT       → content 원본 그대로.
//   RESULT     → payload(asResultPayload) 로 레시피별 요약 + 결과값(key: value 줄나열).
//   CARD       → 인터랙티브 카드(execution_mode/service_select/candidates/plan)는 제외(빈 문자열).
//                (버튼/입력 UI 라 복사 텍스트로 의미가 없음)
//   PROGRESS / INVESTIGATE / REFERENCES → 1차 스킵(빈 문자열).
//                TODO: 후속으로 진행/조회 출처 요약 텍스트를 조립할 수 있다.
//
// 여러 파트는 빈 줄로 join 한다.

import type { PartResponse } from "../api/types";
import { asResultPayload } from "./messagePayload";
import { resultKeyLabel, resultValueDisplay } from "../features/panel/shared/format";

/** RESULT 파트 payload → md 텍스트. summary + 결과값 목록. */
function resultPartToText(part: PartResponse): string {
  const payload = asResultPayload(part.payload);
  if (!payload) {
    // payload 파싱 실패 시 content 요약 폴백.
    return part.content?.trim() ?? "";
  }

  const lines: string[] = [];
  for (const recipe of payload.recipes ?? []) {
    const name = recipe.recipeName?.trim();
    if (name) lines.push(`### ${name}`);
    if (recipe.summary?.trim()) lines.push(recipe.summary.trim());

    const entries = Object.entries(recipe.resultValues ?? {});
    for (const [key, value] of entries) {
      const label = resultKeyLabel(key, recipe.resultLabels);
      lines.push(`- ${label}: ${resultValueDisplay(value)}`);
    }
  }
  return lines.join("\n").trim();
}

/** 파트 하나를 md 텍스트로 변환. 복사 대상이 아니면 빈 문자열. */
function partToText(part: PartResponse): string {
  const typeCode = (part.type.code ?? "").toUpperCase();
  switch (typeCode) {
    case "TEXT":
      return part.content?.trim() ?? "";
    case "RESULT":
      return resultPartToText(part);
    // 인터랙티브 카드 및 진행/조회류는 복사 텍스트에서 제외.
    case "CARD":
    case "PROGRESS":
    case "INVESTIGATE":
    case "REFERENCES":
    case "ACTION_PICKER":
    default:
      return "";
  }
}

/**
 * 턴(파트 배열)을 복사용 마크다운 텍스트로 조립한다.
 * 빈 파트는 제외하고, 남은 파트를 빈 줄로 join 한다.
 */
export function buildTurnClipboardText(parts: PartResponse[]): string {
  return (parts ?? [])
    .map(partToText)
    .filter((text) => text.length > 0)
    .join("\n\n")
    .trim();
}
