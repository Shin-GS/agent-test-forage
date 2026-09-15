// AppTooltip — 앱 공통 툴팁 래퍼 (Base UI Tooltip 기반).
//
// hover/focus 트리거 + 지연/포커스 관리/포지셔닝/포탈/aria 를 Base UI Tooltip 에 위임하고,
// 시각은 .app-tooltip-popup 클래스(기존 .tooltip__content 시각을 이식)로 입힌다.
// 앱 코드는 Base UI 를 직접 import 하지 않고 이 래퍼만 쓴다.
//
// 규칙: docs/specs/common/ui-patterns.md "오버레이 구현 규칙 (Base UI 래퍼로만)"

import type { ReactNode } from "react";
import { Tooltip } from "@base-ui/react/tooltip";

type Side = "top" | "bottom" | "left" | "right";
type Align = "start" | "center" | "end";

interface AppTooltipProps {
  /** 트리거 요소(포커스 가능 권장). Base UI 가 aria-describedby/hover·focus 를 연결한다. */
  trigger: ReactNode;
  /** 툴팁 내용 */
  children: ReactNode;
  /** 배치 방향 (기본 top) */
  side?: Side;
  /** 정렬 (기본 center) */
  align?: Align;
  /** 트리거-팝업 간격(px, 기본 6) */
  sideOffset?: number;
  /** 팝업 추가 클래스 (내용 폭/정렬 커스터마이즈용) */
  popupClassName?: string;
  /** 팝업 인라인 스타일 (예: 폭 지정) */
  popupStyle?: React.CSSProperties;
}

export function AppTooltip({
  trigger,
  children,
  side = "top",
  align = "center",
  sideOffset = 6,
  popupClassName,
  popupStyle,
}: AppTooltipProps) {
  return (
    <Tooltip.Root>
      <Tooltip.Trigger render={trigger as React.ReactElement} />
      <Tooltip.Portal>
        <Tooltip.Positioner side={side} align={align} sideOffset={sideOffset} className="app-tooltip-positioner">
          <Tooltip.Popup
            className={`app-tooltip-popup${popupClassName ? ` ${popupClassName}` : ""}`}
            style={popupStyle}
          >
            {children}
          </Tooltip.Popup>
        </Tooltip.Positioner>
      </Tooltip.Portal>
    </Tooltip.Root>
  );
}
