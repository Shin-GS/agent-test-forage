package com.testforge.ai.connector;

import com.testforge.ai.config.ConfluenceSettings;
import com.testforge.ai.confluence.ConfluenceClient;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.repository.spec.ApiSpecRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 2단계 커넥터: 대화방 서비스에 연결된 <b>Confluence 스페이스</b>의 위키 페이지(요구사항/설계/정책 맥락)를
 * 조회한다 (investigation.md confluence 커넥터 조회 정의). 호출 주체는 BE이며 토큰은 서버 시크릿이다.
 *
 * <p>조회 규칙:
 * <ul>
 *   <li>대상 범위 = {@code apiSpecId} 서비스의 {@code confluenceSpaceKey} space로 한정 (오조회/SSRF 방지)</li>
 *   <li>spaceKey 미연결 → 조회 안 함 + notFound 안내 (스킵 — 카운터는 상위에서 소비)</li>
 *   <li>연동 미설정(토큰/URL/email 없음) → 조회 안 함 + notFound (방어)</li>
 *   <li>검색 성공+페이지 → 최소 필드(title/excerpt)만 재주입 + references(외부 위키 URL)</li>
 *   <li>검색 성공+0건 → notFound 안내</li>
 *   <li>호출 실패 → notFound(일시적 오류) 안내</li>
 * </ul>
 *
 * <p>references의 {@code url}은 외부 URL({@code {baseUrl}/wiki/spaces/{KEY}/pages/{id}})이며, FE는 이를 새 탭으로 연다.
 */
@Component
public class ConfluenceConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceConnector.class);

    /** 소스 식별자 (AI의 investigate source 값과 매칭) */
    public static final String SOURCE = "confluence";

    private final ApiSpecRepository apiSpecRepository;
    private final ConfluenceSettings confluenceSettings;
    private final ConfluenceClient confluenceClient;

    public ConfluenceConnector(ApiSpecRepository apiSpecRepository,
                               ConfluenceSettings confluenceSettings,
                               ConfluenceClient confluenceClient) {
        this.apiSpecRepository = apiSpecRepository;
        this.confluenceSettings = confluenceSettings;
        this.confluenceClient = confluenceClient;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public ConnectorResult query(Long apiSpecId, String query) {
        if (apiSpecId == null) {
            // 상위(InvestigateLoop/hard guard)에서 미지정을 걸러야 하지만, 방어적으로 못 찾음 처리.
            return ConnectorResult.notFound("서비스가 지정되지 않아 Confluence를 조회할 수 없습니다.");
        }

        ApiSpec spec = apiSpecRepository.findByIdAndDeletedAtIsNull(apiSpecId).orElse(null);
        if (spec == null) {
            log.warn("confluence connector: spec not found or deleted, apiSpecId={}", apiSpecId);
            return ConnectorResult.notFound("해당 서비스를 찾을 수 없습니다.");
        }

        String spaceKey = spec.getConfluenceSpaceKey();
        if (spaceKey == null || spaceKey.isBlank()) {
            // spaceKey 미연결: 조회 시도 안 함 (카운터는 상위에서 소비 — 우회 차단).
            return ConnectorResult.notFound("이 서비스에 연결된 Confluence 스페이스가 없습니다.");
        }

        if (!confluenceSettings.hasCredentials()) {
            // 연동 미설정(토큰/URL/email 없음) 방어: 호출하지 않고 안내.
            log.warn("confluence connector: credentials not configured; skipping. apiSpecId={}", apiSpecId);
            return ConnectorResult.notFound("Confluence 연동이 설정되지 않았습니다.");
        }

        ConfluenceClient.SearchResult result = confluenceClient.search(spaceKey, query);
        if (result.failed()) {
            // 401/네트워크/타임아웃 등: 같은 조회 반복 대신 다른 source 시도하도록 안내.
            return ConnectorResult.notFound("Confluence 조회에 실패했습니다(일시적 오류).");
        }
        if (result.pages().isEmpty()) {
            return ConnectorResult.notFound(
                    "스페이스 " + spaceKey + "에서 '" + safeQuery(query) + "' 관련 문서를 찾지 못했습니다.");
        }

        String text = renderText(spaceKey, query, result.pages());
        List<ConnectorResult.Reference> references = new ArrayList<>();
        String base = trimTrailingSlash(confluenceSettings.baseUrl());
        for (ConfluenceClient.ConfluencePage page : result.pages()) {
            String label = page.title() == null || page.title().isBlank()
                    ? ("페이지 " + page.id()) : page.title();
            references.add(new ConnectorResult.Reference(
                    SOURCE, label, base + "/wiki/spaces/" + spaceKey + "/pages/" + page.id()));
        }
        return ConnectorResult.found(text, references);
    }

    /**
     * AI 재주입용 조회 텍스트. 각 페이지를 "title — excerpt"로 나열한다(최소 필드만 — 본문 전체 없음).
     */
    private String renderText(String spaceKey, String query, List<ConfluenceClient.ConfluencePage> pages) {
        StringBuilder sb = new StringBuilder();
        sb.append("Confluence 스페이스: ").append(spaceKey);
        sb.append("\n조회 질의: ").append(query == null ? "" : query);
        sb.append("\n\n관련 문서 (상위 ").append(pages.size()).append("개):");
        for (ConfluenceClient.ConfluencePage page : pages) {
            sb.append("\n- ");
            if (page.title() != null && !page.title().isBlank()) {
                sb.append(page.title());
            } else {
                sb.append("(제목 없음)");
            }
            if (page.excerpt() != null && !page.excerpt().isBlank()) {
                sb.append(" — ").append(page.excerpt());
            }
        }
        return sb.toString();
    }

    private String safeQuery(String query) {
        return query == null ? "" : query;
    }

    private String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
