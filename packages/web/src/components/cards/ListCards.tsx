// 최소 렌더 카드 모음 (service_select / candidates / plan).
// 디자인 명세: 목록 항목은 .card--interactive, 버튼은 .btn.
// 프로토타입: 목록 표시만. 클릭 동작은 후속 구현(자리표시).

/* eslint-disable @typescript-eslint/no-explicit-any */

import type {
  CandidatesCard as CandidatesCardMeta,
  PlanCard as PlanCardMeta,
  ServiceSelectCard as ServiceSelectCardMeta,
} from "../../api/types";

const listStyle: React.CSSProperties = {
  display: "flex",
  flexDirection: "column",
  gap: "var(--space-2)",
  marginTop: "var(--space-2)",
};

const rowStyle: React.CSSProperties = {
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  gap: "var(--space-2)",
  padding: "var(--space-2) var(--space-3)",
  background: "var(--color-bg-tertiary)",
  borderRadius: "var(--radius-md)",
};

const nameStyle: React.CSSProperties = {
  fontSize: "var(--font-size-sm)",
  fontWeight: "var(--font-weight-medium)",
};

const descStyle: React.CSSProperties = {
  fontSize: "var(--font-size-xs)",
  color: "var(--color-text-tertiary)",
  marginTop: 2,
};

/** 서비스 선택 — 버튼 목록만 표시 (동작은 후속) */
export function ServiceSelectCard({ card }: { card: ServiceSelectCardMeta }) {
  const services: any[] = card.services ?? [];
  return (
    <div style={{ display: "flex", flexWrap: "wrap", gap: "var(--space-2)", marginTop: "var(--space-2)" }}>
      {services.map((svc, idx) => (
        <button
          key={svc.id ?? svc.apiSpecId ?? idx}
          type="button"
          className="btn btn--secondary btn--sm"
          disabled
          title="후속 구현 예정"
        >
          {svc.name ?? svc.title ?? String(svc)}
        </button>
      ))}
      {services.length === 0 && <span style={descStyle}>표시할 서비스가 없습니다</span>}
    </div>
  );
}

/** 후보 선택 — 항목 목록만 표시 (동작은 후속) */
export function CandidatesCard({ card }: { card: CandidatesCardMeta }) {
  const candidates: any[] = card.candidates ?? [];
  return (
    <div style={listStyle}>
      {candidates.map((c, idx) => (
        <div key={c.recipeId ?? c.id ?? idx} style={rowStyle}>
          <div>
            <div style={nameStyle}>{c.name ?? c.recipeName ?? `후보 ${idx + 1}`}</div>
            {(c.description ?? c.desc) && <div style={descStyle}>{c.description ?? c.desc}</div>}
          </div>
        </div>
      ))}
      {candidates.length === 0 && <span style={descStyle}>후보가 없습니다</span>}
    </div>
  );
}

/** 플랜 제안 — 레시피 목록만 표시 (동작은 후속) */
export function PlanCard({ card }: { card: PlanCardMeta }) {
  const recipes: any[] = card.recipes ?? card.steps ?? [];
  return (
    <div style={listStyle}>
      <div style={{ fontSize: "var(--font-size-sm)", fontWeight: "var(--font-weight-semibold)" }}>📋 실행 계획</div>
      {recipes.map((r, idx) => (
        <div key={r.recipeId ?? r.id ?? idx} style={rowStyle}>
          <span style={nameStyle}>
            {idx + 1}. {r.name ?? r.recipeName ?? `레시피 ${idx + 1}`}
          </span>
        </div>
      ))}
      {recipes.length === 0 && <span style={descStyle}>플랜 항목이 없습니다</span>}
    </div>
  );
}
