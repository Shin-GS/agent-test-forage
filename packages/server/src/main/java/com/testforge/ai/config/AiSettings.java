package com.testforge.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 호출에 필요한 설정을 읽는 <b>단일 창구</b>. 지금은 프로퍼티({@code ai-test-forge.ai.*},
 * 값은 .env/환경변수)에서 읽지만, 추후 설정 페이지(DB 저장, settings.md)가 생기면 이 창구의 내부만
 * "DB에서 읽기"로 교체하면 된다. AI 호출 코드(OpenAiCompatibleIntentResolver 등)는 이 창구에만
 * 의존하므로, 설정 소스가 바뀌어도 호출 코드는 변경되지 않는다.
 *
 * <p>모델은 역할별 2개다(ai-config.md): {@code reasoning}(의도 분석/플랜/조회 판단),
 * {@code fast}(필드 생성/요약). base-url을 두어 OpenAI 직접·OpenRouter 등 OpenAI 호환 엔드포인트를
 * 설정만으로 전환한다. {@code apiKey}가 비어 있으면 실제 AI를 쓰지 않고 규칙 기반 목으로 동작한다.
 *
 * @param baseUrl        OpenAI 호환 API base URL (예: https://openrouter.ai/api/v1)
 * @param apiKey         API 키 (시크릿, .env/환경변수 주입). 비어 있으면 목 사용
 * @param reasoningModel 무거운 판단용 모델 (예: openai/gpt-4o)
 * @param fastModel      가벼운 생성용 모델 (예: openai/gpt-4o-mini)
 * @param historyLimit   AI에 전달할 최근 대화 이력 건수 (기본 15)
 * @param timeoutSeconds 단일 AI 호출 타임아웃(초)
 */
@ConfigurationProperties(prefix = "ai-test-forge.ai")
public record AiSettings(
        String baseUrl,
        String apiKey,
        String reasoningModel,
        String fastModel,
        Integer historyLimit,
        Integer timeoutSeconds) {

    public AiSettings {
        // 합리적 기본값 (프로퍼티 미지정 시)
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://openrouter.ai/api/v1";
        }
        if (reasoningModel == null || reasoningModel.isBlank()) {
            reasoningModel = "openai/gpt-4o";
        }
        if (fastModel == null || fastModel.isBlank()) {
            fastModel = "openai/gpt-4o-mini";
        }
        if (historyLimit == null || historyLimit <= 0) {
            historyLimit = 15;
        }
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            timeoutSeconds = 30;
        }
    }

    /** 실제 AI 호출 가능 여부 (키가 있으면 true). false면 규칙 기반 목으로 동작 */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
