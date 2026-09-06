package com.testforge.ai.connector;

import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.enums.EndpointStatus;
import com.testforge.repository.spec.ApiEndpointRepository;
import com.testforge.repository.spec.ApiSpecRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 1단계 커넥터: 대화방 서비스에 연결된 <b>등록 스펙(내부 DB)</b>을 조회한다
 * (investigation.md api_spec 커넥터 조회 정의). 외부 의존이 없어 실동작 검증이 가능하다.
 *
 * <p>조회 규칙:
 * <ul>
 *   <li>대상 범위 = {@code apiSpecId} 스펙의 <b>ACTIVE·미제외</b> 엔드포인트만 (SSRF/오조회 방지)</li>
 *   <li>매칭 = {@code query}로 {@code path}/{@code summary}/{@code operationJson}(description 포함)
 *       부분 매칭(대소문자 무시)</li>
 *   <li>반환 = 매칭 상위 N개(기본 5) 엔드포인트 요약 + 서비스 설명(있으면)</li>
 * </ul>
 *
 * <p>조회 텍스트는 AI에게 role:tool로 재주입되는 <b>데이터</b>다(가드 문구는 InvestigateLoop에서 부착).
 * references의 {@code url}은 사이드 패널 스펙 상세를 여는 내부 식별자
 * ({@code /specs/{apiSpecId}/endpoints/{endpointId}})이며 외부 URL이 아니다.
 */
@Component
public class ApiSpecConnector implements Connector {

    private static final Logger log = LoggerFactory.getLogger(ApiSpecConnector.class);

    /** 소스 식별자 (AI의 investigate source 값과 매칭) */
    public static final String SOURCE = "api_spec";

    /** 토큰 절약: 매칭 엔드포인트 상위 N개만 반환 */
    private static final int MAX_ENDPOINTS = 5;

    /** operationJson에서 매칭용으로 훑는 최대 길이 (거대한 스키마 전체 스캔 방지) */
    private static final int OPERATION_SCAN_LIMIT = 4000;

    private final ApiSpecRepository apiSpecRepository;
    private final ApiEndpointRepository apiEndpointRepository;

    public ApiSpecConnector(ApiSpecRepository apiSpecRepository,
                            ApiEndpointRepository apiEndpointRepository) {
        this.apiSpecRepository = apiSpecRepository;
        this.apiEndpointRepository = apiEndpointRepository;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public ConnectorResult query(Long apiSpecId, String query) {
        if (apiSpecId == null) {
            // 상위(InvestigateLoop/hard guard)에서 미지정을 걸러야 하지만, 방어적으로 못 찾음 처리.
            return ConnectorResult.notFound("서비스가 지정되지 않아 스펙을 조회할 수 없습니다.");
        }

        ApiSpec spec = apiSpecRepository.findByIdAndDeletedAtIsNull(apiSpecId).orElse(null);
        if (spec == null) {
            log.warn("api_spec connector: spec not found or deleted, apiSpecId={}", apiSpecId);
            return ConnectorResult.notFound("해당 서비스 스펙을 찾을 수 없습니다.");
        }

        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        // 조회 범위 한정: 이 스펙의 엔드포인트만. ACTIVE·미제외만 대상으로 (DEPRECATED/제외 제외).
        List<ApiEndpoint> endpoints = apiEndpointRepository.findByApiSpecIdOrderByPathAscHttpMethodAsc(apiSpecId);
        List<ApiEndpoint> matched = new ArrayList<>();
        for (ApiEndpoint e : endpoints) {
            if (e.getStatus() != EndpointStatus.ACTIVE || e.isExcluded()) {
                continue;
            }
            if (needle.isEmpty() || matches(e, needle)) {
                matched.add(e);
                if (matched.size() >= MAX_ENDPOINTS) {
                    break;
                }
            }
        }

        String text = renderText(spec, query, matched);
        if (matched.isEmpty()) {
            // 근거(엔드포인트)를 못 찾으면 서비스 설명만 텍스트로 주되, references는 비운다(억지 인용 금지).
            return ConnectorResult.notFound(text);
        }

        List<ConnectorResult.Reference> references = new ArrayList<>();
        for (ApiEndpoint e : matched) {
            references.add(new ConnectorResult.Reference(
                    SOURCE,
                    e.getHttpMethod() + " " + e.getPath(),
                    "/specs/" + apiSpecId + "/endpoints/" + e.getId()));
        }
        return ConnectorResult.found(text, references);
    }

    /** query가 path/summary/operationJson(description 등)에 부분 포함되는지(소문자 비교) */
    private boolean matches(ApiEndpoint e, String needle) {
        if (contains(e.getPath(), needle) || contains(e.getSummary(), needle)) {
            return true;
        }
        // operationJson에는 description/파라미터명 등이 들어있어 정책 힌트가 될 수 있다. 앞부분만 스캔.
        String op = e.getOperationJson();
        if (op != null) {
            String scan = op.length() > OPERATION_SCAN_LIMIT ? op.substring(0, OPERATION_SCAN_LIMIT) : op;
            return scan.toLowerCase(Locale.ROOT).contains(needle);
        }
        return false;
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * AI 재주입용 조회 텍스트를 만든다. 서비스 설명 + 매칭 엔드포인트 요약(method/path/summary +
     * operationJson 발췌). 토큰 절약을 위해 operationJson은 앞부분만 발췌한다.
     */
    private String renderText(ApiSpec spec, String query, List<ApiEndpoint> matched) {
        StringBuilder sb = new StringBuilder();
        sb.append("서비스: ").append(spec.getName() == null ? "(이름 없음)" : spec.getName());
        if (spec.getServiceDescription() != null && !spec.getServiceDescription().isBlank()) {
            sb.append("\n서비스 설명: ").append(spec.getServiceDescription());
        }
        sb.append("\n조회 질의: ").append(query == null ? "" : query);
        if (matched.isEmpty()) {
            sb.append("\n\n매칭되는 엔드포인트를 찾지 못했습니다.");
            return sb.toString();
        }
        sb.append("\n\n매칭된 엔드포인트 (상위 ").append(matched.size()).append("개):");
        for (ApiEndpoint e : matched) {
            sb.append("\n- ").append(e.getHttpMethod()).append(" ").append(e.getPath());
            if (e.getSummary() != null && !e.getSummary().isBlank()) {
                sb.append("\n  설명: ").append(e.getSummary());
            }
            String op = e.getOperationJson();
            if (op != null && !op.isBlank()) {
                String excerpt = op.length() > 800 ? op.substring(0, 800) + "...(생략)" : op;
                sb.append("\n  스키마 발췌: ").append(excerpt);
            }
        }
        return sb.toString();
    }
}
