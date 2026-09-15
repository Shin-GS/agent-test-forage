// 다중 선택 체크박스 드롭다운 필터 (레시피 목록 Case 7).
// - 트리거 버튼 + 선택 개수 배지, 열면 체크박스 메뉴.
// - "빈 선택 = 전체"이므로 선택 0개면 배지 숨김.
// - 오버레이 동작(열림/닫힘·ESC·바깥클릭·roving focus·포커스 복귀·aria)은 AppMenu(Base UI Menu)에 위임.
// - 체크 항목은 Menu.CheckboxItem(closeOnClick=false 기본 → 다중선택 중 메뉴 유지). aria-expanded 자동.

import { AppMenu } from "../common/AppMenu";

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
  function toggleValue(value: string) {
    if (selected.includes(value)) {
      onChange(selected.filter((v) => v !== value));
    } else {
      onChange([...selected, value]);
    }
  }

  return (
    <div className="filter-dropdown">
      <AppMenu
        side="bottom"
        align="start"
        popupClassName="filter-dropdown__menu"
        ariaLabel={`${label} 필터`}
        trigger={
          <button type="button" className="filter-dropdown__trigger" aria-haspopup="menu">
            {label}
            {selected.length > 0 && <span className="filter-dropdown__count">{selected.length}</span>}
            <span aria-hidden="true">▾</span>
          </button>
        }
      >
        {options.length === 0 ? (
          <div className="filter-dropdown__empty">선택할 항목이 없습니다</div>
        ) : (
          options.map((opt) => (
            <AppMenu.CheckboxItem
              key={opt.value}
              className="filter-check"
              checked={selected.includes(opt.value)}
              onCheckedChange={() => toggleValue(opt.value)}
            >
              <span className="filter-check__box" aria-hidden>
                <AppMenu.CheckboxItemIndicator>✓</AppMenu.CheckboxItemIndicator>
              </span>
              {opt.label}
            </AppMenu.CheckboxItem>
          ))
        )}
      </AppMenu>
    </div>
  );
}
