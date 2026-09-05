// 우측 패널 최상단 "대상 서비스" 고정 블록.
// (기획 docs/specs/panel/overview.md 대상 서비스 블록, chat/overview.md 대화방 서비스 설정)
// - 현재 대화의 대상 서비스(apiSpecId/serviceName)를 표시하고 드롭다운으로 변경한다.
// - 표시값 소스:
//     현재 대화(conversationId 있음) → 그 대화의 apiSpecId/serviceName
//     새 대화(conversationId 없음)   → FE 로컬 pending 값(pendingApiSpecId)
// - 변경 안전 규칙(FE 판정, 기준=conversationId 유무):
//     새 대화   → 확인 없이 pending 만 갱신
//     기존 대화 → ConfirmModal 후 updateService API 호출
// - 미지정은 오류가 아니라 정상 기본 상태 → 중립 톤(경고색/아이콘 금지).
// - 접힘/오버레이로 패널이 안 보이면 이 블록도 안 보인다(폴백은 좌측 목록 배지, 표시만).

import { useEffect, useRef, useState } from "react";
import { conversationsApi } from "../../api";
import type { SpecListItem } from "../../api/types";
import { ConfirmModal } from "../../components/common/ConfirmModal";
import { useToastStore } from "../../store/toastStore";
import { useServices } from "./recipes/useRecipes";

interface Props {
  /** 현재 대화방 ID. null 이면 새 대화(서버 미생성) */
  conversationId: number | null;
  /** 현재 대화방의 대상 서비스 apiSpecId (기존 대화일 때 표시 소스) */
  conversationApiSpecId: number | null;
  /** 현재 대화방의 서비스 표시명 (없으면 드롭다운 목록에서 라벨 폴백) */
  conversationServiceName?: string | null;
  /** 새 대화의 pending 대상 서비스 (새 대화일 때 표시 소스) */
  pendingApiSpecId: number | null;
  /** 새 대화 pending 갱신 */
  onChangePending: (apiSpecId: number | null) => void;
  /** 기존 대화 서비스 변경 성공 시 (serviceName 등 갱신 반영용) */
  onServiceChanged: (apiSpecId: number | null, serviceName: string | null) => void;
}

/** 서비스 라벨: serviceDescription 우선, 없으면 name */
function specLabel(spec: SpecListItem): string {
  return spec.serviceDescription?.trim() || spec.name;
}

export function PanelServiceBlock({
  conversationId,
  conversationApiSpecId,
  conversationServiceName,
  pendingApiSpecId,
  onChangePending,
  onServiceChanged,
}: Props) {
  const { data: services } = useServices();
  const showToast = useToastStore((s) => s.show);

  const [menuOpen, setMenuOpen] = useState(false);
  // 기존 대화 변경 확인 대기: 선택한 apiSpecId(null=미지정) 보관
  const [pendingChange, setPendingChange] = useState<{ apiSpecId: number | null } | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);

  const isNewConversation = conversationId == null;
  // 표시 소스: 새 대화면 pending, 기존 대화면 대화방 값
  const activeApiSpecId = isNewConversation ? pendingApiSpecId : conversationApiSpecId;

  // 표시 라벨: 대화방 serviceName 우선 → 목록에서 매칭 → 미지정
  const matched = services?.find((s) => s.id === activeApiSpecId) ?? null;
  const activeLabel =
    (!isNewConversation && conversationServiceName?.trim()) ||
    (matched ? specLabel(matched) : null);

  // 바깥 클릭 시 드롭다운 닫기
  useEffect(() => {
    if (!menuOpen) return;
    const onDocClick = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    };
    const t = setTimeout(() => document.addEventListener("mousedown", onDocClick), 0);
    return () => {
      clearTimeout(t);
      document.removeEventListener("mousedown", onDocClick);
    };
  }, [menuOpen]);

  // 옵션 선택: 새 대화면 pending 갱신, 기존 대화면 확인 모달
  const handleSelect = (apiSpecId: number | null) => {
    setMenuOpen(false);
    if (apiSpecId === activeApiSpecId) return; // 변경 없음
    if (isNewConversation) {
      onChangePending(apiSpecId);
      return;
    }
    setPendingChange({ apiSpecId });
  };

  // 기존 대화 변경 확정: updateService 호출
  const confirmChange = async () => {
    if (!pendingChange || conversationId == null) return;
    const target = pendingChange.apiSpecId;
    setPendingChange(null);
    try {
      const detail = await conversationsApi.updateService(conversationId, target);
      onServiceChanged(detail.apiSpecId, detail.serviceName ?? null);
    } catch {
      showToast("서비스 변경에 실패했어요. 잠시 후 다시 시도해주세요.", "warning");
    }
  };

  return (
    <div className="panel-service" ref={rootRef}>
      <button
        type="button"
        className={`panel-service__trigger${activeLabel ? "" : " panel-service__trigger--unset"}`}
        aria-haspopup="listbox"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen((v) => !v)}
      >
        <span className="panel-service__icon" aria-hidden>
          🏷️
        </span>
        <span className="panel-service__label">
          {activeLabel ? (
            <>
              <span className="panel-service__label-prefix">대상 서비스:</span> {activeLabel}
            </>
          ) : (
            "서비스 선택 안 됨"
          )}
        </span>
        <span className="panel-service__caret" aria-hidden>
          ▾
        </span>
      </button>

      {menuOpen && (
        <ul className="panel-service__menu" role="listbox" aria-label="대상 서비스 선택">
          <li role="option" aria-selected={activeApiSpecId == null}>
            <button
              type="button"
              className={`panel-service__option${activeApiSpecId == null ? " is-active" : ""}`}
              onClick={() => handleSelect(null)}
            >
              선택 안 함 (미지정)
            </button>
          </li>
          {(services ?? []).map((spec) => (
            <li key={spec.id} role="option" aria-selected={spec.id === activeApiSpecId}>
              <button
                type="button"
                className={`panel-service__option${spec.id === activeApiSpecId ? " is-active" : ""}`}
                onClick={() => handleSelect(spec.id)}
              >
                {specLabel(spec)}
              </button>
            </li>
          ))}
        </ul>
      )}

      <ConfirmModal
        open={pendingChange != null}
        title="대상 서비스를 바꿀까요?"
        description="대상 서비스를 바꾸면 이후 레시피 매칭에 영향을 줍니다. 과거 실행 기록은 유지됩니다. 계속하시겠어요?"
        confirmLabel="변경"
        cancelLabel="취소"
        onCancel={() => setPendingChange(null)}
        onConfirm={() => void confirmChange()}
      />
    </div>
  );
}
