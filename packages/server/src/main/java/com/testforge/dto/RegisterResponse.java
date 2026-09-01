package com.testforge.dto;

/**
 * 스펙 등록 응답: { "specId": 123, "status": "ACTIVE" }
 */
public record RegisterResponse(
        // 등록된 스펙 ID (예: 123)
        Long specId,
        // 등록 후 스펙 상태 (예: ACTIVE)
        String status) {
}
