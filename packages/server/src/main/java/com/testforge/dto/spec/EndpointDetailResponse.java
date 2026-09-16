package com.testforge.dto.spec;

import com.testforge.dto.common.StatusView;

/**
 * 편집 화면 전용 엔드포인트 단건 조회 응답. 목록 행(SpecDetailResponse.EndpointItem)보다
 * 상세하며, 편집을 위해 원본 operationJson과 출처(source)를 함께 내린다.
 */
public record EndpointDetailResponse(
        // 엔드포인트 ID (PK)
        Long id,
        // 소속 스펙 ID
        Long apiSpecId,
        // HTTP 메서드
        String method,
        // 경로
        String path,
        // API 설명 (매칭 힌트)
        String summary,
        // 생명주기 상태 (code + description)
        StatusView status,
        // 등록 출처 (LIBRARY/MANUAL) — 편집 가능 범위 판단용
        StatusView source,
        // 목록 제외 여부 (@TestForgeExclude)
        boolean excluded,
        // 실행 전 확인 필요 여부 (@TestForgeConfirm)
        boolean confirmRequired,
        // 실행 전 확인 메시지
        String confirmMessage,
        // OpenAPI Operation 원본 JSON (편집 폼 복원용)
        String operationJson
) {
}
