// 대화 목록 사이드바 (디자인 명세 chat.html aside.sidebar).
// - "채팅 세션" 섹션 타이틀
// - listConversations(userId) 결과를 .sidebar__item 으로 표시 (활성 항목 ◉)
// - 하단 .sidebar__footer 에 "+ 새 대화" 버튼 (margin-top:auto 로 바닥 고정)

import type { ConversationSummary } from "../../api/types";

interface Props {
  conversations: ConversationSummary[];
  currentId: number | null;
  onSelect: (conversationId: number) => void;
  onNew: () => void;
}

export function ConversationSidebar({ conversations, currentId, onSelect, onNew }: Props) {
  // 방어: 어떤 이유로든 목록이 비정상(undefined)이어도 렌더가 깨지지 않게 한다.
  const list = conversations ?? [];
  return (
    <aside className="sidebar">
      <div className="sidebar__section-title">채팅 세션</div>

      {list.length === 0 && (
        <div
          className="sidebar__section-title"
          style={{ color: "var(--color-text-tertiary)", fontWeight: "var(--font-weight-normal)" }}
        >
          아직 대화가 없습니다
        </div>
      )}

      {list.map((c) => {
        const active = c.id === currentId;
        return (
          <button
            key={c.id}
            type="button"
            className={`sidebar__item${active ? " active" : ""}`}
            onClick={() => onSelect(c.id)}
          >
            <span aria-hidden>{active ? "◉" : "💬"}</span>
            <span
              style={{
                flex: 1,
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
            >
              {c.title ?? "새 대화"}
            </span>
          </button>
        );
      })}

      <div className="sidebar__footer">
        <button type="button" className="btn btn--ghost btn--sm" style={{ width: "100%" }} onClick={onNew}>
          + 새 대화
        </button>
      </div>
    </aside>
  );
}
