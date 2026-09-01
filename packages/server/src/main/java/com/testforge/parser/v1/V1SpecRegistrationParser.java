package com.testforge.parser.v1;

import com.testforge.common.error.ApiException;
import com.testforge.dto.RegisterRequest;
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

    private static final String EXT_EXCLUDE = "x-testforge-exclude";
    private static final String EXT_CONFIRM = "x-testforge-confirm";
    private static final String EXT_CONFIRM_MESSAGE = "x-testforge-confirm-message";

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
        String jiraProjectKey = raw.jira() != null ? raw.jira().projectKey() : null;
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
                jiraProjectKey,
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
        options.setResolve(false);
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
            excluded = toBoolean(extensions.get(EXT_EXCLUDE));
            confirmRequired = toBoolean(extensions.get(EXT_CONFIRM));
            Object msg = extensions.get(EXT_CONFIRM_MESSAGE);
            if (msg != null) {
                confirmMessage = String.valueOf(msg);
            }
        }

        return new NormalizedSpec.EndpointData(
                method, path, operationJson, summary, excluded, confirmRequired, confirmMessage);
    }

    /** operation 객체를 JSON 문자열로 직렬화 */
    private String serializeOperation(Operation operation) {
        try {
            // OpenAPI 모델이 올바르게 직렬화되도록 swagger-core 자체 매퍼 사용.
            return Json.mapper().writeValueAsString(operation);
        } catch (Exception e) {
            // 비치명적: operation 본문은 힌트일 뿐 식별에 필수는 아님.
            return null;
        }
    }

    /** 확장 필드 값을 boolean으로 안전 변환 */
    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
