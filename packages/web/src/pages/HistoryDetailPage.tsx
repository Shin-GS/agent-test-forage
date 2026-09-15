// 실행 결과 상세 (별도 페이지, 라우트 "/history/:executionId").
// 디자인 명세: docs/design/web/history.html (Case 2·3·4·5·6), 기획: docs/specs/pages/history-full.md.
// - 모달이 아니라 별도 페이지다(딥링크/새로고침/공유 가능). 스펙 상세(AdminSpecDetailPage)와 동일 골격.
// - PageShell(title + subject=실행제목) + PageActionBar([← 목록으로] + 제목/상태아이콘 + 상태배지).
//   우측 액션 없음(원본 레시피 링크는 각 레시피 블록 헤더로 이동).
// - 뒤로가기: useBackToList("/history") — 직전 목록 URL(필터 유지) 복귀, 딥링크는 /history 폴백.
// - 본문: ExecutionDetailView variant="page"(요약 헤더/실패 배너/레시피 블록/복사) 재사용.
// - 로딩=스피너, 404/에러=전용 빈 상태 + [목록으로](스펙 상세와 동일 패턴).
//
// 데이터: GET /executions/{id} (useExecutionDetail) — 본인 소유 검증, 타인 접근 404.

import { useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../api";
import { PageShell } from "../components/layout/PageShell";
import { PageActionBar } from "../components/layout/PageActionBar";
import { ExecutionDetailView } from "../features/panel/detail/ExecutionDetailView";
import { useExecutionDetail } from "../features/panel/detail/useExecutionDetail";
import { statusIcon } from "../features/panel/shared/format";
import { useBackToList } from "../hooks/useListNavigation";

/** 상태 코드 → 배지 클래스 (성공/실패/중지/취소/부분성공) */
function statusBadgeClass(code: string | null | undefined): string {
  switch ((code ?? "").toUpperCase()) {
    case "SUCCESS":
    case "COMPLETED":
      return "badge badge--success";
    case "FAILED":
    case "ERROR":
    case "TIMEOUT":
      return "badge badge--error";
    case "PARTIAL":
    case "PARTIAL_SUCCESS":
      return "badge badge--warning";
    default:
      // STOPPED / CANCELLED / 진행중 등은 중립
      return "badge badge--neutral";
  }
}

export function HistoryDetailPage() {
  const navigate = useNavigate();
  // [← 목록으로]: 직전 히스토리 목록 URL(필터 포함) 복귀, 없으면 /history 폴백.
  const backToList = useBackToList("/history");
  const { executionId: idParam } = useParams<{ executionId: string }>();
  const executionId = Number(idParam);

  const { data, isError, error } = useExecutionDetail(executionId);

  const notFound = isError && error instanceof ApiError && error.status === 404;

  const title = data?.title ?? "실행 결과";

  return (
    <PageShell
      title="실행 히스토리"
      subject={data?.title}
      actionBar={
        <PageActionBar
          onBack={backToList}
          title={
            <>
              {statusIcon(data?.status.code)} {title}
            </>
          }
          meta={
            data && (
              <span className={statusBadgeClass(data.status.code)} style={{ marginLeft: "var(--space-2)" }}>
                {data.status.description}
              </span>
            )
          }
        />
      }
    >
      <div className="page-body detail-body">
        {/* 404 / 삭제 / 권한 없음: 전용 빈 상태 + [목록으로] (토스트 아님) */}
        {notFound && (
          <div className="empty-state" role="alert">
            <div className="empty-state__icon">🔍</div>
            <div className="empty-state__title">해당 실행 기록을 찾을 수 없어요</div>
            <div className="empty-state__desc">접근 권한이 없거나 삭제되었을 수 있어요.</div>
            <button type="button" className="btn btn--primary" onClick={backToList}>
              목록으로
            </button>
          </div>
        )}

        {/* 그 외 에러 */}
        {isError && !notFound && (
          <div className="recipe-state recipe-state--error" role="alert">
            <div>실행 상세를 불러오지 못했습니다{error instanceof Error ? `: ${error.message}` : ""}</div>
            <button type="button" className="btn btn--secondary btn--sm" onClick={backToList}>
              목록으로
            </button>
          </div>
        )}

        {/* 로딩 + 정상: ExecutionDetailView(page variant)가 내부에서 로딩/본문을 처리.
            에러 상태는 위에서 처리하므로 여기선 !isError 일 때만 렌더. */}
        {!isError && (
          <ExecutionDetailView
            executionId={executionId}
            onBack={backToList}
            variant="page"
            showStepJson
            showMode
            onOpenRecipe={(recipeId) => navigate(`/recipes/${recipeId}/edit`)}
          />
        )}
      </div>
    </PageShell>
  );
}
