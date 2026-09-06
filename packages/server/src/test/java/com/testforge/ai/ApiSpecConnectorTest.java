package com.testforge.ai;

import com.testforge.ai.connector.ApiSpecConnector;
import com.testforge.ai.connector.ConnectorResult;
import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.enums.EndpointStatus;
import com.testforge.repository.spec.ApiEndpointRepository;
import com.testforge.repository.spec.ApiSpecRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ApiSpecConnector 단위 테스트 (investigation.md api_spec 커넥터 조회 정의). Spring 컨텍스트 없이
 * repository를 목으로 세워 순수 조회 로직을 검증한다: 부분 매칭 / 상위 N / 서비스(스펙) 한정 /
 * ACTIVE·미제외 필터 / 못 찾음.
 */
class ApiSpecConnectorTest {

    private static final Long SPEC_ID = 7L;

    private ApiSpecRepository specRepository;
    private ApiEndpointRepository endpointRepository;
    private ApiSpecConnector connector;

    @BeforeEach
    void setUp() {
        specRepository = mock(ApiSpecRepository.class);
        endpointRepository = mock(ApiEndpointRepository.class);
        connector = new ApiSpecConnector(specRepository, endpointRepository);

        ApiSpec spec = new ApiSpec("https://demo.example.com");
        spec.setName("demo-shop");
        spec.setServiceDescription("커머스 데모 서비스");
        when(specRepository.findByIdAndDeletedAtIsNull(eq(SPEC_ID))).thenReturn(Optional.of(spec));
    }

    @Test
    void source_isApiSpec() {
        assertThat(connector.source()).isEqualTo("api_spec");
    }

    @Test
    void query_partialMatchOnPathSummaryOperation() {
        when(endpointRepository.findByApiSpecIdOrderByPathAscHttpMethodAsc(eq(SPEC_ID)))
                .thenReturn(List.of(
                        endpoint(1L, "POST", "/api/v1/users", "회원가입", "{\"agreementYn\":\"약관 동의\"}"),
                        endpoint(2L, "GET", "/api/v1/orders", "주문 목록", null)));

        // "회원가입"은 첫 엔드포인트 summary에 매칭, 둘째는 미매칭 → 1건.
        ConnectorResult result = connector.query(SPEC_ID, "회원가입");

        assertThat(result.found()).isTrue();
        assertThat(result.references()).hasSize(1);
        assertThat(result.references().get(0).label()).isEqualTo("POST /api/v1/users");
        assertThat(result.references().get(0).source()).isEqualTo("api_spec");
        assertThat(result.references().get(0).url()).isEqualTo("/specs/7/endpoints/1");
        assertThat(result.text()).contains("demo-shop").contains("회원가입");
    }

    @Test
    void query_matchInsideOperationJson() {
        when(endpointRepository.findByApiSpecIdOrderByPathAscHttpMethodAsc(eq(SPEC_ID)))
                .thenReturn(List.of(
                        endpoint(1L, "POST", "/api/v1/users", "가입", "{\"agreementYn\": \"필수 약관 동의\"}")));

        // path/summary엔 없지만 operationJson에 "약관"이 있어 매칭.
        ConnectorResult result = connector.query(SPEC_ID, "약관");

        assertThat(result.found()).isTrue();
        assertThat(result.references()).hasSize(1);
    }

    @Test
    void query_limitsToTopN() {
        List<ApiEndpoint> many = new ArrayList<>();
        for (long i = 1; i <= 8; i++) {
            many.add(endpoint(i, "GET", "/api/v1/item" + i, "item search", null));
        }
        when(endpointRepository.findByApiSpecIdOrderByPathAscHttpMethodAsc(eq(SPEC_ID))).thenReturn(many);

        // 모든 엔드포인트가 "item"에 매칭되지만 상위 5개(MAX_ENDPOINTS)로 제한된다.
        ConnectorResult result = connector.query(SPEC_ID, "item");

        assertThat(result.found()).isTrue();
        assertThat(result.references()).hasSize(5);
    }

    @Test
    void query_excludesNonActiveAndExcludedEndpoints() {
        ApiEndpoint deprecated = endpoint(1L, "GET", "/api/v1/legacy", "legacy signup", null);
        deprecated.setStatus(EndpointStatus.DEPRECATED);
        ApiEndpoint excluded = endpoint(2L, "GET", "/api/v1/hidden", "hidden signup", null);
        excluded.setExcluded(true);
        ApiEndpoint active = endpoint(3L, "POST", "/api/v1/users", "signup", null);
        when(endpointRepository.findByApiSpecIdOrderByPathAscHttpMethodAsc(eq(SPEC_ID)))
                .thenReturn(List.of(deprecated, excluded, active));

        // DEPRECATED/제외 엔드포인트는 매칭에서 빠지고 ACTIVE·미제외만 반환된다.
        ConnectorResult result = connector.query(SPEC_ID, "signup");

        assertThat(result.found()).isTrue();
        assertThat(result.references()).hasSize(1);
        assertThat(result.references().get(0).url()).isEqualTo("/specs/7/endpoints/3");
    }

    @Test
    void query_noMatch_returnsNotFoundWithoutReferences() {
        when(endpointRepository.findByApiSpecIdOrderByPathAscHttpMethodAsc(eq(SPEC_ID)))
                .thenReturn(List.of(endpoint(1L, "GET", "/api/v1/orders", "주문", null)));

        // 매칭 엔드포인트가 없으면 found=false + references 없음(억지 인용 금지). 텍스트는 서비스 설명 포함.
        ConnectorResult result = connector.query(SPEC_ID, "존재하지않는키워드zzz");

        assertThat(result.found()).isFalse();
        assertThat(result.references()).isEmpty();
    }

    @Test
    void query_specNotFound_returnsNotFound() {
        when(specRepository.findByIdAndDeletedAtIsNull(eq(999L))).thenReturn(Optional.empty());

        ConnectorResult result = connector.query(999L, "회원가입");

        assertThat(result.found()).isFalse();
        assertThat(result.references()).isEmpty();
    }

    @Test
    void query_nullSpecId_returnsNotFound() {
        // 서비스 미지정(상위 hard guard 우회 방어): apiSpecId가 null이면 조회 없이 못 찾음.
        ConnectorResult result = connector.query(null, "회원가입");

        assertThat(result.found()).isFalse();
    }

    // ── fixture helper ──

    /** 엔드포인트 fixture. id는 setter가 없어 리플렉션으로 주입한다(reference url 검증용). */
    private ApiEndpoint endpoint(Long id, String method, String path, String summary, String operationJson) {
        ApiEndpoint e = new ApiEndpoint(SPEC_ID, method, path);
        e.setSummary(summary);
        e.setOperationJson(operationJson);
        e.setStatus(EndpointStatus.ACTIVE);
        setId(e, id);
        return e;
    }

    private void setId(ApiEndpoint endpoint, Long id) {
        try {
            Field field = ApiEndpoint.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(endpoint, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to set endpoint id in test", ex);
        }
    }
}
