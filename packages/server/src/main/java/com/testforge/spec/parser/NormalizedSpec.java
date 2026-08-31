package com.testforge.spec.parser;

import java.util.List;

/**
 * {@link SpecRegistrationParser}가 생성하는 내부 표준 모델.
 *
 * <p>모든 도메인 로직(upsert, 엔드포인트 분해, 메타 병합)은 이 타입에만 의존한다.
 * 원본 전송 계약({@code RegisterRequest})과 버전별 특성은 파서가 흡수하므로,
 * 도메인은 계약 진화로부터 분리된다. 새 계약 버전이 생기면 이 모델로 매핑하는
 * 파서를 추가하기만 하면 되고, 도메인 변경은 필요 없다.
 */
public record NormalizedSpec(
        String schemaVersion,
        String clientLang,
        String clientVersion,
        String name,
        String baseUrl,
        String specJson,
        String specHash,
        ServiceMeta serviceMeta,
        String jiraProjectKey,
        List<AuthProfileData> authProfiles,
        List<EndpointData> endpoints
) {

    /** 서비스 설명 메타 (관리자 우선 병합에 사용) */
    public record ServiceMeta(
            String description,
            String domain,
            List<String> capabilities,
            String notes
    ) {
    }

    /** 인증 프로필 데이터 */
    public record AuthProfileData(String name, String loginPageUrl) {
    }

    /** OpenAPI paths에서 분해된 단일 엔드포인트 */
    public record EndpointData(
            String httpMethod,
            String path,
            String operationJson,
            String summary,
            boolean excluded,
            boolean confirmRequired,
            String confirmMessage
    ) {
    }
}
