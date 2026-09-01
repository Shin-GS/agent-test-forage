package com.testforge.dto.conversation;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;

/**
 * 대화방 목록의 한 행. (사이드바 대화 목록 소비용)
 * unread는 lastMessageAt > lastReadAt로 서버가 계산한 파생값이다.
 */
public record ConversationSummaryResponse(
        // 대화방 ID
        Long id,
        // 대화 제목
        String title,
        // 대상 서비스(스펙) ID (미지정 시 null)
        Long apiSpecId,
        // 처리 상태 (code + description)
        StatusView status,
        // 마지막 메시지 시각
        LocalDateTime lastMessageAt,
        // 마지막 읽은 시각
        LocalDateTime lastReadAt,
        // 안 읽음 여부 (lastMessageAt > lastReadAt)
        boolean unread,
        // 생성 시각
        LocalDateTime createdAt) {
}
