package com.testforge.dto.conversation;

/**
 * session_deleted 이벤트의 data 페이로드(messaging.md): 삭제된 대화방 id.
 *
 * <p>대화방 삭제를 목록 갱신({@code session_list_update})과 분리한 별도 SIGNAL 이벤트다.
 * FE는 이 이벤트를 받으면 (1) 현재 보고 있는 대화방이면 홈으로 이탈 + 안내, (2) 목록을
 * 재조회해 삭제된 방을 제거한다. 모든 탭(같은 사용자 Global SSE)에 전달된다.
 */
public record SessionDeletedPayload(Long conversationId) {

    public static SessionDeletedPayload of(Long conversationId) {
        return new SessionDeletedPayload(conversationId);
    }
}
