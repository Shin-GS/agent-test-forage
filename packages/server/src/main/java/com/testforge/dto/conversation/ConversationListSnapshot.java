package com.testforge.dto.conversation;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;

/**
 * session_list_update 이벤트의 conversation 스냅샷(messaging.md).
 * 대화방 목록 "한 줄"을 통째로 그리는 데 필요한 필드만 담는다(op=upsert 전체 스냅샷).
 *
 * <p>삭제는 이 스냅샷이 아니라 별도 {@code session_deleted} 이벤트로 처리한다.
 */
public record ConversationListSnapshot(
        // 대화방 ID
        Long id,
        // 대화 제목
        String title,
        // 대상 서비스(스펙) ID (미지정 시 null)
        Long apiSpecId,
        // 서비스 표시명 (serviceDescription > name, apiSpecId가 null이면 null)
        String serviceName,
        // 처리 상태 (code + description)
        StatusView status,
        // 마지막 메시지 시각
        LocalDateTime lastMessageAt,
        // 안 읽음 여부
        boolean unread,
        // 갱신 시각
        LocalDateTime updatedAt) {
}
