package com.testforge.ai;

import com.testforge.ai.config.ConfluenceSettings;
import com.testforge.ai.confluence.ConfluenceClient;
import com.testforge.ai.connector.ConfluenceConnector;
import com.testforge.ai.connector.ConnectorResult;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.repository.spec.ApiSpecRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ConfluenceConnector 단위 테스트 (investigation.md confluence 커넥터 조회 정의). Spring 컨텍스트 없이
 * repository/settings/client를 목으로 세워 순수 라우팅·결과 매핑을 검증한다: spaceKey 미연결 / 연동 미설정 /
 * 검색 성공(references) / 0건 / 호출 실패. 실제 Confluence 호출은 하지 않는다(ConfluenceClient를 목으로 대체).
 */
class ConfluenceConnectorTest {

    private static final Long SPEC_ID = 7L;
    private static final String SPACE_KEY = "BT";
    private static final String BASE_URL = "https://demo.atlassian.net";

    private ApiSpecRepository specRepository;
    private ConfluenceSettings settings;
    private ConfluenceClient client;
    private ConfluenceConnector connector;

    @BeforeEach
    void setUp() {
        specRepository = mock(ApiSpecRepository.class);
        client = mock(ConfluenceClient.class);
        // 기본: 연동 설정됨. hasCredentials()는 record의 실제 로직을 그대로 쓴다(목 아님).
        settings = new ConfluenceSettings(BASE_URL, "user@example.com", "secret-token");
        connector = new ConfluenceConnector(specRepository, settings, client);

        when(specRepository.findByIdAndDeletedAtIsNull(eq(SPEC_ID)))
                .thenReturn(Optional.of(specWithSpaceKey(SPACE_KEY)));
    }

    @Test
    void source_isConfluence() {
        assertThat(connector.source()).isEqualTo("confluence");
    }

    @Test
    void query_nullSpecId_returnsNotFound_withoutClientCall() {
        ConnectorResult result = connector.query(null, "회원가입");

        assertThat(result.found()).isFalse();
        verify(client, never()).search(any(), any());
    }

    @Test
    void query_specNotFound_returnsNotFound() {
        when(specRepository.findByIdAndDeletedAtIsNull(eq(999L))).thenReturn(Optional.empty());

        ConnectorResult result = connector.query(999L, "회원가입");

        assertThat(result.found()).isFalse();
        verify(client, never()).search(any(), any());
    }

    @Test
    void query_noSpaceKey_returnsNotFound_withoutClientCall() {
        when(specRepository.findByIdAndDeletedAtIsNull(eq(SPEC_ID)))
                .thenReturn(Optional.of(specWithSpaceKey(null)));

        // spaceKey 미연결: 조회 시도 안 함 → notFound 안내.
        ConnectorResult result = connector.query(SPEC_ID, "회원가입");

        assertThat(result.found()).isFalse();
        assertThat(result.text()).contains("Confluence 스페이스가 없습니다");
        verify(client, never()).search(any(), any());
    }

    @Test
    void query_noCredentials_returnsNotFound_withoutClientCall() {
        // 연동 미설정(토큰/URL/email 비어 있음) 방어: 호출하지 않고 안내.
        ConfluenceConnector unconfigured =
                new ConfluenceConnector(specRepository, new ConfluenceSettings("", "", ""), client);

        ConnectorResult result = unconfigured.query(SPEC_ID, "회원가입");

        assertThat(result.found()).isFalse();
        assertThat(result.text()).contains("설정되지 않았습니다");
        verify(client, never()).search(any(), any());
    }

    @Test
    void query_emailMissing_returnsNotFound_withoutClientCall() {
        // Basic 인증은 email 필수 — email만 비어도 연동 비활성으로 간주하고 호출하지 않는다.
        ConfluenceConnector emailless = new ConfluenceConnector(
                specRepository, new ConfluenceSettings(BASE_URL, "", "secret-token"), client);

        ConnectorResult result = emailless.query(SPEC_ID, "회원가입");

        assertThat(result.found()).isFalse();
        assertThat(result.text()).contains("설정되지 않았습니다");
        verify(client, never()).search(any(), any());
    }

    @Test
    void query_success_returnsFoundWithReferences() {
        when(client.search(eq(SPACE_KEY), eq("회원가입"))).thenReturn(
                ConfluenceClient.SearchResult.ok(List.of(
                        new ConfluenceClient.ConfluencePage("608141425", "회원가입 정책 v0.1", "약관 동의 필드 정의"),
                        new ConfluenceClient.ConfluencePage("608141500", "회원가입 API 설계", "엔드포인트 명세"))));

        ConnectorResult result = connector.query(SPEC_ID, "회원가입");

        assertThat(result.found()).isTrue();
        assertThat(result.references()).hasSize(2);
        ConnectorResult.Reference first = result.references().get(0);
        assertThat(first.source()).isEqualTo("confluence");
        assertThat(first.label()).isEqualTo("회원가입 정책 v0.1");
        assertThat(first.url()).isEqualTo("https://demo.atlassian.net/wiki/spaces/BT/pages/608141425");
        // 재주입 텍스트에 title/excerpt가 최소 필드로 담긴다.
        assertThat(result.text())
                .contains("회원가입 정책 v0.1")
                .contains("약관 동의 필드 정의");
    }

    @Test
    void query_zeroResults_returnsNotFound() {
        when(client.search(eq(SPACE_KEY), any())).thenReturn(ConfluenceClient.SearchResult.ok(List.of()));

        ConnectorResult result = connector.query(SPEC_ID, "존재하지않는키워드");

        assertThat(result.found()).isFalse();
        assertThat(result.references()).isEmpty();
        assertThat(result.text()).contains(SPACE_KEY).contains("찾지 못했습니다");
    }

    @Test
    void query_callFailure_returnsNotFound() {
        when(client.search(eq(SPACE_KEY), any())).thenReturn(ConfluenceClient.SearchResult.fail());

        ConnectorResult result = connector.query(SPEC_ID, "회원가입");

        assertThat(result.found()).isFalse();
        assertThat(result.references()).isEmpty();
        assertThat(result.text()).contains("실패했습니다");
    }

    @Test
    void query_baseUrlWithTrailingSlash_buildsCleanPageUrl() {
        ConfluenceConnector withSlash = new ConfluenceConnector(
                specRepository, new ConfluenceSettings(BASE_URL + "/", "user@example.com", "secret-token"), client);
        when(client.search(eq(SPACE_KEY), any())).thenReturn(
                ConfluenceClient.SearchResult.ok(List.of(new ConfluenceClient.ConfluencePage("123", "제목", "요약"))));

        ConnectorResult result = withSlash.query(SPEC_ID, "회원가입");

        // 트레일링 슬래시가 있어도 page URL에 중복 슬래시가 생기지 않는다.
        assertThat(result.references().get(0).url())
                .isEqualTo("https://demo.atlassian.net/wiki/spaces/BT/pages/123");
    }

    // ── fixture helper ──

    private ApiSpec specWithSpaceKey(String spaceKey) {
        ApiSpec spec = new ApiSpec("https://demo.example.com");
        spec.setName("demo-shop");
        spec.setConfluenceSpaceKey(spaceKey);
        return spec;
    }
}
