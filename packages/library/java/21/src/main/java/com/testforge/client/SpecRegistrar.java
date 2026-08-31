package com.testforge.client;

/**
 * 스펙 등록 클라이언트 (스켈레톤).
 *
 * 실제 구현 시:
 * - 앱 기동 시 /v3/api-docs 에서 OpenAPI JSON 수집
 * - ai-test-forge 서버에 POST /api/v1/specs/register
 *   (name, environment, baseUrl, specJson, specHash, authProfiles, serviceInfo, jira)
 * - 30초마다 heartbeat (specHash 비교, 변경 시에만 재전송)
 * - SHA-256 해시로 변경 감지
 *
 * 현재는 뼈대만 존재. 기능 구현은 후속.
 */
public class SpecRegistrar {

    private final TestForgeProperties properties;

    public SpecRegistrar(TestForgeProperties properties) {
        this.properties = properties;
    }

    /** 앱 기동 시 스펙 등록 (미구현) */
    public void register() {
        // TODO: OpenAPI 수집 → 해시 계산 → 서버 등록
        throw new UnsupportedOperationException("스펙 등록 미구현 (뼈대)");
    }

    /** 주기적 heartbeat (미구현) */
    public void heartbeat() {
        // TODO: specHash 비교 → 변경 시 재전송
        throw new UnsupportedOperationException("heartbeat 미구현 (뼈대)");
    }

    public TestForgeProperties getProperties() {
        return properties;
    }
}
