package com.testforge.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Confluence 커넥터 호출에 필요한 설정을 읽는 <b>단일 창구</b> (investigation.md confluence 커넥터 조회 정의).
 * 값은 프로퍼티({@code ai-test-forge.confluence.*}, .env/application.yml)에서만 읽는다. 사용자·관리자가 UI에서
 * 바꿀 수 없고 DB에도 저장하지 않는다({@link AiSettings}와 동일 정책).
 *
 * <p>{@code baseUrl}은 서버 설정값으로 고정이라 사용자 입력으로 대상 URL이 바뀌지 않는다(SSRF 방지).
 * {@code apiToken}은 시크릿이며 로그에 남기지 않는다. 세 값({@code baseUrl}/{@code email}/{@code apiToken})
 * 중 하나라도 비어 있으면 Confluence 연동이 비활성 상태로 간주하고({@link #hasCredentials()} false), 커넥터는
 * 조회를 시도하지 않는다.
 *
 * <p>인증은 Atlassian Cloud REST 규격인 <b>Basic 인증</b>({@code email:apiToken}을 base64)으로 한다.
 * (Bearer는 OAuth 2.0/Data Center PAT 전용이라 Cloud API 토큰과 함께 쓰면 401.)
 *
 * @param baseUrl  Atlassian Cloud base URL (예: https://your-domain.atlassian.net). HTTPS 전제
 * @param email    Atlassian 계정 이메일 (Basic 인증 사용자명). 비어 있으면 연동 비활성
 * @param apiToken Atlassian Cloud API 토큰 (시크릿, Basic 인증 비밀번호 자리). 비어 있으면 연동 비활성
 */
@ConfigurationProperties(prefix = "ai-test-forge.confluence")
public record ConfluenceSettings(
        String baseUrl,
        String email,
        String apiToken) {

    /** Confluence 조회 가능 여부 (base-url·email·api-token 셋 다 있으면 true). false면 커넥터가 notFound로 안내 */
    public boolean hasCredentials() {
        return baseUrl != null && !baseUrl.isBlank()
                && email != null && !email.isBlank()
                && apiToken != null && !apiToken.isBlank();
    }
}
