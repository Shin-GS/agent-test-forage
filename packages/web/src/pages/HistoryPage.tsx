// 전체 히스토리 페이지 (라우트 "/history").
// 디자인 명세: docs/design/web/history.html (Case 1~6), 기획: docs/specs/pages/history-full.md.
// - 테이블: 시각 / 레시피명+결과요약 / 서비스 / 상태 / 소요. 최신순(서버 고정 startedAt DESC).
// - 필터: 검색(디바운스) + 서비스(다중 FilterDropdown) + 상태(다중: 성공/실패/중지/취소)
//   + 날짜범위(from~to). 필터 칩 + [필터 초기화] + 활성필터 요약 + 결과 카운트.
// - URL 쿼리 동기화(q/spec/status/from/to). FE 가 API 호출 시 q→keyword, spec→apiSpecId 매핑.
//   필터가 쿼리키에 포함되어(useHistoryList) 변경 시 커서 리셋 + 재조회.
// - 무한 스크롤(useInfiniteQuery + useInfiniteScroll). 실패/중지 행 색 강조(토큰 기반).
// - 행 클릭 → 상세 모달(HistoryDetailModal = ExecutionDetailView + 스텝 JSON 펼침).
// - 빈 상태(이력 0 / 필터 결과 없음) 2종. <1024px 카드 폴백.
//
// 데이터: GET /executions (executionsApi.history), 서비스 옵션 GET /specs (specsApi.list).

import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { specsApi } from "../api";
import type { ExecutionSummaryView, SpecListItem } from "../api/types";
import { FilterDropdown, type FilterOption } from "../components/recipe/FilterDropdown";
import { HistoryDetailModal } from "../components/history/HistoryDetailModal";
import { useHistoryList } from "./history/useHistoryList";
import { useInfiniteScroll } from "../features/panel/shared/useInfiniteScroll";
import { useMediaQuery } from "../hooks/useMediaQuery";
import { formatDuration, statusIcon } from "../features/panel/shared/format";

/** 상태 필터 옵션 (완료 이력 4종만 — history-full.md 확정) */
const STATUS_OPTIONS: FilterOption[] = [
  { value: "SUCCESS", label: "✅ 성공" },
  { value: "FAILED", label: "❌ 실패" },
  { value: "STOPPED", label: "⏹️ 중지" },
  { value: "CANCELLED", label: "⏸️ 취소" },
];

const STATUS_LABEL = new Map(STATUS_OPTIONS.map((o) => [o.value, o.label]));

/** startedAt(ISO) → "MM/DD HH:mm" (테이블 시각 셀). 파싱 불가 시 "-" */
function historyTime(iso: string | null): string {
  if (!iso) return "-";
  const ts = Date.parse(iso);
  if (Number.isNaN(ts)) return "-";
  const d = new Date(ts);
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const hh = String(d.getHours()).padStart(2, "0");
  const mi = String(d.getMinutes()).padStart(2, "0");
  return `${mm}/${dd} ${hh}:${mi}`;
}

function useSpecNameMap(specs: SpecListItem[] | undefined): Map<number, string> {
  return useMemo(() => {
    const map = new Map<number, string>();
    for (const spec of specs ?? []) map.set(spec.id, spec.name);
    return map;
  }, [specs]);
}

/** 실패/중지 행 강조 클래스 (디자인 history.html: row--error / row--stopped) */
function rowStatusClass(code: string | null | undefined): string {
  switch ((code ?? "").toUpperCase()) {
    case "FAILED":
    case "ERROR":
    case "TIMEOUT":
      return "row--error";
    case "STOPPED":
    case "CANCELLED":
    case "CANCELED":
      return "row--stopped";
    default:
      return "";
  }
}

function serviceName(item: ExecutionSummaryView, specNameMap: Map<number, string>): string | null {
  if (item.serviceName && item.serviceName.trim()) return item.serviceName;
  if (item.apiSpecId != null) return specNameMap.get(item.apiSpecId) ?? `#${item.apiSpecId}`;
  return null;
}

export function HistoryPage() {
  const navigate = useNavigate();
  const isCompact = useMediaQuery("(max-width: 1023px)");
  const [searchParams, setSearchParams] = useSearchParams();

  // --- URL → 상태 파싱 ---
  const urlKeyword = searchParams.get("q") ?? "";
  const selectedSpecIds = searchParams.getAll("spec");
  const selectedStatuses = searchParams.getAll("status");
  const from = searchParams.get("from") ?? "";
  const to = searchParams.get("to") ?? "";

  // 검색 입력: 로컬 상태 + 디바운스 후 URL 커밋 (RecipeListPage 패턴 동일)
  const [keywordInput, setKeywordInput] = useState(urlKeyword);
  const committedKeywordRef = useRef(urlKeyword);

  useEffect(() => {
    if (urlKeyword === committedKeywordRef.current) return;
    committedKeywordRef.current = urlKeyword;
    setKeywordInput(urlKeyword);
  }, [urlKeyword]);

  useEffect(() => {
    const trimmed = keywordInput.trim();
    if (trimmed === committedKeywordRef.current) return;
    const handle = setTimeout(() => {
      committedKeywordRef.current = trimmed;
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (trimmed) next.set("q", trimmed);
          else next.delete("q");
          return next;
        },
        { replace: true },
      );
    }, 300);
    return () => clearTimeout(handle);
  }, [keywordInput, setSearchParams]);

  function updateParams(mutate: (params: URLSearchParams) => void) {
    const next = new URLSearchParams(searchParams);
    mutate(next);
    setSearchParams(next, { replace: true });
  }

  function setMultiParam(key: string, values: string[]) {
    updateParams((params) => {
      params.delete(key);
      for (const v of values) params.append(key, v);
    });
  }

  function setSingleParam(key: string, value: string) {
    updateParams((params) => {
      if (value) params.set(key, value);
      else params.delete(key);
    });
  }

  // 서비스 옵션 (레시피 목록과 동일 소스)
  const { data: specs } = useQuery({ queryKey: ["specs"], queryFn: () => specsApi.list() });
  const specNameMap = useSpecNameMap(specs);
  const specOptions: FilterOption[] = useMemo(
    () => (specs ?? []).map((s) => ({ value: String(s.id), label: s.name })),
    [specs],
  );

  // --- 데이터 (필터 → 훅. 필터 변경 시 커서 리셋) ---
  const specIdsNum = useMemo(
    () => selectedSpecIds.map(Number).filter((n) => Number.isFinite(n)),
    [selectedSpecIds],
  );
  const {
    data,
    isLoading,
    isError,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useHistoryList({
    keyword: urlKeyword,
    specIds: specIdsNum,
    statuses: selectedStatuses,
    from,
    to,
  });

  const items = useMemo(() => (data?.pages ?? []).flatMap((p) => p.items), [data]);

  const sentinelRef = useInfiniteScroll<HTMLDivElement>({
    onLoadMore: () => void fetchNextPage(),
    enabled: Boolean(hasNextPage) && !isFetchingNextPage,
  });

  // --- 필터 칩/초기화 ---
  const activeFilterCount =
    selectedSpecIds.length + selectedStatuses.length + (from ? 1 : 0) + (to ? 1 : 0);
  const hasActiveQuery = activeFilterCount > 0 || urlKeyword.trim().length > 0;

  function clearAllFilters() {
    updateParams((params) => {
      params.delete("spec");
      params.delete("status");
      params.delete("from");
      params.delete("to");
      params.delete("q");
    });
    setKeywordInput("");
    committedKeywordRef.current = "";
  }

  function removeSpec(id: string) {
    setMultiParam("spec", selectedSpecIds.filter((v) => v !== id));
  }
  function removeStatus(s: string) {
    setMultiParam("status", selectedStatuses.filter((v) => v !== s));
  }

  // --- 상세 모달 ---
  const [detailId, setDetailId] = useState<number | null>(null);

  const hasAny = items.length > 0;

  return (
    <div className="recipe-page">
      <div className="page-header">
        <span className="page-header__title">실행 히스토리</span>
      </div>

      <div className="page-body">
        {/* 툴바: 검색 + 서비스/상태 필터 + 날짜 범위 */}
        <div className="list-toolbar">
          <div className="list-toolbar__row">
            <input
              className="input"
              type="text"
              placeholder="🔍 레시피명 검색..."
              aria-label="레시피명 검색"
              style={{ maxWidth: "240px" }}
              value={keywordInput}
              onChange={(e) => setKeywordInput(e.target.value)}
            />

            <FilterDropdown
              label="서비스"
              options={specOptions}
              selected={selectedSpecIds}
              onChange={(next) => setMultiParam("spec", next)}
            />
            <FilterDropdown
              label="상태"
              options={STATUS_OPTIONS}
              selected={selectedStatuses}
              onChange={(next) => setMultiParam("status", next)}
            />

            <div className="date-range">
              <label className="date-range__label" htmlFor="hist-from">
                기간
              </label>
              <input
                className="input"
                type="date"
                id="hist-from"
                aria-label="시작일"
                value={from}
                max={to || undefined}
                onChange={(e) => setSingleParam("from", e.target.value)}
              />
              <span className="date-range__label" aria-hidden>
                ~
              </span>
              <input
                className="input"
                type="date"
                aria-label="종료일"
                value={to}
                min={from || undefined}
                onChange={(e) => setSingleParam("to", e.target.value)}
              />
            </div>
          </div>

          {/* 필터 칩 요약 */}
          {activeFilterCount > 0 && (
            <div className="filter-summary">
              <span className="filter-summary__label">활성 필터 {activeFilterCount}개 ·</span>
              {selectedSpecIds.map((id) => (
                <span key={`spec-${id}`} className="filter-chip">
                  {specNameMap.get(Number(id)) ?? `#${id}`}
                  <button
                    type="button"
                    className="filter-chip__remove"
                    aria-label={`${specNameMap.get(Number(id)) ?? id} 필터 제거`}
                    onClick={() => removeSpec(id)}
                  >
                    ✕
                  </button>
                </span>
              ))}
              {selectedStatuses.map((s) => (
                <span key={`status-${s}`} className="filter-chip">
                  {STATUS_LABEL.get(s) ?? s}
                  <button
                    type="button"
                    className="filter-chip__remove"
                    aria-label={`${STATUS_LABEL.get(s) ?? s} 필터 제거`}
                    onClick={() => removeStatus(s)}
                  >
                    ✕
                  </button>
                </span>
              ))}
              {(from || to) && (
                <span className="filter-chip">
                  {from || "…"} ~ {to || "…"}
                  <button
                    type="button"
                    className="filter-chip__remove"
                    aria-label="기간 필터 제거"
                    onClick={() =>
                      updateParams((p) => {
                        p.delete("from");
                        p.delete("to");
                      })
                    }
                  >
                    ✕
                  </button>
                </span>
              )}
              <button type="button" className="btn btn--ghost btn--sm" onClick={clearAllFilters}>
                필터 초기화
              </button>
              <span className="list-toolbar__spacer" />
              {hasAny && (
                <span className="list-count" role="status" aria-live="polite">
                  총 {items.length}건{hasNextPage ? "+" : ""}
                </span>
              )}
            </div>
          )}
        </div>

        {/* 로딩(첫 페이지) */}
        {isLoading && (
          <div className="recipe-state" role="status" aria-live="polite">
            실행 히스토리를 불러오는 중입니다…
          </div>
        )}

        {/* 에러 */}
        {isError && (
          <div className="recipe-state recipe-state--error" role="alert">
            <div>실행 히스토리를 불러오지 못했습니다.</div>
            <button type="button" className="btn btn--secondary btn--sm" onClick={() => void refetch()}>
              다시 시도
            </button>
          </div>
        )}

        {/* 빈 상태: 이력 없음 vs 필터 결과 없음 */}
        {!isLoading && !isError && !hasAny && !hasActiveQuery && (
          <div className="empty-state">
            <div className="empty-state__icon">🕘</div>
            <div className="empty-state__title">아직 실행한 레시피가 없어요</div>
            <div className="empty-state__desc">
              채팅에서 레시피를 실행하면 여기에 이력이 쌓입니다. 대화방에서 원하는 작업을 요청해보세요.
            </div>
            <button type="button" className="btn btn--primary" onClick={() => navigate("/")}>
              채팅으로 이동
            </button>
          </div>
        )}

        {!isLoading && !isError && !hasAny && hasActiveQuery && (
          <div className="empty-state">
            <div className="empty-state__icon">🔍</div>
            <div className="empty-state__title">조건에 맞는 실행 기록이 없어요</div>
            <div className="empty-state__desc">검색어나 필터를 완화하거나 초기화해보세요.</div>
            <button type="button" className="btn btn--secondary" onClick={clearAllFilters}>
              필터 초기화
            </button>
          </div>
        )}

        {/* 결과 카운트(필터 없을 때) */}
        {!isLoading && !isError && hasAny && activeFilterCount === 0 && (
          <div className="list-count" role="status" aria-live="polite">
            총 {items.length}건{hasNextPage ? "+" : ""}
          </div>
        )}

        {/* Desktop 테이블 */}
        {!isLoading && !isError && hasAny && !isCompact && (
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">실행 시각</th>
                <th scope="col">레시피명 · 결과</th>
                <th scope="col">서비스</th>
                <th scope="col">상태</th>
                <th scope="col">소요시간</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const svc = serviceName(item, specNameMap);
                const dur = formatDuration(item.durationMs);
                return (
                  <tr
                    key={item.id}
                    className={rowStatusClass(item.status.code)}
                    tabIndex={0}
                    onClick={() => setDetailId(item.id)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        setDetailId(item.id);
                      }
                    }}
                  >
                    <td className="time-cell">{historyTime(item.startedAt)}</td>
                    <td>
                      <div className="recipe-cell__name">{item.title ?? "실행"}</div>
                      {item.resultSummary && (
                        <div className="recipe-cell__summary">{item.resultSummary}</div>
                      )}
                    </td>
                    <td>
                      {svc ? (
                        <span className="badge badge--neutral">{svc}</span>
                      ) : (
                        <span style={{ color: "var(--color-text-tertiary)" }}>-</span>
                      )}
                    </td>
                    <td>
                      <span className="status-cell">
                        <span aria-hidden>{statusIcon(item.status.code)}</span>
                        {item.status.description}
                      </span>
                    </td>
                    <td className="duration-cell">{dur ?? "-"}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}

        {/* 카드 폴백 (Tablet 이하) */}
        {!isLoading && !isError && hasAny && isCompact && (
          <div className="exec-cards">
            {items.map((item) => {
              const svc = serviceName(item, specNameMap);
              const dur = formatDuration(item.durationMs);
              const cls = rowStatusClass(item.status.code);
              return (
                <div
                  key={item.id}
                  className={`exec-card${cls ? ` exec-card--${cls.replace("row--", "")}` : ""}`}
                  role="button"
                  tabIndex={0}
                  onClick={() => setDetailId(item.id)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      setDetailId(item.id);
                    }
                  }}
                >
                  <div className="exec-card__top">
                    <span className="exec-card__name">{item.title ?? "실행"}</span>
                    <span className="status-cell">
                      <span aria-hidden>{statusIcon(item.status.code)}</span>
                      {item.status.description}
                    </span>
                  </div>
                  {item.resultSummary && (
                    <div className="exec-card__summary">{item.resultSummary}</div>
                  )}
                  <div className="exec-card__meta">
                    {svc && <span className="badge badge--neutral">{svc}</span>}
                    {dur && <span>· {dur}</span>}
                    <span>· {historyTime(item.startedAt)}</span>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* 무한 스크롤 sentinel + 하단 로딩/종료 표시 */}
        {!isLoading && !isError && hasAny && (
          <div ref={sentinelRef}>
            {isFetchingNextPage && (
              <div className="infinite-loader" role="status" aria-live="polite">
                <span className="spinner" />
                <span>더 불러오는 중...</span>
              </div>
            )}
            {!hasNextPage && <div className="infinite-end">모든 기록을 불러왔어요</div>}
          </div>
        )}
      </div>

      {/* 상세 모달 */}
      {detailId != null && (
        <HistoryDetailModal executionId={detailId} onClose={() => setDetailId(null)} />
      )}
    </div>
  );
}
