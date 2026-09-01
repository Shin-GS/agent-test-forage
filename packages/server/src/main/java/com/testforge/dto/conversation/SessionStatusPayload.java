package com.testforge.dto.conversation;

import com.testforge.dto.common.StatusView;
import com.testforge.entity.conversation.enums.ConversationStatus;

/**
 * session_status 이벤트의 data 페이로드(messaging.md): {@code { sessionId, status }}.
 *
 * <p>대화방 처리 상태 변경을 같은 사용자의 모든 탭에 동기화하기 위한 신호(SIGNAL)다.
 * status는 코드/설명을 함께 내리는 {@link StatusView}로 표현하여 FE가 라벨을 하드코딩하지 않게 한다.
 */
public record SessionStatusPayload(
        // 대화방 ID
        Long sessionId,
        // 처리 상태 (code + description)
        StatusView status) {

    public static SessionStatusPayload of(Long sessionId, ConversationStatus status) {
        return new SessionStatusPayload(sessionId, StatusView.of(status));
    }
}
