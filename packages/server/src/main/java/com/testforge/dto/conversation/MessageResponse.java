package com.testforge.dto.conversation;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 턴 응답 (messaging.md 메시지 JSON 구조). 한 턴(MESSAGE)은 순서 있는 파트(MESSAGE_PART) 배열을 품는다.
 * FE는 턴을 {@code id} 오름차순으로, 각 턴 안 파트를 {@code id} 오름차순으로 렌더한다.
 * {@code message_new}/{@code message_update}의 data는 항상 이 턴 전체 스냅샷(parts 포함)이다.
 */
public record MessageResponse(
        // 턴 ID (정렬·커서 기준)
        Long id,
        // 소속 대화방 ID
        Long conversationId,
        // 작성 주체 (code + description)
        StatusView role,
        // 턴 전체 상태 (code + description)
        StatusView status,
        // 참조 태그 (없으면 null)
        String referenceId,
        // 낙관적 UI 매칭용 클라이언트 메시지 ID (없으면 null)
        String clientMessageId,
        // 생성 시각
        LocalDateTime createdAt,
        // 순서 있는 파트 배열 (id 오름차순)
        List<PartResponse> parts) {
}
