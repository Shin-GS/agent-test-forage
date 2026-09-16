package com.testforge.parser.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.testforge.common.error.ApiException;
import com.testforge.dto.spec.RegisterRequest;
import com.testforge.parser.NormalizedSpec;
import com.testforge.parser.SpecRegistrationParser;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 계약 schemaVersion "1"용 파서.
 *
 * <p>v1 전송 본문을 {@link NormalizedSpec}으로 매핑하고, swagger-parser로
 * OpenAPI {@code specJson}(3.0/3.1)을 개별 엔드포인트로 분해한다.
 * TestForge 힌트는 operation 확장 필드({@code x-testforge-exclude},
 * {@code x-testforge-confirm}, {@code x-testforge-confirm-message})에서 읽으며,
 * 없으면 false / null로 기본 처리한다.
 */
@Component
public class V1SpecRegistrationParser implements SpecRegistrationParser {

    private static final String SCHEMA_VERSION = "1";

    // 라이브러리 TestForgeOperationCustomizer가 만드는 확장 키와 정확히 일치해야 한다.
    // exclude: reason 없으면 true, 있으면 {reason: ...}. confirm: {message: ...} 객체(존재 = 확인 필요).
    private static final String EXT_EXCLUDE = "x-test-forge-exclude";
    private static final String EXT_CONFIRM = "x-test-forge-confirm";

    @Override
    public boolean supports(String schemaVersion) {
        return SCHEMA_VERSION.equals(schemaVersion);
    }

    @Override
    public NormalizedSpec parse(RegisterRequest raw) {
        if (raw.baseUrl() == null || raw.baseUrl().isBlank()) {
            throw ApiException.invalidRequest("baseUrl is required");
        }
        if (raw.specJson() == null || raw.specJson().isBlank()) {
            throw ApiException.invalidSpec("specJson is required");
        }

        String clientLang = raw.client() != null ? raw.client().lang() : null;
        String clientVersion = raw.client() != null ? raw.client().version() : null;

        NormalizedSpec.ServiceMeta meta = mapServiceMeta(raw.serviceInfo());
        String confluenceSpaceKey = raw.confluence() != null ? raw.confluence().spaceKey() : null;
        List<NormalizedSpec.AuthProfileData> authProfiles = mapAuthProfiles(raw);
        List<NormalizedSpec.EndpointData> endpoints = decomposeEndpoints(raw.specJson());

        return new NormalizedSpec(
                SCHEMA_VERSION,
                clientLang,
                clientVersion,
                raw.name(),
                raw.baseUrl(),
                raw.specJson(),
                raw.specHash(),
                meta,
                confluenceSpaceKey,
                authProfiles,
                endpoints
        );
    }

    /** serviceInfo → 내부 ServiceMeta 매핑 (null 안전) */
    private NormalizedSpec.ServiceMeta mapServiceMeta(RegisterRequest.ServiceInfo info) {
        if (info == null) {
            return new NormalizedSpec.ServiceMeta(null, null, List.of(), null);
        }
        List<String> capabilities = info.capabilities() != null ? info.capabilities() : List.of();
        return new NormalizedSpec.ServiceMeta(
                info.description(), info.domain(), capabilities, info.notes());
    }

    /** 요청의 authProfiles → 내부 AuthProfileData 목록 매핑 */
    private List<NormalizedSpec.AuthProfileData> mapAuthProfiles(RegisterRequest raw) {
        List<NormalizedSpec.AuthProfileData> result = new ArrayList<>();
        if (raw.authProfiles() != null) {
            for (RegisterRequest.AuthProfileDto p : raw.authProfiles()) {
                result.add(new NormalizedSpec.AuthProfileData(p.name(), p.loginPageUrl()));
            }
        }
        return result;
    }

    /** specJson의 paths를 순회하여 method+path 단위 엔드포인트로 분해 */
    private List<NormalizedSpec.EndpointData> decomposeEndpoints(String specJson) {
        ParseOptions options = new ParseOptions();
        // $ref($/components/schemas/...)를 실제 properties로 인라인 해석한다.
        // resolve: 내부 참조 해석 활성화. resolveFully: operation 내부 스키마까지 완전히 펼침.
        // swagger-parser는 순환 참조(자기참조/상호참조)를 감지해 해당 지점만 $ref로 남기므로 무한 팽창하지 않는다.
        // 외부 URL 참조는 이 프로젝트 스펙엔 없고, resolveFully는 내부 components 위주로 동작한다.
        options.setResolve(true);
        options.setResolveFully(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(specJson, null, options);
        OpenAPI openApi = result.getOpenAPI();
        if (openApi == null) {
            throw ApiException.invalidSpec("Unable to parse OpenAPI document");
        }
        if (openApi.getPaths() == null || openApi.getPaths().isEmpty()) {
            return List.of();
        }

        List<NormalizedSpec.EndpointData> endpoints = new ArrayList<>();
        for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();
            if (pathItem == null) {
                continue;
            }
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : pathItem.readOperationsMap().entrySet()) {
                String method = opEntry.getKey().name();
                Operation operation = opEntry.getValue();
                endpoints.add(toEndpointData(method, path, operation));
            }
        }
        return endpoints;
    }

    /** 단일 operation을 EndpointData로 변환 (TestForge 확장 힌트 포함) */
    private NormalizedSpec.EndpointData toEndpointData(String method, String path, Operation operation) {
        String summary = operation.getSummary();
        String operationJson = serializeOperation(operation);

        boolean excluded = false;
        boolean confirmRequired = false;
        String confirmMessage = null;

        Map<String, Object> extensions = operation.getExtensions();
        if (extensions != null) {
            // exclude: true(boolean) 또는 {reason: ...}(객체) 둘 다 "제외"로 본다.
            excluded = isPresentFlag(extensions.get(EXT_EXCLUDE));
            // confirm: {message: ...} 객체의 존재 자체가 "확인 필요"를 의미한다.
            Object confirm = extensions.get(EXT_CONFIRM);
            if (confirm != null) {
                confirmRequired = true;
                confirmMessage = extractMessage(confirm);
            }
        }

        return new NormalizedSpec.EndpointData(
                method, path, operationJson, summary, excluded, confirmRequired, confirmMessage);
    }

    /**
     * operation 객체를 JSON 문자열로 직렬화한다.
     *
     * <p>$ref 인라인 후에도 스키마 구조(properties/type/required)만 저장하고 example 값은 제거한다.
     * 인라인/직렬화가 특정 operation에서 실패해도 등록 전체를 실패시키지 않도록 조용히 null 폴백한다.
     */
    private String serializeOperation(Operation operation) {
        try {
            // OpenAPI 모델이 올바르게 직렬화되도록 swagger-core 자체 매퍼 사용.
            JsonNode tree = Json.mapper().valueToTree(operation);
            stripExamples(tree);
            return Json.mapper().writeValueAsString(tree);
        } catch (Exception e) {
            // 비치명적: operation 본문은 힌트일 뿐 식별에 필수는 아님.
            return null;
        }
    }

    /**
     * JSON 트리에서 example/examples 필드를 재귀적으로 제거한다.
     * resolveFully 이후 스키마에 딸려온 예시 값을 저장하지 않기 위함.
     */
    private void stripExamples(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            obj.remove("example");
            obj.remove("examples");
            Iterator<JsonNode> children = obj.elements();
            while (children.hasNext()) {
                stripExamples(children.next());
            }
        } else if (node instanceof ArrayNode arr) {
            for (JsonNode child : arr) {
                stripExamples(child);
            }
        }
    }

    /**
     * 플래그성 확장 값 해석. {@code true}(boolean)이거나 값이 존재하는 객체({reason:...} 등)면
     * "설정됨"으로 본다. 명시적 {@code false}만 미설정으로 취급한다.
     */
    private boolean isPresentFlag(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        // 객체/문자열 등 값이 있으면 설정된 것으로 본다 (예: {reason: ...})
        return true;
    }

    /** confirm 확장에서 message 추출. {@code {message: ...}} 객체 형태를 기대하되, 없으면 null. */
    private String extractMessage(Object confirm) {
        if (confirm instanceof Map<?, ?> map) {
            Object msg = map.get("message");
            return msg != null ? String.valueOf(msg) : null;
        }
        return null;
    }
}
