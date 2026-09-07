// 정보 조회(investigate) 참고 자료 (디자인 명세 chat.html Case 17 .references).
// TEXT 답변 메시지에 동반된 references payload 를 답변 본문 하단에 출처 칩 리스트로 표시한다.
//
// 인라인 확장 동작 (investigation.md "인라인 확장 동작", 1단계 api_spec 칩):
// - references[].url 이 "/specs/{apiSpecId}/endpoints/{endpointId}" 패턴이면 → 클릭 가능한 칩(button).
//   클릭 시 그 자리에서 아코디언으로 해당 엔드포인트 상세를 펼친다(각 칩 독립 토글, 동시 펼침 가능).
// - url 이 외부 http(s) URL 이면 → 새 탭으로 여는 외부 링크 칩(📄 label ↗). confluence 등 (investigation.md "외부 URL 확장 동작").
// - url 이 null 이거나 그 외 형식이면 → 비인터랙션 정적 칩(📋 label)으로 폴백.
// - 펼침 시점(최초 1회) getSpec(apiSpecId) 을 useQuery(['spec', apiSpecId])로 조회(같은 스펙 칩 공유).
//   조회 중/실패/엔드포인트 없음 3단 안내(role=status). 성공 시 method/path + summary + 서비스명.
//   펼친 시점에 DEPRECATED/INACTIVE 로 바뀐 엔드포인트는 상세를 그대로 두되 상태 뱃지를 붙인다.
// - "전체 스펙 보기" 링크는 관리자(ADMIN)에게만 렌더(일반 사용자는 미렌더).

import { useId, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";

import { specsApi } from "../../api";
import type { ReferenceItemPayload, ReferencesPayload, SpecDetail, SpecEndpointItem } from "../../api/types";
import { useAuthStore } from "../../store/authStore";

interface Props {
  payload: ReferencesPayload;
}

/**
 * references url 을 "/specs/{apiSpecId}/endpoints/{endpointId}" 패턴으로 파싱한다.
 * 두 세그먼트가 모두 양의 정수여야 매칭. null/외부 URL/형식 불일치는 null 반환(정적 폴백).
 */
export function parseSpecEndpointUrl(
  url: string | null | undefined,
): { apiSpecId: number; endpointId: number } | null {
  if (!url) return null;
  const match = /^\/specs\/(\d+)\/endpoints\/(\d+)$/.exec(url);
  if (!match) return null;
  const apiSpecId = Number(match[1]);
  const endpointId = Number(match[2]);
  if (!Number.isSafeInteger(apiSpecId) || !Number.isSafeInteger(endpointId)) return null;
  return { apiSpecId, endpointId };
}

/** 외부 http(s) URL 여부 (confluence/figma 등 새 탭 링크 대상). 내부 /specs 패턴은 여기서 판별하지 않는다. */
export function isExternalUrl(url: string | null | undefined): url is string {
  return !!url && /^https?:\/\//.test(url);
}

/** 엔드포인트 상태가 확장 시점에 종료(지원 종료)된 상태인지 — 뱃지 표기 대상 */
function endpointStatusBadge(status: SpecEndpointItem["status"]): string | null {
  const code = status?.code;
  if (code === "DEPRECATED") return "지원 종료";
  if (code === "INACTIVE") return "비활성";
  return null;
}

export function MessageReferences({ payload }: Props) {
  const references = payload.references ?? [];

  if (references.length === 0) return null;

  return (
    <div className="references">
      <div className="references__label">참고한 자료</div>
      <ul className="references__list">
        {references.map((ref, index) => (
          <ReferenceItem key={`${index}-${ref.label}`} reference={ref} />
        ))}
      </ul>
    </div>
  );
}

interface ReferenceItemProps {
  reference: ReferenceItemPayload;
}

function ReferenceItem({ reference }: ReferenceItemProps) {
  const target = parseSpecEndpointUrl(reference.url);
  const detailId = useId();
  const buttonId = useId();
  const [expanded, setExpanded] = useState(false);

  // 외부 http(s) URL(confluence 등) → 새 탭 링크로 분기 (인라인 확장 아님). 내부 /specs 패턴이 먼저 매칭되므로
  // target 이 없을 때만 여기 도달한다. aria-label 에 "새 탭에서 열림" 을 명시하고 rel 로 opener 를 차단한다.
  if (!target && isExternalUrl(reference.url)) {
    return (
      <li className="reference-item">
        <a
          className="reference-link"
          href={reference.url}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={`${reference.label} (Confluence, 새 탭에서 열림)`}
        >
          <span aria-hidden>📄</span> {reference.label}
          <span className="reference-link__external" aria-hidden>
            ↗
          </span>
        </a>
      </li>
    );
  }

  // 패턴 불일치(null/기타) → 기존 정적 칩으로 폴백 (클릭/확장 없음).
  // 라벨 텍스트(📋 label)가 이미 화면에 노출되므로 별도 aria-label 은 두지 않는다(중복 낭독 방지).
  if (!target) {
    return (
      <li className="reference-chip">
        <span aria-hidden>📋</span> {reference.label}
      </li>
    );
  }

  return (
    <li className="reference-item">
      <button
        type="button"
        id={buttonId}
        className="reference-btn"
        aria-expanded={expanded}
        aria-controls={detailId}
        onClick={() => setExpanded((v) => !v)}
      >
        <span aria-hidden>📋</span> {reference.label}
        <span className="reference-btn__toggle" aria-hidden>
          {expanded ? "▾" : "▸"}
        </span>
      </button>
      <ReferenceDetail
        id={detailId}
        labelledBy={buttonId}
        apiSpecId={target.apiSpecId}
        endpointId={target.endpointId}
        expanded={expanded}
      />
    </li>
  );
}

interface ReferenceDetailProps {
  id: string;
  /** 이 상세 영역에 이름을 주는 토글 버튼 id (aria-labelledby — region 접근성) */
  labelledBy: string;
  apiSpecId: number;
  endpointId: number;
  expanded: boolean;
}

function ReferenceDetail({ id, labelledBy, apiSpecId, endpointId, expanded }: ReferenceDetailProps) {
  const isAdmin = useAuthStore((s) => s.user?.role === "ADMIN");

  // 펼쳐졌을 때만 조회. queryKey 로 같은 스펙을 여러 칩이 공유(캐시).
  const { data, isLoading, isError } = useQuery<SpecDetail>({
    queryKey: ["spec", apiSpecId],
    queryFn: () => specsApi.getSpec(apiSpecId),
    enabled: expanded,
    staleTime: 60_000,
  });

  const endpoint = data?.endpoints.find((e) => e.id === endpointId);
  const badge = endpoint ? endpointStatusBadge(endpoint.status) : null;

  return (
    <div
      className="reference-detail"
      id={id}
      role="region"
      aria-labelledby={labelledBy}
      hidden={!expanded}
    >
      {isLoading && (
        <div className="reference-detail__status" role="status" aria-live="polite">
          불러오는 중...
        </div>
      )}

      {!isLoading && isError && (
        <div className="reference-detail__status" role="status" aria-live="polite">
          스펙 정보를 불러올 수 없습니다
        </div>
      )}

      {!isLoading && !isError && data && !endpoint && (
        <div className="reference-detail__status" role="status" aria-live="polite">
          해당 엔드포인트를 찾을 수 없습니다
        </div>
      )}

      {!isLoading && !isError && data && endpoint && (
        <>
          <div className="reference-detail__service">서비스: {data.name}</div>
          <div className="reference-detail__endpoint">
            <span>
              {endpoint.method} {endpoint.path}
            </span>
            {badge && <span className="badge badge--warning">{badge}</span>}
          </div>
          {endpoint.summary && (
            <div className="reference-detail__summary">{endpoint.summary}</div>
          )}
          {isAdmin && (
            <Link className="reference-detail__spec-link" to={`/admin/specs/${apiSpecId}`}>
              전체 스펙 보기 →
            </Link>
          )}
        </>
      )}
    </div>
  );
}
