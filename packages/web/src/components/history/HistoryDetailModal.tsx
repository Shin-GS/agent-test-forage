// 전체 히스토리 페이지의 실행 결과 상세 모달.
// - 사이드 패널의 ExecutionDetailView 를 그대로 재사용하되(로직 중복 최소화),
//   히스토리에서는 스텝별 원본 응답/입력 JSON 펼침을 보여주도록 showStepJson 을 켠다.
// - 모달 셸: role=dialog + aria-modal, 배경 클릭/ESC 로 닫기 + 포커스 관리는
//   useOverlayDismiss(열릴 때 내부 첫 포커스, 닫힐 때 트리거 복귀)로 처리.
// - ExecutionDetailView 헤더의 [← 뒤로] 버튼이 onBack(=닫기) 을 호출한다.

import { useEffect } from "react";
import { useOverlayDismiss } from "../../hooks/useOverlayDismiss";
import { ExecutionDetailView } from "../../features/panel/detail/ExecutionDetailView";
import { useExecutionDetail } from "../../features/panel/detail/useExecutionDetail";
import { useToastStore } from "../../store/toastStore";
import { ApiError } from "../../api";

interface Props {
  executionId: number;
  onClose: () => void;
}

export function HistoryDetailModal({ executionId, onClose }: Props) {
  // open 은 항상 true(마운트 = 열림). 언마운트로 닫는다.
  const { containerRef } = useOverlayDismiss<HTMLDivElement, HTMLElement>(true, onClose);
  const showToast = useToastStore((s) => s.show);

  // 트리거(행/카드)는 HistoryPage 에 있어 useOverlayDismiss.triggerRef 로 잡히지 않는다.
  // 모달을 연 시점의 포커스 요소(document.activeElement)를 저장했다가 언마운트 시 복귀시킨다.
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    return () => {
      if (opener && document.contains(opener)) opener.focus();
    };
  }, []);

  // 상세는 ExecutionDetailView 내부에서도 조회되지만, 캐시를 공유(queryKey ['execution', id])하므로
  // 여기서 error 만 관찰해 404(타인 소유/삭제)를 토스트로 안내하고 모달을 닫는다.
  const { error } = useExecutionDetail(executionId);
  useEffect(() => {
    if (!error) return;
    const notFound = error instanceof ApiError && error.status === 404;
    showToast(
      notFound
        ? "해당 실행 기록을 찾을 수 없어요. 접근 권한이 없거나 삭제되었을 수 있어요."
        : "실행 상세를 불러오지 못했어요. 잠시 후 다시 시도해주세요.",
      "error",
    );
    onClose();
  }, [error, showToast, onClose]);

  return (
    <div
      className="modal-backdrop"
      onMouseDown={(e) => {
        // 백드롭(바깥) 클릭 시에만 닫는다. 내부 클릭은 무시.
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={containerRef}
        className="modal modal--wide history-detail-modal"
        role="dialog"
        aria-modal="true"
        aria-label="실행 결과 상세"
      >
        <ExecutionDetailView executionId={executionId} onBack={onClose} showStepJson showMode />
      </div>
    </div>
  );
}
