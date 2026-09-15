// AppModal — 앱 공통 모달 래퍼 (Base UI Dialog 기반).
//
// 오버레이 동작(포커스 트랩/복귀·ESC·바깥클릭·스크롤락·배경 inert·중첩 top-most·portal·aria)은
// Base UI Dialog 에 위임하고, 시각은 기존 디자인 토큰 클래스(.modal-backdrop/.modal/.modal__*)를
// 그대로 입힌다. 앱 코드는 Base UI 를 직접 import 하지 않고 이 래퍼만 사용한다(교체 지점 단일화).
//
// 규칙: docs/specs/common/ui-patterns.md "오버레이 구현 규칙 (Base UI 래퍼로만)"

import type { ReactNode } from "react";
import { Dialog } from "@base-ui/react/dialog";

interface AppModalProps {
  /** 열림 상태(제어형) */
  open: boolean;
  /** 닫기 요청(ESC/바깥클릭/닫기버튼 등). 실제 open=false 전이는 부모가 상태로 처리 */
  onClose: () => void;
  /** 헤더 제목. 문자열/노드 모두 허용. aria 라벨로도 연결됨 */
  title?: ReactNode;
  /** 헤더 우측 닫기(✕) 버튼 노출 여부 (기본 true) */
  showCloseButton?: boolean;
  /** 배경(백드롭) 클릭으로 닫힘 허용 여부 (기본 true) */
  dismissOnBackdrop?: boolean;
  /** 팝업 추가 클래스 (예: "modal--wide") */
  className?: string;
  /** 팝업 인라인 스타일 (예: maxWidth) */
  style?: React.CSSProperties;
  /** aria 설명 연결용 노드 id (선택) */
  describedById?: string;
  /**
   * 열릴 때 최초 포커스 대상(선택). ref 를 넘기면 그 요소로 포커스.
   * 없으면 Base UI 기본(팝업 내 첫 tabbable). 확인 다이얼로그의 "취소 우선 포커스" 등에 사용.
   */
  initialFocusRef?: React.RefObject<HTMLElement | null>;
  /** 본문/푸터 등 내용 */
  children: ReactNode;
}

/**
 * 제어형 모달. `open`/`onClose`로 부모가 상태를 소유한다.
 * Base UI Dialog 의 onOpenChange(open, details)에서 닫힘 요청을 onClose 로 위임하되,
 * dismissOnBackdrop=false 면 바깥클릭('outside-press')로 인한 닫힘은 무시한다.
 */
export function AppModal({
  open,
  onClose,
  title,
  showCloseButton = true,
  dismissOnBackdrop = true,
  className,
  style,
  describedById,
  initialFocusRef,
  children,
}: AppModalProps) {
  return (
    <Dialog.Root
      open={open}
      onOpenChange={(nextOpen, details) => {
        if (nextOpen) return; // 열림 전이는 부모가 관리
        // 배경 클릭 닫기를 막아야 하는 모달은 outside-press 사유를 무시한다.
        if (!dismissOnBackdrop && details.reason === "outside-press") return;
        onClose();
      }}
    >
      <Dialog.Portal>
        {/* Base UI 구조: Backdrop/Popup 은 형제. 기존 .modal-backdrop 는 flex 컨테이너 전제였으므로
            위치는 app-modal-* 보정 클래스로 잡고(popup=fixed 중앙, backdrop=순수 덮개), 시각(.modal 등)은 유지. */}
        <Dialog.Backdrop className="modal-backdrop app-modal-backdrop" />
        <Dialog.Popup
          className={`modal app-modal-popup${className ? ` ${className}` : ""}`}
          style={style}
          aria-describedby={describedById}
          initialFocus={initialFocusRef}
        >
          {title != null && (
            <div className="modal__header">
              {/* Dialog.Title 이 aria-labelledby 를 자동 연결한다. 시각 스타일은 기존 .modal__title */}
              <Dialog.Title className="modal__title">{title}</Dialog.Title>
              {showCloseButton && (
                <Dialog.Close
                  className="btn btn--ghost btn--sm"
                  aria-label="닫기"
                  render={<button type="button" />}
                >
                  ✕
                </Dialog.Close>
              )}
            </div>
          )}
          {children}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
