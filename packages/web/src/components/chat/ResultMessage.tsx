// 실행 결과 메시지 (RESULT payload, schemaVersion 2 레시피별 구조).
// - 단일 실행(recipes 1개): 결과값 요약 + [결과 보기].
// - 플랜(recipes ≥2): 레시피별 결과 한 줄(✓/✕ + 이름 — 요약) + [결과 보기]/[상세 보기].
// content 요약(payload 파생물)이 있으면 상단에 노출한다.
// [결과 보기] → 사이드 패널 결과 상세 드릴다운(panelStore.openDetail). executionId 유효 시만 활성.

import type { ResultPayload, ResultRecipePayload } from "../../api/types";
import { usePanelStore } from "../../features/panel/panelStore";
import { resultKeyLabel, resultValueDisplay, statusIcon } from "../../features/panel/shared/format";

interface Props {
  payload: ResultPayload;
  /** 표시용 요약 텍스트 (payload 파생물). 있으면 상단에 노출 */
  content?: string | null;
}

export function ResultMessage({ payload, content }: Props) {
  const recipes = payload.recipes ?? [];
  const isPlan = recipes.length >= 2;
  const openDetail = usePanelStore((s) => s.openDetail);
  // executionId 가 유효할 때만 [결과 보기] 활성화(사이드 패널 상세 드릴다운으로 진입).
  const canView = typeof payload.executionId === "number" && payload.executionId > 0;

  return (
    <div className="result-message" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
      {content && <div style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{content}</div>}

      {isPlan ? (
        <PlanResult recipes={recipes} />
      ) : (
        <SingleResult recipe={recipes[0]} />
      )}

      <div>
        <button
          type="button"
          className="btn btn--secondary btn--sm"
          disabled={!canView}
          title={canView ? "결과 상세 보기" : "상세를 볼 수 없어요"}
          onClick={() => canView && openDetail(payload.executionId)}
        >
          결과 보기
        </button>
      </div>
    </div>
  );
}

/** 단일 레시피: 결과값 목록 표시 */
function SingleResult({ recipe }: { recipe: ResultRecipePayload | undefined }) {
  const entries = Object.entries(recipe?.resultValues ?? {});
  if (entries.length === 0) return null;
  return (
    <ul style={listStyle}>
      {entries.map(([key, value]) => (
        <li key={key} style={rowStyle}>
          <span style={{ color: "var(--color-text-secondary)", minWidth: 90 }}>
            {resultKeyLabel(key, recipe?.resultLabels)}
          </span>
          <span style={{ color: "var(--color-text-primary)" }}>{resultValueDisplay(value)}</span>
        </li>
      ))}
    </ul>
  );
}

/** 플랜: 레시피별 결과 한 줄 (상태 아이콘 + 이름 — 요약) */
function PlanResult({ recipes }: { recipes: ResultRecipePayload[] }) {
  return (
    <ul style={listStyle} aria-label="레시피별 결과">
      {recipes.map((recipe) => {
        const label = recipe.recipeName ?? `레시피 ${recipe.sequence + 1}`;
        return (
          <li key={recipe.sequence} style={{ display: "flex", gap: "var(--space-2)", fontSize: "var(--font-size-sm)" }}>
            <span aria-hidden>{statusIcon(recipe.status)}</span>
            <span style={{ color: "var(--color-text-primary)" }}>
              {recipe.sequence + 1}. {label}
            </span>
            {recipe.summary && (
              <span style={{ color: "var(--color-text-tertiary)" }}>— {recipe.summary}</span>
            )}
          </li>
        );
      })}
    </ul>
  );
}

const listStyle: React.CSSProperties = {
  listStyle: "none",
  margin: 0,
  padding: 0,
  display: "flex",
  flexDirection: "column",
  gap: "var(--space-1)",
};

const rowStyle: React.CSSProperties = {
  display: "flex",
  gap: "var(--space-2)",
  fontSize: "var(--font-size-sm)",
};
