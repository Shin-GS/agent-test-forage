package com.testforge.dto.conversation;

/**
 * session_list_update 이벤트의 data 페이로드(messaging.md): {@code { op, conversation }}.
 *
 * <ul>
 *   <li>{@code op=upsert}: 추가·갱신 통합 (생성/이름변경/서비스변경/읽음/상태변경) — 전체 스냅샷</li>
 *   <li>{@code op=removed}: 삭제 — conversation은 id만</li>
 * </ul>
 */
public record SessionListUpdatePayload(
        // "upsert" | "removed"
        String op,
        // 대화방 목록 한 줄 스냅샷
        ConversationListSnapshot conversation) {

    public static SessionListUpdatePayload upsert(ConversationListSnapshot snapshot) {
        return new SessionListUpdatePayload("upsert", snapshot);
    }

    public static SessionListUpdatePayload removed(Long conversationId) {
        return new SessionListUpdatePayload("removed", ConversationListSnapshot.removed(conversationId));
    }
}
