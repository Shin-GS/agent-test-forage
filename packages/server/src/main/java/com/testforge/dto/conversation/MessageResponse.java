package com.testforge.dto.conversation;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;

/**
 * 메시지 응답. metadata는 저장된 JSON 문자열을 범용 객체(Map/List)로 파싱해 내린다.
 */
public record MessageResponse(
        // 메시지 ID
        Long id,
        // 소속 대화방 ID
        Long conversationId,
        // 대화방 내 정렬 순서
        Long seq,
        // 작성 주체 (code + description)
        StatusView role,
        // 표현 타입 (code + description)
        StatusView type,
        // 상태 (code + description)
        StatusView status,
        // 본문 (Markdown)
        String content,
        // 타입별 상세 (파싱된 객체, 없으면 null)
        Object metadata,
        // 참조 태그 (없으면 null)
        String referenceId,
        // 생성 시각
        LocalDateTime createdAt) {
}
