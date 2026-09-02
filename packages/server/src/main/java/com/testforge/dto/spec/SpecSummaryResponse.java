package com.testforge.dto.spec;

import com.testforge.dto.common.StatusView;

/**
 * 스펙 관리 목록의 한 행. (admin.md "스펙 관리 > 목록" 소비용)
 *
 * <p>예:
 * <pre>
 * {
 *   "id": 123,
 *   "name": "demo-shop",
 *   "baseUrl": "https://shop.example.com",
 *   "status": { "code": "ACTIVE", "description": "정상" },
 *   "apiCount": 42
 * }
 * </pre>
 */
public record SpecSummaryResponse(
        // 스펙 ID
        Long id,
        // 표시용 서비스 이름
        String name,
        // 서버 도메인 (식별 키)
        String baseUrl,
        // 생명주기 상태 (code + description)
        StatusView status,
        // ACTIVE 엔드포인트 수 (DEPRECATED 제외)
        long apiCount) {
}
