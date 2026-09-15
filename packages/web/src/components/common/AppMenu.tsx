// AppMenu — 앱 공통 드롭다운 메뉴 래퍼 (Base UI Menu 기반).
//
// 오버레이 동작(열림/닫힘·ESC·바깥클릭·roving focus·포커스 복귀·portal·aria-haspopup/expanded·
// 배경 스크롤 처리·중첩 top-most)은 Base UI Menu 에 위임하고, 시각은 기존 디자인 토큰 클래스를
// 그대로 입힌다(각 파트에 className 전달). 앱 코드는 Base UI 를 직접 import 하지 않고 이 래퍼만 쓴다.
//
// 슬롯형: trigger(트리거 요소) + children(팝업 내용). 팝업 항목은 AppMenu.Item / AppMenu.CheckboxItem
// / AppMenu.RadioGroup+RadioItem 을 사용한다(각각 Base UI Menu 파트 재노출).
//
// 규칙: docs/specs/common/ui-patterns.md "오버레이 구현 규칙 (Base UI 래퍼로만)"

import type { ReactNode } from "react";
import { Menu } from "@base-ui/react/menu";

type Side = "top" | "bottom" | "left" | "right";
type Align = "start" | "center" | "end";

interface AppMenuProps {
  /** 열림 상태(제어형). 생략 시 Base UI 비제어(내부 상태) */
  open?: boolean;
  /** 열림/닫힘 요청. 제어형일 때 부모가 상태를 갱신한다 */
  onOpenChange?: (open: boolean) => void;
  /**
   * 트리거 요소. 반드시 단일 포커스 가능 요소(button 등)여야 한다.
   * Base UI 가 aria-haspopup/aria-expanded/ref 를 자동 연결한다.
   */
  trigger: ReactNode;
  /** 팝업 내용 (AppMenu.Item 등) */
  children: ReactNode;
  /** 팝업 배치 방향 (기본 bottom) */
  side?: Side;
  /** 팝업 정렬 (기본 start) */
  align?: Align;
  /** 트리거-팝업 간격(px, 기본 4) */
  sideOffset?: number;
  /** 팝업(Popup) 추가 클래스 — 기존 .dropdown-menu 등 시각 유지용 */
  popupClassName?: string;
  /** 팝업 aria-label (라벨 텍스트가 없을 때) */
  ariaLabel?: string;
  /**
   * 모달 여부(기본 false). 메뉴는 대개 비모달(문서 스크롤/상호작용 허용).
   * 배경 inert 가 필요하면 true.
   */
  modal?: boolean;
}

/**
 * 제어/비제어 모두 지원하는 드롭다운 메뉴.
 * - trigger 는 Menu.Trigger 의 render 로 연결(기존 버튼 마크업/클래스 유지).
 * - Popup 에 기존 시각 클래스를 입혀 디자인 토큰을 보존한다.
 */
export function AppMenu({
  open,
  onOpenChange,
  trigger,
  children,
  side = "bottom",
  align = "start",
  sideOffset = 4,
  popupClassName,
  ariaLabel,
  modal = false,
}: AppMenuProps) {
  return (
    <Menu.Root
      open={open}
      onOpenChange={onOpenChange ? (nextOpen) => onOpenChange(nextOpen) : undefined}
      modal={modal}
    >
      <Menu.Trigger render={trigger as React.ReactElement} />
      <Menu.Portal>
        <Menu.Positioner side={side} align={align} sideOffset={sideOffset} className="app-menu-positioner">
          <Menu.Popup className={popupClassName} aria-label={ariaLabel}>
            {children}
          </Menu.Popup>
        </Menu.Positioner>
      </Menu.Portal>
    </Menu.Root>
  );
}

// 편의 재노출 — 앱 코드가 Base UI 를 직접 import 하지 않도록 한다.
AppMenu.Item = Menu.Item;
AppMenu.CheckboxItem = Menu.CheckboxItem;
AppMenu.CheckboxItemIndicator = Menu.CheckboxItemIndicator;
AppMenu.RadioGroup = Menu.RadioGroup;
AppMenu.RadioItem = Menu.RadioItem;
AppMenu.RadioItemIndicator = Menu.RadioItemIndicator;
AppMenu.Separator = Menu.Separator;
AppMenu.Group = Menu.Group;
AppMenu.GroupLabel = Menu.GroupLabel;
