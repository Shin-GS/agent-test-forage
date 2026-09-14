package com.testforge.dto.conversation;

/**
 * 메시지 전송(동기 접수) 요청. 사용자 메시지를 저장하고 AI 처리를 비동기로 트리거한다.
 * userId는 세션에서 도출한다(요청 바디에 없음).
 */
public record MessageSendRequest(
        // 메시지 본문 (Markdown). 비어 있으면 400
        String content,
        // 사용자가 지목한 레시피 ID (사이드 패널 [▶] 실행 등, 선택)
        Long targetRecipeId,
        // 타입별 상세 메타 (Map/List 등, 선택)
        Object metadata) {
}
