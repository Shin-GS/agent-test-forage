package com.testforge.dto;

/**
 * Heartbeat 요청 본문 (해시만 전송).
 */
public record HeartbeatRequest(
        // 등록 계약 버전 (예: 1)
        String schemaVersion,
        // 서버 도메인. 식별 키 (예: https://shop-api.example.com)
        String baseUrl,
        // 현재 스펙 해시 (서버 저장 해시와 비교, 예: sha256...)
        String specHash
) {
}
