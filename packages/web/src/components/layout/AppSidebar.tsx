// 전역 통합 사이드바 (ChatGPT식). 모든 라우트에서 상시 표시된다.
// (기획 docs/specs/chat/overview.md 레이아웃 섹션)
// 위→아래: [🔨 테스트메이트 + 접기] / [+ 새 채팅] / [메뉴 nav(레시피·설정)] /
//          [대화 기록 목록(스크롤)] / [회원정보(하단 고정, 드롭업 메뉴)]
//
// - 대화 목록/상태는 전역 chatStore 를 읽는다(ChatPage 와 공유 → 정합 유지).
// - 다른 페이지에서 대화 항목/새 채팅 클릭 시 navigate("/") 로 채팅 라우트로 이동.
// - 접힘(레일)에서도 메뉴 아이콘으로 페이지 이동 가능(현재 라우트 active 강조).
// - 회원정보는 authStore.user 로 렌더, 클릭 시 드롭업(로그아웃).

import { useCallback, useEffect, useRef, useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { conversationsApi } from "../../api";
import { ConversationSidebar } from "../chat/ConversationSidebar";
import { useChatStore } from "../../store/chatStore";
import { useToastStore } from "../../store/toastStore";
import { useAuthStore } from "../../store/authStore";

interface Props {
  /** 레일(아이콘) 축소 여부 */
  collapsed: boolean;
  /** 접기/펼치기 토글 */
  onToggleCollapse: () => void;
  /** 로그아웃 (AppLayout 의 세션 종료 로직 재사용) */
  onLogout: () => void;
}

/** 사용자 표시 이니셜 (name 우선, 없으면 username 첫 글자) */
function userInitials(name: string | undefined, username: string | undefined): string {
  const base = (name && name.trim()) || (username && username.trim()) || "";
  if (!base) return "?";
  return base.slice(0, 2).toUpperCase();
}

export function AppSidebar({ collapsed, onToggleCollapse, onLogout }: Props) {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const showToast = useToastStore((s) => s.show);

  // 전역 대화 상태 (ChatPage 와 공유)
  const conversations = useChatStore((s) => s.conversations);
  const currentConversationId = useChatStore((s) => s.currentConversationId);
  const setConversations = useChatStore((s) => s.setConversations);
  const clearConversation = useChatStore((s) => s.clearConversation);

  // 회원 드롭업 메뉴 열림 상태
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);

  // 대화 목록 로드 (앱 진입 시 1회, 전역)
  const loadConversations = useCallback(async () => {
    try {
      const list = await conversationsApi.listConversations();
      setConversations(list);
    } catch (err) {
      showToast(
        err instanceof Error ? `대화 목록을 불러오지 못했습니다: ${err.message}` : "대화 목록을 불러오지 못했습니다",
        "error"
      );
    }
  }, [setConversations, showToast]);

  useEffect(() => {
    void loadConversations();
  }, [loadConversations]);

  // 대화 선택 → 대화방 URL 로 이동만 한다(navigate). URL 이 source of truth 이며,
  // 메시지 로드/읽음 처리/store 동기화는 ChatPage 가 :conversationId 를 읽어 수행한다.
  // (URL→store 단방향. 같은 대화 재클릭도 동일 URL 로 navigate — ChatPage 가 id 일치로 재로드 스킵.)
  const handleSelect = useCallback(
    (conversationId: number) => {
      navigate(`/c/${conversationId}`);
    },
    [navigate]
  );

  // 새 채팅 → 채팅 라우트로만 이동. 대화 상태 정리는 ChatPage 의 URL→store effect 가
  // param==null 을 감지해 clearConversation() 을 수행한다(정리 책임 일원화).
  // navigate("/") 는 conversationIdParam 을 null 로 만들고, 현재 대화가 열려 있으면
  // 그 effect 가 정리하므로 여기서 직접 clearConversation 을 호출하지 않는다(이중 호출 방지).
  const handleNew = useCallback(() => {
    navigate("/");
  }, [navigate]);

  // 이름 변경: 서버 응답(정규화된 title)을 신뢰값으로 반영. 실패 시 재조회로 롤백.
  const handleRename = useCallback(
    async (conversationId: number, title: string) => {
      try {
        const updated = await conversationsApi.updateTitle(conversationId, title);
        const next = conversations.map((c) =>
          c.id === conversationId ? { ...c, title: updated.title } : c
        );
        setConversations(next);
      } catch (err) {
        showToast(
          err instanceof Error ? `이름 변경 실패: ${err.message}` : "이름 변경에 실패했습니다",
          "error"
        );
        void loadConversations();
      }
    },
    [conversations, setConversations, showToast, loadConversations]
  );

  // 삭제 확정: 낙관적 제거 + 현재 방이면 먼저 초기화 → 그 다음 API 호출. 실패 시 재조회 롤백.
  // 초기화(clearConversation)를 API 호출보다 앞에 두는 이유:
  //   BE 는 삭제 처리 중 session_deleted SSE 를 발행하는데, 로컬처럼 지연이 거의 없으면
  //   그 이벤트가 remove() 의 await 가 풀리기 전에 이 탭에 도착할 수 있다. 그 시점에
  //   currentConversationId 가 아직 삭제 대상이면 useSse 가 "보고 있던 대화가 삭제됨"으로
  //   오인해 홈 이탈 + 토스트를 띄운다(내가 방금 지운 방인데 안내가 뜨는 어색함).
  //   API 호출 전에 currentConversationId 를 null 로 만들어 이 race 를 원천 차단한다
  //   (다른 탭에서의 삭제만 이탈/안내 대상으로 남는다).
  const handleDelete = useCallback(
    async (conversationId: number) => {
      const wasCurrent = currentConversationId === conversationId;
      setConversations(conversations.filter((c) => c.id !== conversationId));
      if (wasCurrent) {
        clearConversation();
      }
      try {
        await conversationsApi.remove(conversationId);
      } catch (err) {
        showToast(
          err instanceof Error ? `삭제 실패: ${err.message}` : "삭제에 실패했습니다",
          "error"
        );
        void loadConversations();
      }
    },
    [conversations, setConversations, currentConversationId, clearConversation, showToast, loadConversations]
  );

  // 회원 메뉴 바깥 클릭 시 닫기
  useEffect(() => {
    if (!userMenuOpen) return;
    const onClick = (e: MouseEvent) => {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target as Node)) {
        setUserMenuOpen(false);
      }
    };
    const t = setTimeout(() => document.addEventListener("click", onClick), 0);
    return () => {
      clearTimeout(t);
      document.removeEventListener("click", onClick);
    };
  }, [userMenuOpen]);

  return (
    <aside className={`app-sidebar${collapsed ? " collapsed" : ""}`} aria-label="네비게이션">
      {/* 로고 + 접기 버튼 */}
      <div className="sidebar-brand">
        <span className="sidebar-brand__logo">🔨 테스트메이트</span>
        <button
          type="button"
          className="sidebar-brand__collapse"
          aria-label={collapsed ? "사이드바 펼치기" : "사이드바 접기"}
          aria-expanded={!collapsed}
          onClick={onToggleCollapse}
        >
          {collapsed ? "»" : "«"}
        </button>
      </div>

      {/* + 새 채팅 (상단 고정) */}
      <button type="button" className="sidebar-new-chat" onClick={handleNew} aria-label="새 채팅" title="새 채팅">
        <span className="sidebar-nav__icon" aria-hidden>➕</span>
        <span className="sidebar-new-chat__label">새 채팅</span>
      </button>

      {/* 스크롤 영역: 메뉴 nav + 관리 nav + "대화" 섹션 + 대화 목록 이 함께 스크롤.
          상단(brand+new-chat)·하단(sidebar-user)만 고정. (디자인 chat.html .sidebar-scroll)
          내부에 이미 <nav> landmark 와 개별 포커스 가능한 항목들이 있으므로, 순수 스크롤
          컨테이너에는 role/aria-label/tabindex 를 두지 않는다(landmark 중첩·중복 정지점 방지). */}
      <div className="sidebar-scroll">
      {/* 메뉴 nav (레시피 / 설정) — 레일에서도 아이콘으로 이동 가능.
          레일에서 라벨 span 이 숨겨져도 접근 이름이 남도록 aria-label/title 을 항상 부여한다. */}
      <nav className="sidebar-nav" aria-label="페이지 메뉴">
        <NavLink
          to="/recipes"
          aria-label="레시피 관리"
          title="레시피 관리"
          className={({ isActive }) => `sidebar-nav__item${isActive ? " active" : ""}`}
        >
          <span className="sidebar-nav__icon" aria-hidden>📋</span>
          <span className="sidebar-nav__label">레시피 관리</span>
        </NavLink>
        <NavLink
          to="/history"
          aria-label="히스토리"
          title="히스토리"
          className={({ isActive }) => `sidebar-nav__item${isActive ? " active" : ""}`}
        >
          <span className="sidebar-nav__icon" aria-hidden>📊</span>
          <span className="sidebar-nav__label">히스토리</span>
        </NavLink>
        <NavLink
          to="/settings"
          aria-label="설정"
          title="설정"
          className={({ isActive }) => `sidebar-nav__item${isActive ? " active" : ""}`}
        >
          <span className="sidebar-nav__icon" aria-hidden>⚙️</span>
          <span className="sidebar-nav__label">설정</span>
        </NavLink>
      </nav>

      {/* 관리자 전용 메뉴 (role === ADMIN 일 때만 렌더). FE 게이팅은 UX 힌트이며
          실제 권한은 서버가 강제한다(비-admin URL 직접 접근 시 RequireAdmin 리다이렉트 + API 403). */}
      {user?.role === "ADMIN" && (
        <>
        <div className="sidebar-section-label">관리</div>
        <nav className="sidebar-nav sidebar-nav--admin" aria-label="관리자 메뉴">
          <NavLink
            to="/admin/specs"
            aria-label="스펙 관리 (관리자)"
            title="스펙 관리 (관리자)"
            className={({ isActive }) => `sidebar-nav__item${isActive ? " active" : ""}`}
          >
            <span className="sidebar-nav__icon" aria-hidden>🖥️</span>
            <span className="sidebar-nav__label">스펙 관리</span>
          </NavLink>
          <NavLink
            to="/admin/users"
            aria-label="사용자 관리 (관리자)"
            title="사용자 관리 (관리자)"
            className={({ isActive }) => `sidebar-nav__item${isActive ? " active" : ""}`}
          >
            <span className="sidebar-nav__icon" aria-hidden>👥</span>
            <span className="sidebar-nav__label">사용자 관리</span>
          </NavLink>
        </nav>
        </>
      )}

      {/* "대화" 섹션 헤더 (항상 표시, 레일에서는 CSS 로 숨김) */}
      <div className="sidebar-section-label">대화</div>

      {/* 대화 목록 (레일에서는 CSS 로 숨김). collapsed 전달로 접힘 시 active 자동 스크롤 스킵. */}
      <ConversationSidebar
        conversations={conversations}
        currentId={currentConversationId}
        onSelect={handleSelect}
        onRename={handleRename}
        onDelete={handleDelete}
        collapsed={collapsed}
      />
      </div>{/* /.sidebar-scroll */}

      {/* 회원정보 (하단 고정) + 드롭업 메뉴 */}
      <div className="sidebar-user" ref={userMenuRef}>
        <button
          type="button"
          className="sidebar-user__trigger"
          aria-haspopup="menu"
          aria-expanded={userMenuOpen}
          aria-label={`${user?.name ?? user?.username ?? "사용자"} 계정 메뉴`}
          title={user?.name ?? user?.username ?? "사용자"}
          onClick={() => setUserMenuOpen((v) => !v)}
        >
          <span className="sidebar-user__avatar" aria-hidden>
            {userInitials(user?.name, user?.username)}
          </span>
          <span className="sidebar-user__info">
            <span className="sidebar-user__name">{user?.name ?? user?.username ?? "사용자"}</span>
            {user?.username && <span className="sidebar-user__id">@{user.username}</span>}
          </span>
        </button>

        {userMenuOpen && (
          <div className="sidebar-user__menu" role="menu">
            <button
              type="button"
              role="menuitem"
              className="dropdown-item dropdown-item--danger"
              onClick={() => {
                setUserMenuOpen(false);
                onLogout();
              }}
            >
              🚪 로그아웃
            </button>
          </div>
        )}
      </div>
    </aside>
  );
}
