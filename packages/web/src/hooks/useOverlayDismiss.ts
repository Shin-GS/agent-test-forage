// 오버레이(드롭다운/drawer) 공통 닫기 인터랙션 훅.
// recipe-editor.cases.md 접근성 규칙: ESC 로 닫기, 바깥 클릭 시 닫기,
// 열릴 때 내부 첫 포커스 요소로 포커스 이동, 닫으면 트리거로 포커스 복귀.
//
// 사용법:
//   const { containerRef, triggerRef } = useOverlayDismiss(open, onClose);
//   <button ref={triggerRef} ... /> <div ref={containerRef} ... />
// - containerRef: 오버레이 컨테이너(이 밖을 클릭하면 닫힘)
// - triggerRef: 트리거 버튼(닫힐 때 포커스 복귀 대상; 바깥 클릭 판정에서 제외)

import { useEffect, useRef } from "react";

export function useOverlayDismiss<
  C extends HTMLElement = HTMLElement,
  T extends HTMLElement = HTMLElement,
>(open: boolean, onClose: () => void) {
  const containerRef = useRef<C>(null);
  const triggerRef = useRef<T>(null);

  useEffect(() => {
    if (!open) return;

    // 열릴 때 내부 첫 포커스 가능 요소로 이동
    const raf = requestAnimationFrame(() => {
      const root = containerRef.current;
      if (!root) return;
      const focusable = root.querySelector<HTMLElement>(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      );
      focusable?.focus();
    });

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
      }
    };

    const handlePointerDown = (e: PointerEvent) => {
      const target = e.target as Node;
      const inContainer = containerRef.current?.contains(target);
      const inTrigger = triggerRef.current?.contains(target);
      if (!inContainer && !inTrigger) {
        onClose();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    // pointerdown 은 click 보다 먼저 발생 → 트리거 재클릭 토글과 충돌하지 않게 trigger 제외
    document.addEventListener("pointerdown", handlePointerDown, true);

    return () => {
      cancelAnimationFrame(raf);
      document.removeEventListener("keydown", handleKeyDown);
      document.removeEventListener("pointerdown", handlePointerDown, true);
      // 닫힐 때 트리거로 포커스 복귀 (여전히 문서에 있으면)
      const trigger = triggerRef.current;
      if (trigger && document.contains(trigger)) {
        trigger.focus();
      }
    };
  }, [open, onClose]);

  return { containerRef, triggerRef };
}
