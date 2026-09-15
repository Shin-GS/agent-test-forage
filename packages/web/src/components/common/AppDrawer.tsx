// AppDrawer — 앱 공통 드로어 래퍼 (Base UI Dialog 기반, 우측 슬라이드).
//
// 모달과 동일하게 포커스/ESC/바깥클릭/중첩 top-most/portal 을 Base UI Dialog 에 위임한다.
// 드로어 위에 다른 오버레이(예: 버전 미리보기 모달)가 겹쳐도, Base UI 가 최상단만 dismiss 하므로
// 아래 드로어가 오판으로 닫히지 않는다(기존 자체 pointerdown 캡처 리스너의 겹침 버그 해결).
//
// 시각은 기존 .version-drawer 클래스를 그대로 쓴다(우측 고정 슬라이드 패널 스타일).
// 규칙: docs/specs/common/ui-patterns.md "오버레이 구현 규칙 (Base UI 래퍼로만)"

import type { ReactNode } from "react";
import { Dialog } from "@base-ui/react/dialog";

interface AppDrawerProps {
  open: boolean;
  onClose: () => void;
  /** 접근성 라벨 (드로어 제목). aria-label 로 연결 */
  ariaLabel: string;
  /** 팝업(패널) 추가 클래스 */
  className?: string;
  children: ReactNode;
}

/**
 * 우측 슬라이드 드로어. 편집 화면 위에 떠도 배경 상호작용을 막지 않도록 modal="trap-focus"
 * (포커스만 트랩, 배경 스크롤/클릭은 허용 — 기존 드로어처럼 배경 클릭 시 onClose 로 닫힘).
 */
export function AppDrawer({ open, onClose, ariaLabel, className, children }: AppDrawerProps) {
  return (
    <Dialog.Root
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) onClose();
      }}
      modal="trap-focus"
    >
      <Dialog.Portal>
        <Dialog.Popup
          className={`version-drawer${className ? ` ${className}` : ""}`}
          aria-label={ariaLabel}
        >
          {children}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
