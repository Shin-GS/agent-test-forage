package com.testforge.dto.spec;

/**
 * 엔드포인트 복제 요청. 사본은 항상 MANUAL 출처로 생성된다.
 * (method, path) 유니크 충돌을 피하기 위해 새 path를 지정할 수 있으며,
 * 비어 있으면 서버가 원본 path에 suffix를 붙여 생성한다.
 */
public record DuplicateEndpointRequest(
        // 사본에 사용할 새 경로 (선택). 비면 원본 path + "-copy" 규칙 적용
        String path
) {
}
