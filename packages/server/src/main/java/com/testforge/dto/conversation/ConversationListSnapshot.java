package com.testforge.dto.conversation;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;

/**
 * session_list_update 이벤트의 conversation 스냅샷(messaging.md).
 * 대화방 목록 "한 줄"을 통째로 그리는 데 필요한 필드만 담는다.
 *
 * <p>op=upsert면 전체 스냅샷을, op=removed면 {@link #id}만 채운 스냅샷을 사용한다.
 */
public record ConversationListSnapshot(
        // 대화방 ID
        Long id,
        // 대화 제목 (removed면 null)
        String title,
        // 대상 서비스(스펙) ID (미지정 시 null)
        Long apiSpecId,
        // 처리 상태 (code + description, removed면 null)
        StatusView status,
        // 마지막 메시지 시각
        LocalDateTime lastMessageAt,
        // 안 읽음 여부
        boolean unread,
        // 갱신 시각
        LocalDateTime updatedAt) {

    /** removed 이벤트용: id만 채운 스냅샷. */
    public static ConversationListSnapshot removed(Long id) {
        return new ConversationListSnapshot(id, null, null, null, null, false, null);
    }
}
