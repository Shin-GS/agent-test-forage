package com.testforge.dto.conversation;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;

/**
 * 대화방 상세 응답.
 */
public record ConversationDetailResponse(
        // 대화방 ID
        Long id,
        // 소유자 사용자 ID
        Long userId,
        // 대화 제목
        String title,
        // 대상 서비스(스펙) ID (미지정 시 null)
        Long apiSpecId,
        // 서비스 표시명 (apiSpecId null이거나 스펙 없으면 null)
        String serviceName,
        // 처리 상태 (code + description)
        StatusView status,
        // 마지막 메시지 시각
        LocalDateTime lastMessageAt,
        // 마지막 읽은 시각
        LocalDateTime lastReadAt,
        // 안 읽음 여부 (lastMessageAt > lastReadAt)
        boolean unread,
        // 생성 시각
        LocalDateTime createdAt,
        // 갱신 시각
        LocalDateTime updatedAt) {
}
