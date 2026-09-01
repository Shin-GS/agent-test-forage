package com.testforge.dto.conversation;

/**
 * 첫 메시지로 대화방을 생성하는 요청. 빈 대화방을 원천 차단하기 위해
 * 대화방 생성과 첫 사용자 메시지 저장을 한 트랜잭션으로 처리한다.
 *
 * <p>제목은 요청 title이 있으면(레시피명 등) 그대로 사용하고, 없으면 첫 메시지
 * content 앞부분으로 임시 제목을 파생한다(추후 AI 요약으로 교체 — 다음 조각).
 * userId는 인증 도메인 구현 전까지 요청으로 받는다(검증은 TODO).
 */
public record ConversationStartRequest(
        // 소유자 사용자 ID (인증 전까지 요청으로 수신)
        Long userId,
        // 첫 메시지 본문 (Markdown). 비어 있으면 400
        String content,
        // 대상 서비스(스펙) ID (선택)
        Long apiSpecId,
        // 대화 제목 (선택 — 레시피로 시작 시 레시피명 등). 없으면 content로 파생
        String title,
        // 참조 태그 (레시피 ID 등, 선택)
        String referenceId,
        // 타입별 상세 메타 (Map/List 등, 선택)
        Object metadata) {
}
