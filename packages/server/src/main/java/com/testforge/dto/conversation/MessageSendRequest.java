package com.testforge.dto.conversation;

/**
 * 메시지 전송(동기 접수) 요청. 이번 스코프는 사용자 메시지 저장까지만이며,
 * AI 처리/SSE 발행은 다음 조각에서 다룬다.
 * userId는 인증 도메인 구현 전까지 요청으로 받는다(검증은 TODO).
 */
public record MessageSendRequest(
        // 발신 사용자 ID (인증 전까지 요청으로 수신)
        Long userId,
        // 메시지 본문 (Markdown). 비어 있으면 400
        String content,
        // 참조 태그 (레시피 ID 등, 선택)
        String referenceId,
        // 타입별 상세 메타 (Map/List 등, 선택)
        Object metadata) {
}
