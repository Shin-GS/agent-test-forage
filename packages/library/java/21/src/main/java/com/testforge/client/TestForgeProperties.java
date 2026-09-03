package com.testforge.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 외부 서버 application.yml의 ai-test-forge.* 설정 바인딩.
 *
 * <pre>
 * ai-test-forge:
 *   enabled: true
 *   server-url: https://ai-test-forge.example.com
 *   register-token: ${TESTFORGE_TOKEN}
 *   name: "demo-shop"
 *   base-url: https://shop-api.example.com
 *   docs-url: /v3/api-docs
 *   service:
 *     description: "온라인 쇼핑몰 API"
 *     domain: "커머스"
 *     capabilities: ["회원가입", "상품등록"]
 *     notes: "스테이징"
 *   jira:
 *     project-key: "SHOP"
 *   auth:
 *     profiles:
 *       - name: "일반"
 *         login-page-url: "https://.../login"
 * </pre>
 */
@ConfigurationProperties(prefix = "ai-test-forge")
public class TestForgeProperties {

    /** 라이브러리 활성화 여부 (기본 true) */
    private boolean enabled = true;

    /** ai-test-forge 서버 URL. 스펙 등록 대상 + CORS 자동 허용 대상 */
    private String serverUrl;

    /**
     * 레시피를 실행하는 웹 앱(FE) origin 목록. CORS 자동 허용 대상에 추가된다.
     * 레시피 API 는 FE 브라우저가 이 서버로 직접 호출하므로, FE origin 을 허용해야 한다
     * (serverUrl 은 BE origin 이라 FE 호출을 커버하지 못한다).
     * 예: ["http://localhost:5173"]
     */
    private List<String> webOrigins = new ArrayList<>();

    /** 등록 보안 토큰 (X-TestForge-Token 헤더로 전송) */
    private String registerToken;

    /** 사용자에게 보이는 서비스 이름 (AI/사이드바 노출) */
    private String name;

    /** 이 서버의 baseUrl. 시스템 식별 키. 미지정 시 등록 스킵 */
    private String baseUrl;

    /** OpenAPI 문서 경로 (기본 /v3/api-docs) */
    private String docsUrl = "/v3/api-docs";

    private Service service = new Service();
    private Jira jira = new Jira();
    private Auth auth = new Auth();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
    public List<String> getWebOrigins() { return webOrigins; }
    public void setWebOrigins(List<String> webOrigins) { this.webOrigins = webOrigins; }
    public String getRegisterToken() { return registerToken; }
    public void setRegisterToken(String registerToken) { this.registerToken = registerToken; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getDocsUrl() { return docsUrl; }
    public void setDocsUrl(String docsUrl) { this.docsUrl = docsUrl; }
    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }
    public Jira getJira() { return jira; }
    public void setJira(Jira jira) { this.jira = jira; }
    public Auth getAuth() { return auth; }
    public void setAuth(Auth auth) { this.auth = auth; }

    /** 서비스 설명 메타 (AI 매칭 컨텍스트) */
    public static class Service {
        private String description;
        private String domain;
        private List<String> capabilities;
        private String notes;

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
        public List<String> getCapabilities() { return capabilities; }
        public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    /** 정보 조회용 Jira 프로젝트 연결 (프로젝트 키만. 토큰은 ai-test-forge 서버 관리) */
    public static class Jira {
        private String projectKey;

        public String getProjectKey() { return projectKey; }
        public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    }

    /** 인증 프로필 목록 (401/403 시 안내할 로그인 URL) */
    public static class Auth {
        private List<Profile> profiles = new ArrayList<>();

        public List<Profile> getProfiles() { return profiles; }
        public void setProfiles(List<Profile> profiles) { this.profiles = profiles; }

        public static class Profile {
            private String name;
            private String loginPageUrl;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getLoginPageUrl() { return loginPageUrl; }
            public void setLoginPageUrl(String loginPageUrl) { this.loginPageUrl = loginPageUrl; }
        }
    }
}
