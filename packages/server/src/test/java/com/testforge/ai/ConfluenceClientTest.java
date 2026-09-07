package com.testforge.ai;

import com.testforge.ai.config.ConfluenceSettings;
import com.testforge.ai.confluence.ConfluenceClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConfluenceClient의 CQL 조립/정제 로직 단위 테스트 (investigation.md 보안 — CQL 인젝션 방지). 실제 HTTP
 * 호출은 하지 않고, private CQL 빌더를 리플렉션으로 호출해 space 강제·따옴표 이스케이프·spaceKey
 * 정제를 검증한다. 결과 래퍼({@link ConfluenceClient.SearchResult})의 성공/실패 구분 의미도 함께 확인한다.
 */
class ConfluenceClientTest {

    private final ConfluenceClient client =
            new ConfluenceClient(new ConfluenceSettings("https://demo.atlassian.net", "user@example.com", "token"));

    @Test
    void buildCql_alwaysScopesToSpace() throws Exception {
        String cql = invokeBuildCql("BT", "회원가입");

        // 항상 type = page + space = "{KEY}"로 한정 + text ~ "{query}".
        assertThat(cql).isEqualTo("type = page AND space = \"BT\" AND text ~ \"회원가입\"");
    }

    @Test
    void buildCql_escapesQuoteInQuery_preventsInjection() throws Exception {
        // query에 따옴표/절 주입 시도가 있어도 문자열 리터럴로 이스케이프되어 절이 조기 종료되지 않는다.
        String cql = invokeBuildCql("BT", "x\" OR space = \"SECRET");

        assertThat(cql).contains("space = \"BT\" AND text ~ \"");
        // 원본 따옴표는 \" 로 이스케이프되어 남는다(리터럴 안).
        assertThat(cql).contains("x\\\" OR space = \\\"SECRET");
        // space 절은 여전히 BT 하나뿐 — 두 번째 space 절이 유효 절로 새지 않는다.
        assertThat(cql.indexOf("space = \"BT\"")).isPositive();
    }

    @Test
    void buildCql_stripsNewlinesAndControlChars() throws Exception {
        String cql = invokeBuildCql("BT", "line1\nline2\tend");

        assertThat(cql).doesNotContain("\n").doesNotContain("\t");
        assertThat(cql).contains("line1 line2 end");
    }

    @Test
    void buildCql_sanitizesSpaceKey() throws Exception {
        // spaceKey에 위험 문자가 섞여도 영숫자/언더스코어만 남기고 대문자화한다.
        String cql = invokeBuildCql("b t\" OR", "회원가입");

        assertThat(cql).contains("space = \"BTOR\"");
    }

    @Test
    void searchResult_okAndFailed_distinguish() {
        assertThat(ConfluenceClient.SearchResult.fail().failed()).isTrue();
        assertThat(ConfluenceClient.SearchResult.fail().pages()).isEmpty();
        assertThat(ConfluenceClient.SearchResult.ok(java.util.List.of()).failed()).isFalse();
    }

    @Test
    void stripHighlight_stripsMarkersAndFoldsWhitespace() throws Exception {
        String cleaned = invokeStripHighlight("회원가입 @@@hl@@@약관@@@endhl@@@   동의\n필드");

        // 하이라이트 마커 제거 + 연속 공백/개행 접힘.
        assertThat(cleaned).isEqualTo("회원가입 약관 동의 필드");
    }

    @Test
    void stripHighlight_nullSafe() throws Exception {
        assertThat(invokeStripHighlight(null)).isNull();
    }

    private String invokeBuildCql(String spaceKey, String query) throws Exception {
        Method m = ConfluenceClient.class.getDeclaredMethod("buildCql", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(client, spaceKey, query);
    }

    private String invokeStripHighlight(String text) throws Exception {
        Method m = ConfluenceClient.class.getDeclaredMethod("stripHighlight", String.class);
        m.setAccessible(true);
        return (String) m.invoke(client, text);
    }
}
