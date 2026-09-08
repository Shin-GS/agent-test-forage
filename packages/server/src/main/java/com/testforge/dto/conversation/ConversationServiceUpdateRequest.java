package com.testforge.dto.conversation;

/**
 * 대화방 대상 서비스(스펙) 변경 요청.
 *
 * <p>{@code apiSpecId}는 nullable이다. null이면 "미지정으로 되돌리기"(서비스 해제)를 의미하고,
 * 값이 있으면 해당 서비스로 지정한다. 값이 있을 경우 서버에서 존재/유효성을 검증한다
 * (미삭제 스펙만 허용).
 *
 * <p>{@code messageId}는 optional(nullable)이다. service_select 카드 클릭으로 서비스를 설정할 때
 * 촉발 카드 파트의 id를 함께 보내면, 서비스 변경 후 그 파트를 CONSUMED로 전이해 새로고침 후
 * 재활성화되지 않게 한다. null이면(기존 호출) 소비 처리 없이 기존과 동일하게 동작한다(하위호환).
 */
public record ConversationServiceUpdateRequest(
        // 대상 서비스(스펙) ID. null이면 미지정으로 되돌림
        Long apiSpecId,
        // 촉발 카드 파트 id (optional). non-null이면 서비스 변경 후 그 파트를 CONSUMED로 전이
        Long messageId) {
}
