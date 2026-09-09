package com.testforge.dto.conversation;

/**
 * session_list_update 이벤트의 data 페이로드(messaging.md): {@code { op, conversation }}.
 *
 * <ul>
 *   <li>{@code op=upsert}: 추가·갱신 통합 (생성/이름변경/서비스변경/읽음/상태변경) — 전체 스냅샷</li>
 * </ul>
 *
 * <p>삭제는 이 이벤트가 아니라 별도 {@code session_deleted}({@link SessionDeletedPayload})로 발행한다
 * (관심사 분리 — messaging.md). {@code op}은 현재 {@code upsert}만 사용한다.
 */
public record SessionListUpdatePayload(
        // "upsert" (삭제는 session_deleted 이벤트로 분리)
        String op,
        // 대화방 목록 한 줄 스냅샷
        ConversationListSnapshot conversation) {

    public static SessionListUpdatePayload upsert(ConversationListSnapshot snapshot) {
        return new SessionListUpdatePayload("upsert", snapshot);
    }
}
