package com.testforge.dto.conversation;

/**
 * 대화방 이름 변경 요청.
 */
public record ConversationTitleUpdateRequest(
        // 새 대화 제목
        String title) {
}
