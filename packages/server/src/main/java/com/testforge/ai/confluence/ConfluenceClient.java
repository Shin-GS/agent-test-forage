package com.testforge.ai.confluence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.config.ConfluenceSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Atlassian Cloud REST(CQL 검색)로 Confluence 위키 페이지를 조회하는 얇은 HTTP 계층
 * (investigation.md confluence 커넥터 조회 정의). base-url/email/api-token/타임아웃은
 * {@link ConfluenceSettings}에서 읽는다. {@link com.testforge.ai.openai.OpenAiClient}의 RestClient 패턴을 따른다.
 *
 * <p>보안:
 * <ul>
 *   <li>HTTPS 엔드포인트 + {@code Authorization: Basic base64(email:apiToken)} 헤더
 *       (Atlassian Cloud REST 규격). 토큰/인코딩값은 로그에 남기지 않는다.</li>
 *   <li>CQL 인젝션 방어: {@code space = "{KEY}"}를 항상 강제하고, {@code spaceKey}/{@code query}의
 *       위험 문자를 정제한다({@link #sanitizeSpaceKey}/{@link #escapeCqlValue}).</li>
 *   <li>{@code baseUrl}은 설정 고정이라 사용자 입력으로 대상 URL이 바뀌지 않는다(SSRF 방지).</li>
 * </ul>
 *
 * <p>호출측({@link com.testforge.ai.connector.ConfluenceConnector})이 "0건(정상)"과 "호출 실패"를
 * 구분할 수 있도록, 정상 응답은 {@link SearchResult#ok(List)}(0건이면 빈 리스트), 실패(HTTP 오류/네트워크/파싱)는
 * {@link SearchResult#fail()}로 돌려준다(예외를 밖으로 던지지 않음).
 */
@Component
public class ConfluenceClient {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceClient.class);

    /** 검색 상위 N건 (토큰 절약) */
    private static final int MAX_RESULTS = 5;

    private final ConfluenceSettings settings;
    private final RestClient restClient;

    // 자체 매퍼: 응답에 우리가 정의하지 않은 필드가 와도 무시한다(Confluence 응답은 필드가 매우 많다).
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ConfluenceClient(ConfluenceSettings settings) {
        this.settings = settings;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(settings.baseUrl() == null ? "" : settings.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", basicAuthHeader(settings.email(), settings.apiToken()))
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * {@code space = {spaceKey}} 범위에서 {@code query}로 페이지를 검색한다. CQL은 항상 space로
     * 한정하여 임의 스페이스 조회를 막는다(오조회 방지). 정상 응답이면 페이지 목록(0건이면 빈 리스트)을,
     * 호출 실패면 {@link SearchResult#fail()}를 반환한다(예외 미전파).
     *
     * @param spaceKey 대상 Confluence 스페이스 키 (서비스 {@code confluenceSpaceKey})
     * @param query    조회 키워드/질문
     * @return 정상(페이지 목록, 0건 가능) 또는 실패
     */
    public SearchResult search(String spaceKey, String query) {
        String cql = buildCql(spaceKey, query);

        String responseBody;
        try {
            // CQL을 직접 퍼센트 인코딩한다. UriComponentsBuilder.encode()는 값 안의 따옴표(")를
            // 인코딩하지 않아 raw " 가 쿼리에 남고, Atlassian 게이트웨이가 이를 400으로 거부한다.
            // URLEncoder는 공백을 +로 바꾸므로 %20으로 치환해 CQL 파서가 올바르게 파싱하게 한다.
            // 이미 인코딩된 URI를 RestClient가 재인코딩하지 않도록 URI.create로 감싼다(이중 인코딩 방지).
            String encodedCql = URLEncoder.encode(cql, StandardCharsets.UTF_8).replace("+", "%20");
            String base = settings.baseUrl() == null ? "" : trimTrailingSlash(settings.baseUrl());
            URI uri = URI.create(base + "/wiki/rest/api/search?cql=" + encodedCql + "&limit=" + MAX_RESULTS);
            responseBody = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            // HTTP 오류(4xx/5xx). 상태코드를 로깅해 인증 실패(401/403)와 일시 오류(5xx)를 진단 구분한다.
            // 응답 바디는 로깅하지 않는다(민감정보/토큰 반사 방지). 상태코드만 남긴다.
            log.warn("confluence search failed (http {}), spaceKey={}", e.getStatusCode().value(), spaceKey);
            return SearchResult.fail();
        } catch (RestClientException e) {
            // 네트워크/타임아웃 등 상태코드 없는 오류. 토큰은 헤더에만 있고 여기서 로깅하지 않는다(미노출).
            log.warn("confluence search failed (call error), spaceKey={}: {}", spaceKey, e.getMessage());
            return SearchResult.fail();
        }

        try {
            SearchResponse parsed = mapper.readValue(responseBody, SearchResponse.class);
            return SearchResult.ok(toPages(parsed));
        } catch (Exception e) {
            log.warn("confluence search: response parse failed, spaceKey={}", spaceKey, e);
            return SearchResult.fail();
        }
    }

    /**
     * CQL을 만든다: {@code type = page AND space = "{KEY}" AND text ~ "{query}"}.
     * spaceKey는 안전 문자만 남기고, query는 CQL 문자열 이스케이프하여 인젝션을 막는다.
     */
    private String buildCql(String spaceKey, String query) {
        String safeKey = sanitizeSpaceKey(spaceKey);
        String safeQuery = escapeCqlValue(query);
        return "type = page AND space = \"" + safeKey + "\" AND text ~ \"" + safeQuery + "\"";
    }

    /** spaceKey는 영숫자/언더스코어만 허용(Confluence 스페이스 키 규격). 그 외 문자는 제거 */
    private String sanitizeSpaceKey(String spaceKey) {
        if (spaceKey == null) {
            return "";
        }
        return spaceKey.trim().replaceAll("[^A-Za-z0-9_]", "").toUpperCase(Locale.ROOT);
    }

    /**
     * CQL 문자열 리터럴 이스케이프. 따옴표/역슬래시를 이스케이프하고 개행/제어문자는 공백으로 치환하여
     * 문자열 리터럴을 조기 종료하거나 절을 주입하지 못하게 한다(CQL 인젝션 방지).
     */
    private String escapeCqlValue(String query) {
        if (query == null) {
            return "";
        }
        return query.trim()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replaceAll("[\\r\\n\\t]", " ");
    }

    private List<ConfluencePage> toPages(SearchResponse response) {
        List<ConfluencePage> pages = new ArrayList<>();
        if (response == null || response.results() == null) {
            return pages;
        }
        for (ResultDto dto : response.results()) {
            if (dto == null || dto.content() == null || dto.content().id() == null) {
                continue;
            }
            // title은 content.title 우선, 없으면 result.title 폴백. 두 경로 모두 검색 하이라이트
            // 마커가 섞일 수 있어 정리한다(특히 result.title 폴백은 마커 포함 가능).
            String rawTitle = dto.content().title() != null ? dto.content().title() : dto.title();
            pages.add(new ConfluencePage(
                    dto.content().id(), stripHighlight(rawTitle), stripHighlight(dto.excerpt())));
        }
        return pages;
    }

    /**
     * 검색 결과 텍스트(title/excerpt) 정리. Confluence는 매칭 하이라이트 마커
     * ({@code @@@hl@@@ ... @@@endhl@@@})를 넣으므로 제거하고, 개행/연속 공백을 접어
     * 재주입/표시(칩 label)에 적합한 한 줄 텍스트로 만든다.
     */
    private String stripHighlight(String text) {
        if (text == null) {
            return null;
        }
        return text
                .replace("@@@hl@@@", "")
                .replace("@@@endhl@@@", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Atlassian Cloud Basic 인증 헤더를 만든다: {@code Basic base64(email:apiToken)}.
     * email/token이 없어도 클라이언트 생성 자체는 되게 한다(실제 호출은 hasCredentials 가드로 막음).
     * 인코딩된 자격증명은 로그에 남기지 않는다.
     */
    /** baseUrl 끝의 슬래시를 제거해 경로 결합 시 중복 슬래시를 막는다. */
    private String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String basicAuthHeader(String email, String token) {
        String credentials = (email == null ? "" : email) + ":" + (token == null ? "" : token);
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    // ── 결과 래퍼 ──

    /**
     * 검색 결과 래퍼. 호출측이 "0건(정상)"과 "호출 실패"를 구분하도록 {@code failed} 플래그를 둔다.
     * 성공이면 {@code pages}(0건 가능), 실패면 {@code failed=true} + 빈 목록.
     */
    public record SearchResult(List<ConfluencePage> pages, boolean failed) {
        public SearchResult {
            pages = pages == null ? List.of() : List.copyOf(pages);
        }

        public static SearchResult ok(List<ConfluencePage> pages) {
            return new SearchResult(pages, false);
        }

        public static SearchResult fail() {
            return new SearchResult(List.of(), true);
        }
    }

    /** AI 재주입/references용 최소 페이지 정보 (id + title + excerpt만) */
    public record ConfluencePage(String id, String title, String excerpt) {
    }

    // ── 응답 DTO (자체 매퍼로만 사용) ──

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(List<ResultDto> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResultDto(ContentDto content, String title, String excerpt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContentDto(String id, String type, String title) {
        // content 객체의 다른 필드(status/_links 등)는 무시. id/type/title만 사용.
    }
}
