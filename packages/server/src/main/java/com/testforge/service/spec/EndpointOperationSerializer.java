package com.testforge.service.spec;

import com.testforge.common.error.ApiException;
import com.testforge.dto.spec.ManualEndpointRequest;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 관리자 폼 입력({@link ManualEndpointRequest})을 OpenAPI Operation JSON으로 직렬화한다.
 *
 * <p>라이브러리 파서({@code V1SpecRegistrationParser})가 만드는
 * {@code Json.mapper().writeValueAsString(Operation)}와 동일한 형식을 목표로 한다.
 * 따라서 swagger-core 모델(Operation/Parameter/RequestBody/ApiResponses)을 직접 조립한다.
 * 표준 위치를 지킨다: 요청 헤더는 {@code parameters[in:header]}, 응답 헤더는
 * {@code responses.{code}.headers}. 예시값(example)은 넣지 않는다.
 *
 * <p>GET/DELETE는 요청 바디를 저장하지 않는다(requestBody 미포함).
 */
@Component
public class EndpointOperationSerializer {

    /**
     * 폼 입력을 Operation JSON 문자열로 직렬화한다. method/path는 엔티티 컬럼으로 별도 저장되므로
     * operation 본문에는 넣지 않는다(파서 산출물과 동일: operation은 method/path를 담지 않음).
     *
     * @param request       폼 입력
     * @param bodyAllowed   요청 바디 허용 여부 (GET/DELETE면 false)
     * @return 유효성 검증을 통과한 Operation JSON
     */
    public String serialize(ManualEndpointRequest request, boolean bodyAllowed) {
        Operation operation = new Operation();
        if (request.summary() != null && !request.summary().isBlank()) {
            operation.setSummary(request.summary());
        }

        addParameters(operation, request.parameters());
        addHeaders(operation, request.headers());
        if (bodyAllowed) {
            addRequestBody(operation, request.requestBody());
        }
        addResponses(operation, request.responses());

        try {
            return Json.mapper().writeValueAsString(operation);
        } catch (Exception e) {
            // 조립한 모델이 직렬화되지 않는 경우는 입력 문제로 간주한다(내부 정보 미노출).
            throw ApiException.invalidRequest("Failed to serialize endpoint operation");
        }
    }

    /** 경로/쿼리 파라미터 추가 (in: path → required 강제, query → 입력값) */
    private void addParameters(Operation operation, List<ManualEndpointRequest.ParameterInput> parameters) {
        if (parameters == null) {
            return;
        }
        for (ManualEndpointRequest.ParameterInput p : parameters) {
            if (p == null || p.name() == null || p.name().isBlank()) {
                continue;
            }
            String in = normalizeParamIn(p.in());
            Parameter parameter = new Parameter()
                    .name(p.name())
                    .in(in)
                    .schema(schemaOf(p.type()));
            if (p.description() != null && !p.description().isBlank()) {
                parameter.setDescription(p.description());
            }
            // path 파라미터는 OpenAPI 규격상 항상 required=true
            boolean required = "path".equals(in) || Boolean.TRUE.equals(p.required());
            parameter.setRequired(required);
            operation.addParametersItem(parameter);
        }
    }

    /** 요청 헤더 추가 (parameters[in:header]) */
    private void addHeaders(Operation operation, List<ManualEndpointRequest.HeaderInput> headers) {
        if (headers == null) {
            return;
        }
        for (ManualEndpointRequest.HeaderInput h : headers) {
            if (h == null || h.name() == null || h.name().isBlank()) {
                continue;
            }
            Parameter parameter = new Parameter()
                    .name(h.name())
                    .in("header")
                    .schema(schemaOf("string"));
            if (h.description() != null && !h.description().isBlank()) {
                parameter.setDescription(h.description());
            }
            parameter.setRequired(Boolean.TRUE.equals(h.required()));
            operation.addParametersItem(parameter);
        }
    }

    /** 요청 바디 추가 (object schema + contentType). 필드가 없으면 바디 자체를 넣지 않는다. */
    private void addRequestBody(Operation operation, ManualEndpointRequest.RequestBodyInput body) {
        if (body == null || body.fields() == null || body.fields().isEmpty()) {
            return;
        }
        String contentType = (body.contentType() == null || body.contentType().isBlank())
                ? "application/json"
                : body.contentType();

        Schema<Object> objectSchema = new Schema<>();
        objectSchema.setType("object");
        for (ManualEndpointRequest.FieldInput f : body.fields()) {
            if (f == null || f.name() == null || f.name().isBlank()) {
                continue;
            }
            Schema<?> fieldSchema = schemaOf(f.type());
            if (f.description() != null && !f.description().isBlank()) {
                fieldSchema.setDescription(f.description());
            }
            objectSchema.addProperty(f.name(), fieldSchema);
            if (Boolean.TRUE.equals(f.required())) {
                objectSchema.addRequiredItem(f.name());
            }
        }
        if (objectSchema.getProperties() == null || objectSchema.getProperties().isEmpty()) {
            return;
        }

        Content content = new Content();
        content.addMediaType(contentType, new MediaType().schema(objectSchema));
        operation.setRequestBody(new RequestBody().content(content));
    }

    /** 응답 추가 (responses.{code} + 응답 헤더). 없으면 기본 200을 하나 넣는다. */
    private void addResponses(Operation operation, List<ManualEndpointRequest.ResponseInput> responses) {
        ApiResponses apiResponses = new ApiResponses();
        if (responses == null || responses.isEmpty()) {
            apiResponses.addApiResponse("200", new ApiResponse().description("OK"));
            operation.setResponses(apiResponses);
            return;
        }
        for (ManualEndpointRequest.ResponseInput r : responses) {
            if (r == null || r.statusCode() == null || r.statusCode().isBlank()) {
                continue;
            }
            ApiResponse apiResponse = new ApiResponse();
            apiResponse.setDescription(r.description() != null ? r.description() : "");
            if (r.headers() != null) {
                for (ManualEndpointRequest.HeaderInput h : r.headers()) {
                    if (h == null || h.name() == null || h.name().isBlank()) {
                        continue;
                    }
                    Header header = new Header().schema(schemaOf("string"));
                    if (h.description() != null && !h.description().isBlank()) {
                        header.setDescription(h.description());
                    }
                    apiResponse.addHeaderObject(h.name(), header);
                }
            }
            apiResponses.addApiResponse(r.statusCode(), apiResponse);
        }
        if (apiResponses.isEmpty()) {
            apiResponses.addApiResponse("200", new ApiResponse().description("OK"));
        }
        operation.setResponses(apiResponses);
    }

    /** 타입 문자열 → OpenAPI Schema (예시값 없이 type만) */
    private Schema<?> schemaOf(String type) {
        Schema<?> schema = new Schema<>();
        schema.setType((type == null || type.isBlank()) ? "string" : type);
        return schema;
    }

    /** 파라미터 위치 정규화. path/query만 허용하며 그 외는 query로 취급. */
    private String normalizeParamIn(String in) {
        if (in == null) {
            return "query";
        }
        String normalized = in.trim().toLowerCase();
        return "path".equals(normalized) ? "path" : "query";
    }
}
