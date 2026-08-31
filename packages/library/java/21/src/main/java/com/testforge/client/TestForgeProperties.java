package com.testforge.client;

import java.util.List;

/**
 * 외부 서버 application.yml의 ai-test-forge.* 설정 바인딩.
 *
 * <pre>
 * ai-test-forge:
 *   server-url: https://ai-test-forge.example.com
 *   service:
 *     description: "채용 서비스"
 *     domain: "채용"
 *     capabilities: ["회원가입", "공고등록"]
 *   jira:
 *     project-key: "RECRUIT"
 * </pre>
 */
public class TestForgeProperties {

    /** ai-test-forge 서버 URL. CORS 자동 허용 대상. */
    private String serverUrl;

    private Service service = new Service();
    private Jira jira = new Jira();

    public String getServerUrl() { return serverUrl; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }

    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }

    public Jira getJira() { return jira; }
    public void setJira(Jira jira) { this.jira = jira; }

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
}
