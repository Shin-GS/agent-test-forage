package com.testforge.dto.conversation;

/**
 * 대화방 대상 서비스(스펙) 변경 요청.
 *
 * <p>{@code apiSpecId}는 nullable이다. null이면 "미지정으로 되돌리기"(서비스 해제)를 의미하고,
 * 값이 있으면 해당 서비스로 지정한다. 값이 있을 경우 서버에서 존재/유효성을 검증한다
 * (미삭제 스펙만 허용).
 */
public record ConversationServiceUpdateRequest(
        // 대상 서비스(스펙) ID. null이면 미지정으로 되돌림
        Long apiSpecId) {
}
