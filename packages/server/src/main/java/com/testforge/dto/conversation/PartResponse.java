package com.testforge.dto.conversation;

import com.testforge.dto.common.StatusView;

/**
 * 파트 응답 (턴 DTO의 parts[] 요소, messaging.md 메시지 JSON 구조). payloadJson은 저장된 JSON 문자열을
 * 범용 객체(Map/List)로 파싱해 내린다. 타입에 따라 content(TEXT) / executionId(실행류) /
 * investigationId(조회류) / cardType(카드) / payload(잔여 구조화 데이터)가 채워진다.
 */
public record PartResponse(
        // 파트 ID (턴 내 순서 기준)
        Long id,
        // 파트 타입 (code + description)
        StatusView type,
        // 파트 상태 (code + description)
        StatusView status,
        // TEXT 파트 본문 (Markdown, 없으면 null)
        String content,
        // 실행류 파트가 가리키는 실행 ID (없으면 null)
        Long executionId,
        // 조회류 파트가 가리키는 조회 ID (없으면 null)
        Long investigationId,
        // 카드 세부 유형 (없으면 null)
        String cardType,
        // 타입별 구조화 데이터 (파싱된 객체, 없으면 null)
        Object payload,
        // payload 스키마 버전
        Integer schemaVersion) {
}
