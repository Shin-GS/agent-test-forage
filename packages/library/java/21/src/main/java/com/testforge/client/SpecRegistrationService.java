package com.testforge.client;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 외부 서버의 OpenAPI 스펙을 ai-test-forge에 등록/heartbeat 하는 서비스.
 *
 * - ApplicationReadyEvent 시점에 /v3/api-docs에서 OpenAPI JSON 수집 → 등록
 * - SHA-256 해시로 변경 감지, heartbeat 시 해시만 전송
 * - 등록 실패해도 앱 기동을 막지 않음 (최대 3회 재시도 후 로그만)
 * - 순수 자바 (Lombok 미사용)
 */
public class SpecRegistrationService {

    private static final Logger log = Logger.getLogger(SpecRegistrationService.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5000L;

    /** 등록 계약(body 구조) 버전. 서버가 이 값으로 파싱을 분기한다. */
    private static final String SCHEMA_VERSION = "1";
    /** 라이브러리 식별 (진단/호환용) */
    private static final String CLIENT_LANG = "java";
    private static final String CLIENT_VERSION = "0.0.1";

    private final TestForgeProperties properties;
    private final RestClient restClient;

    private String lastSpecHash;

    public SpecRegistrationService(TestForgeProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getServerUrl())
                .build();
    }

    /** 앱 기동 완료 시 스펙 등록 (재시도 포함, 실패해도 기동 계속) */
    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            log.warning("AI Test Forge: baseUrl 미설정 — 스펙 등록을 건너뜁니다.");
            return;
        }
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String specJson = fetchOpenApiSpec();
                String hash = computeHash(specJson);
                register(specJson, hash);
                this.lastSpecHash = hash;
                printBanner(specJson);
                return;
            } catch (Exception e) {
                log.warning("AI Test Forge: 스펙 등록 실패 (시도 " + attempt + "/" + MAX_RETRIES + "): " + e.getMessage());
                if (attempt < MAX_RETRIES) {
                    sleep(RETRY_DELAY_MS);
                }
            }
        }
        log.severe("AI Test Forge: 스펙 등록 최종 실패 — " + properties.getName() + " (앱은 정상 기동)");
    }

    /** heartbeat: 해시만 전송, 서버가 resend 요청하면 전체 재등록 */
    public void sendHeartbeat() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            return;
        }
        try {
            String specJson = fetchOpenApiSpec();
            String hash = computeHash(specJson);

            // heartbeat는 경량 요청: schemaVersion으로 서버 파싱만 분기, client는 register에서만 전달
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("schemaVersion", SCHEMA_VERSION);
            body.put("baseUrl", properties.getBaseUrl());
            body.put("specHash", hash);

            Map<?, ?> res = restClient.post()
                    .uri("/api/v1/specs/heartbeat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-TestForge-Token", properties.getRegisterToken())
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String action = res != null ? String.valueOf(res.get("action")) : "none";
            if ("resend".equals(action)) {
                register(specJson, hash);
                this.lastSpecHash = hash;
            } else {
                this.lastSpecHash = hash;
            }
        } catch (Exception e) {
            log.warning("AI Test Forge: heartbeat 실패 — " + e.getMessage());
        }
    }

    /** 전체 스펙 등록 */
    private void register(String specJson, String specHash) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", SCHEMA_VERSION);
        body.put("client", Map.of("lang", CLIENT_LANG, "version", CLIENT_VERSION));
        body.put("name", properties.getName());
        body.put("baseUrl", properties.getBaseUrl());
        body.put("specJson", specJson);
        body.put("specHash", specHash);
        body.put("serviceInfo", buildServiceInfo());
        body.put("jira", Map.of("projectKey", nullToEmpty(properties.getJira().getProjectKey())));
        body.put("authProfiles", buildAuthProfiles());

        restClient.post()
                .uri("/api/v1/specs/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-TestForge-Token", properties.getRegisterToken())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> buildServiceInfo() {
        TestForgeProperties.Service s = properties.getService();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("description", s.getDescription());
        info.put("domain", s.getDomain());
        info.put("capabilities", s.getCapabilities());
        info.put("notes", s.getNotes());
        return info;
    }

    private List<Map<String, String>> buildAuthProfiles() {
        List<Map<String, String>> result = new ArrayList<>();
        for (TestForgeProperties.Auth.Profile p : properties.getAuth().getProfiles()) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("name", p.getName());
            m.put("loginPageUrl", p.getLoginPageUrl());
            result.add(m);
        }
        return result;
    }

    private String fetchOpenApiSpec() {
        String docsUrl = properties.getBaseUrl() + properties.getDocsUrl();
        return RestClient.create().get()
                .uri(docsUrl)
                .retrieve()
                .body(String.class);
    }

    private String computeHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(normalize(content).getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** 정규화: 공백 제거 (필드 순서 정렬은 추후 JSON 파서 도입 시 강화) */
    private String normalize(String json) {
        return json == null ? "" : json.replaceAll("\\s+", "");
    }

    private void printBanner(String specJson) {
        int apiCount = countApis(specJson);
        String banner = System.lineSeparator()
                + "  ┌─────────────────────────────────────────────┐" + System.lineSeparator()
                + "  │  ⚡ AI Test Forge — 스펙 등록 완료            │" + System.lineSeparator()
                + "  │  서비스: " + pad(properties.getName(), 34) + "│" + System.lineSeparator()
                + "  │  API: " + pad(apiCount + "개", 37) + "│" + System.lineSeparator()
                + "  └─────────────────────────────────────────────┘";
        log.info(banner);
    }

    /** OpenAPI JSON의 paths 항목 수를 대략 집계 (정밀 파싱은 서버가 수행) */
    private int countApis(String specJson) {
        if (specJson == null) return 0;
        int idx = specJson.indexOf("\"paths\"");
        if (idx < 0) return 0;
        // paths 이후 나오는 method 키워드 수로 대략 추정
        String after = specJson.substring(idx);
        int count = 0;
        for (String m : new String[]{"\"get\"", "\"post\"", "\"put\"", "\"patch\"", "\"delete\""}) {
            int from = 0;
            while ((from = after.indexOf(m, from)) >= 0) { count++; from += m.length(); }
        }
        return count;
    }

    private String pad(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

    private String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
