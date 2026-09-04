// 대화 목록 (개편: ChatGPT식 통합 사이드바의 대화 기록 영역).
// - 각 항목: 아이콘 + 제목 + 상태 뱃지(우선순위 1개) + 서비스 배지(serviceName)
// - hover 시 더보기(⋮) → 드롭다운(이름 변경 / 삭제)
// - 인라인 이름 편집(ChatGPT식): input 전환, 전체선택, Enter/blur 저장, ESC 취소
// - 삭제: ConfirmModal(브라우저 confirm 금지)
//
// 상태 뱃지는 대화방 목록 status(StatusView) + unread 로 도출한다(기획 chat/overview.md):
//   input_waiting 🟡 > ai_responding/executing 🔄 > unread 🔵 > idle(없음)

import { useEffect, useRef, useState } from "react";
import type { ConversationSummary } from "../../api/types";
import { ConfirmModal } from "../common/ConfirmModal";

interface Props {
  conversations: ConversationSummary[];
  currentId: number | null;
  onSelect: (conversationId: number) => void;
  /** 이름 변경 저장 (검증 통과 후 호출). 실패 시 reject → 호출측이 롤백/토스트 */
  onRename: (conversationId: number, title: string) => Promise<void>;
  /** 삭제 확정 시 호출 */
  onDelete: (conversationId: number) => Promise<void>;
}

/** 상태 뱃지 도출: 우선순위 1개만 반환. 없으면 null. */
function statusBadge(c: ConversationSummary): { icon: string; label: string } | null {
  const code = (c.status?.code ?? "").toUpperCase();
  if (code === "INPUT_WAITING" || code === "WAITING_INPUT") {
    return { icon: "🟡", label: "입력 대기" };
  }
  if (code === "AI_RESPONDING" || code === "EXECUTING") {
    return { icon: "🔄", label: "처리 중" };
  }
  if (c.unread) {
    return { icon: "🔵", label: "안 읽음" };
  }
  return null;
}

/** 이름 검증: 트림 → 제어문자 제거 → 길이 clamp. 빈값이면 null(롤백). */
function sanitizeTitle(raw: string): string | null {
  // eslint-disable-next-line no-control-regex
  const noControl = raw.replace(/[\u0000-\u001F\u007F]/g, "");
  const trimmed = noControl.trim();
  if (trimmed.length === 0) return null;
  return trimmed.slice(0, 50);
}

export function ConversationSidebar({
  conversations,
  currentId,
  onSelect,
  onRename,
  onDelete,
}: Props) {
  const list = conversations ?? [];

  // 열린 더보기 메뉴 대화 id
  const [menuId, setMenuId] = useState<number | null>(null);
  // 인라인 편집 중인 대화 id + 현재 입력값
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editValue, setEditValue] = useState("");
  // 삭제 확인 대상
  const [deleteTarget, setDeleteTarget] = useState<ConversationSummary | null>(null);

  const editInputRef = useRef<HTMLInputElement>(null);
  // Enter 가 blur 도 유발하므로 저장 1회만 실행되도록 가드
  const savingRef = useRef(false);
  // IME 조합 중 여부
  const composingRef = useRef(false);

  // 편집 진입 시 포커스 + 전체 선택
  useEffect(() => {
    if (editingId != null) {
      const el = editInputRef.current;
      if (el) {
        el.focus();
        el.select();
      }
    }
  }, [editingId]);

  // 바깥 클릭 시 더보기 메뉴 닫기
  useEffect(() => {
    if (menuId == null) return;
    const close = () => setMenuId(null);
    // 다음 틱부터 등록(현재 클릭이 즉시 닫는 것 방지)
    const t = setTimeout(() => document.addEventListener("click", close), 0);
    return () => {
      clearTimeout(t);
      document.removeEventListener("click", close);
    };
  }, [menuId]);

  const startEditing = (c: ConversationSummary) => {
    setMenuId(null);
    setEditingId(c.id);
    setEditValue(c.title ?? "");
  };

  const cancelEditing = () => {
    setEditingId(null);
    setEditValue("");
  };

  const commitEditing = async (c: ConversationSummary) => {
    if (savingRef.current) return;
    const next = sanitizeTitle(editValue);
    // 빈값이거나 변경 없음 → 롤백(저장 안 함)
    if (next == null || next === (c.title ?? "")) {
      cancelEditing();
      return;
    }
    savingRef.current = true;
    const targetId = c.id;
    cancelEditing();
    try {
      await onRename(targetId, next);
    } finally {
      savingRef.current = false;
    }
  };

  return (
    <div className="sidebar-list" aria-label="대화 목록">
      {list.length === 0 && (
        <div className="sidebar-list__empty">아직 대화가 없습니다</div>
      )}

      {list.map((c) => {
        const active = c.id === currentId;
        const editing = editingId === c.id;
        const badge = statusBadge(c);

        if (editing) {
          return (
            <div key={c.id} className="sidebar-item sidebar-item--editing">
              <input
                ref={editInputRef}
                className="sidebar-item__edit"
                value={editValue}
                maxLength={50}
                aria-label="대화 이름 편집"
                onChange={(e) => setEditValue(e.target.value)}
                onCompositionStart={() => {
                  composingRef.current = true;
                }}
                onCompositionEnd={() => {
                  composingRef.current = false;
                }}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    // IME 조합 중 Enter 는 무시(조합 확정용)
                    if (composingRef.current || e.nativeEvent.isComposing) return;
                    e.preventDefault();
                    void commitEditing(c);
                  } else if (e.key === "Escape") {
                    e.preventDefault();
                    cancelEditing();
                  }
                }}
                onBlur={() => void commitEditing(c)}
              />
            </div>
          );
        }

        return (
          <div
            key={c.id}
            className={`sidebar-item${active ? " active" : ""}`}
            role="button"
            tabIndex={0}
            onClick={() => onSelect(c.id)}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                onSelect(c.id);
              }
            }}
          >
            <div className="sidebar-item__row">
              <span className="sidebar-item__icon" aria-hidden>
                {active ? "◉" : "💬"}
              </span>
              <span className="sidebar-item__text">{c.title ?? "새 대화"}</span>
              {badge && (
                <span className="sidebar-item__status" title={badge.label} aria-label={badge.label}>
                  {badge.icon}
                </span>
              )}
            </div>

            {c.serviceName && (
              <div className="sidebar-item__meta">
                <span className="sidebar-item__service">{c.serviceName}</span>
              </div>
            )}

            {/* 더보기 버튼 */}
            <button
              type="button"
              className={`sidebar-item__more${menuId === c.id ? " is-open" : ""}`}
              aria-label="대화 메뉴"
              aria-haspopup="menu"
              aria-expanded={menuId === c.id}
              onClick={(e) => {
                e.stopPropagation();
                setMenuId((prev) => (prev === c.id ? null : c.id));
              }}
            >
              ⋮
            </button>

            {menuId === c.id && (
              <div className="dropdown-menu" role="menu" onClick={(e) => e.stopPropagation()}>
                <button
                  type="button"
                  role="menuitem"
                  className="dropdown-item"
                  onClick={() => startEditing(c)}
                >
                  ✏️ 이름 변경
                </button>
                <button
                  type="button"
                  role="menuitem"
                  className="dropdown-item dropdown-item--danger"
                  onClick={() => {
                    setMenuId(null);
                    setDeleteTarget(c);
                  }}
                >
                  🗑️ 삭제
                </button>
              </div>
            )}
          </div>
        );
      })}

      {/* 삭제 확인 모달 */}
      <ConfirmModal
        open={deleteTarget != null}
        title="대화를 삭제할까요?"
        description={
          deleteTarget
            ? `"${deleteTarget.title ?? "새 대화"}" 대화가 삭제됩니다. 작업 히스토리는 그대로 유지돼요.`
            : undefined
        }
        confirmLabel="삭제"
        cancelLabel="취소"
        danger
        onCancel={() => setDeleteTarget(null)}
        onConfirm={() => {
          const target = deleteTarget;
          setDeleteTarget(null);
          if (target) void onDelete(target.id);
        }}
      />
    </div>
  );
}
