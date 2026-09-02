package com.testforge.ai.openai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.config.AiSettings;
import com.testforge.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * OpenAI 호환 Chat Completions API를 호출하는 얇은 HTTP 계층 (OpenAI·OpenRouter 공통).
 * base-url/api-key/타임아웃은 {@link AiSettings}에서 읽는다. tool 스키마를 실어 보내고 응답
 * (tool_calls 포함)을 그대로 돌려주며, "어떤 tool을 골랐는지" 해석은 상위(Resolver)가 한다.
 *
 * <p>보안: HTTPS 엔드포인트 + Authorization Bearer 헤더. 키는 로그에 남기지 않는다.
 */
@Component
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private final AiSettings settings;
    private final RestClient restClient;

    // 자체 매퍼로 직렬화/역직렬화를 명시 제어한다. Boot의 기본 매퍼(Jackson 3)에 의존하지 않아
    // 버전 차이에 안 휘둘리고, 응답에 우리가 정의하지 않은 필드(usage/id/model 등)가 와도 무시한다.
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public OpenAiClient(AiSettings settings) {
        this.settings = settings;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(settings.timeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(settings.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + safeKey(settings.apiKey()))
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * chat/completions 호출. tool 목록과 메시지를 보내고 응답을 받는다. tool_choice는 "required"로
     * 두어 반드시 tool 하나를 선택하게 한다(우리 흐름은 항상 tool 분기이므로).
     *
     * @param model    사용할 모델 (예: openai/gpt-4o)
     * @param messages system/user 메시지
     * @param tools    tool 정의 목록
     * @return 응답 (choices[0].message.tool_calls에 선택 결과)
     */
    public OpenAiDtos.ChatResponse chatWithTools(String model,
                                                 List<OpenAiDtos.ChatMessage> messages,
                                                 List<OpenAiDtos.Tool> tools) {
        OpenAiDtos.ChatRequest request = new OpenAiDtos.ChatRequest(
                model, messages, tools, "required", 0.0);

        String requestBody;
        try {
            requestBody = mapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize AI request", e);
        }

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // 상태코드 + 응답 본문을 남겨 원인 추적. 응답 본문엔 키가 없고, 요청 헤더는 로깅하지 않는다.
            int status = e.getStatusCode().value();
            log.error("AI API error: status={}, body={}", status, e.getResponseBodyAsString());
            // 402(Payment Required)/429(Too Many Requests) = 크레딧/한도 소진 → 전용 예외로 구분.
            // 재시도해도 소용없으므로 사용자에게 "사용 한도 도달"을 명확히 안내한다(ai-config.md).
            if (status == 402 || status == 429) {
                throw ApiException.aiQuotaExceeded("AI quota exceeded (status " + status + ")");
            }
            throw ApiException.aiCallFailed("AI API returned " + status);
        }

        try {
            return mapper.readValue(responseBody, OpenAiDtos.ChatResponse.class);
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", truncate(responseBody), e);
            throw ApiException.aiCallFailed("AI response parse error");
        }
    }

    /** 로그용 응답 본문 절단 (과도한 로그 방지) */
    private String truncate(String body) {
        if (body == null) {
            return "null";
        }
        return body.length() > 500 ? body.substring(0, 500) + "...(truncated)" : body;
    }

    private String safeKey(String key) {
        // 키가 없어도 클라이언트 생성 자체는 되게 한다(실제 호출은 hasApiKey 가드로 막음).
        return key == null ? "" : key;
    }
}
