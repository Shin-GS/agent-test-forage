// 실행 결과 메시지 (RESULT payload, schemaVersion 2 레시피별 구조).
// - 단일 실행(recipes 1개): 결과값 요약 + [결과 보기].
// - 플랜(recipes ≥2): 레시피별 결과 한 줄(✓/✕ + 이름 — 요약) + [결과 보기]/[상세 보기].
// content 요약(payload 파생물)이 있으면 상단에 노출한다.
// [결과 보기] → 사이드 패널 결과 상세 드릴다운(panelStore.openDetail). executionId 유효 시만 활성.

import type { ResultPayload, ResultRecipePayload } from "../../api/types";
import { usePanelStore } from "../../features/panel/panelStore";
import { resultKeyLabel, resultValueDisplay, statusIcon } from "../../features/panel/shared/format";
import { Markdown } from "./Markdown";

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
      {/* content 는 BE에서 결과 템플릿(Handlebars)을 1회 렌더한 마크다운 문자열.
          FE는 <Markdown>(remark-gfm + rehype-sanitize)으로 렌더만 한다(강조/목록/표 + XSS 방어). */}
      {content && <Markdown content={content} />}

      {isPlan ? (
        // 플랜 결과(레시피별 한 줄)는 그 자체가 요약이라 접기 대상이 아니다(card-ui.md 결과 제공형 상세).
        <PlanResult recipes={recipes} />
      ) : (
        // 단건 결과값 목록은 마크다운 결과 메시지와 중복되는 보조 정보.
        // content(마크다운)가 있으면 기본 접힘, 없으면 펼침(유일 정보).
        <SingleResult recipe={recipes[0]} collapsible={Boolean(content)} />
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

/**
 * 채팅 결과값 목록 표시용 요약. 배열/객체는 원문 JSON 대신 "N건" 요약으로 표기해
 * 채팅에 raw JSON 덩어리가 노출되지 않게 한다(실제 내용은 마크다운 결과/패널 드릴다운).
 * 스칼라/빈값은 공용 resultValueDisplay 규칙을 따른다.
 */
function chatResultValueDisplay(value: unknown): string {
  if (Array.isArray(value)) return `${value.length}건`;
  if (value != null && typeof value === "object") {
    return `${Object.keys(value as Record<string, unknown>).length}개 항목`;
  }
  return resultValueDisplay(value);
}

/** 결과값 key-value 목록 (표시명 폴백 + 배열/객체는 "N건" 요약) */
function ResultValueList({ recipe }: { recipe: ResultRecipePayload | undefined }) {
  const entries = Object.entries(recipe?.resultValues ?? {});
  return (
    <ul style={listStyle}>
      {entries.map(([key, value]) => (
        <li key={key} style={rowStyle}>
          <span style={{ color: "var(--color-text-secondary)", minWidth: 90 }}>
            {resultKeyLabel(key, recipe?.resultLabels)}
          </span>
          <span style={{ color: "var(--color-text-primary)" }}>{chatResultValueDisplay(value)}</span>
        </li>
      ))}
    </ul>
  );
}

/**
 * 단일 레시피 결과값 목록.
 * - collapsible=true(결과 메시지 content가 있는 경우): 기본 접힘 details 토글("상세 값 보기").
 * - collapsible=false(content 없음): 목록을 그대로 펼쳐 표시(유일 정보).
 */
function SingleResult({
  recipe,
  collapsible,
}: {
  recipe: ResultRecipePayload | undefined;
  collapsible: boolean;
}) {
  const entries = Object.entries(recipe?.resultValues ?? {});
  if (entries.length === 0) return null;

  if (!collapsible) {
    return <ResultValueList recipe={recipe} />;
  }

  return (
    <details className="result-values">
      <summary className="result-values__toggle">상세 값 보기</summary>
      <div style={{ marginTop: "var(--space-2)" }}>
        <ResultValueList recipe={recipe} />
      </div>
    </details>
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
