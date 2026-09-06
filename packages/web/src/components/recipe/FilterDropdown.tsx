// 다중 선택 체크박스 드롭다운 필터 (레시피 목록 Case 7).
// - 트리거 버튼 + 선택 개수 배지, 열면 체크박스 메뉴.
// - "빈 선택 = 전체"이므로 선택 0개면 배지 숨김.
// - ESC/바깥 클릭 닫기 + 포커스 관리는 useOverlayDismiss 가 담당.
// - aria-expanded 는 open 상태와 동기화.

import { useState } from "react";
import { useOverlayDismiss } from "../../hooks/useOverlayDismiss";

export interface FilterOption {
  value: string;
  label: string;
}

interface FilterDropdownProps {
  /** 트리거 라벨 (예: "서비스") */
  label: string;
  options: FilterOption[];
  /** 선택된 value 집합 */
  selected: string[];
  onChange: (next: string[]) => void;
}

export function FilterDropdown({ label, options, selected, onChange }: FilterDropdownProps) {
  const [open, setOpen] = useState(false);
  const { containerRef, triggerRef } = useOverlayDismiss<HTMLDivElement, HTMLButtonElement>(
    open,
    () => setOpen(false),
  );

  function toggleValue(value: string) {
    if (selected.includes(value)) {
      onChange(selected.filter((v) => v !== value));
    } else {
      onChange([...selected, value]);
    }
  }

  return (
    <div className="filter-dropdown">
      <button
        ref={triggerRef}
        type="button"
        className="filter-dropdown__trigger"
        aria-haspopup="true"
        aria-expanded={open}
        onClick={() => setOpen((prev) => !prev)}
      >
        {label}
        {selected.length > 0 && <span className="filter-dropdown__count">{selected.length}</span>}
        <span aria-hidden="true">▾</span>
      </button>
      {open && (
        <div ref={containerRef} className="filter-dropdown__menu" role="menu">
          {options.length === 0 ? (
            <div className="filter-dropdown__empty">선택할 항목이 없습니다</div>
          ) : (
            options.map((opt) => (
              <label key={opt.value} className="filter-check">
                <input
                  type="checkbox"
                  checked={selected.includes(opt.value)}
                  onChange={() => toggleValue(opt.value)}
                />
                {opt.label}
              </label>
            ))
          )}
        </div>
      )}
    </div>
  );
}
