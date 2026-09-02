package com.testforge.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 호출에 필요한 설정을 읽는 <b>단일 창구</b>. 값은 프로퍼티({@code ai-test-forge.ai.*},
 * .env/application.yml)에서만 읽는다. <b>설정 파일이 유일한 소스</b>이며, 사용자·관리자 모두 UI에서
 * 바꿀 수 없고 DB에도 저장하지 않는다(settings.md: 설정 페이지는 읽기 전용, 변경은 파일/재기동 경로로만).
 * AI 호출 코드(OpenAiCompatibleIntentResolver 등)는 이 창구에만 의존한다.
 *
 * <p>모델은 역할별 2개다(ai-config.md): {@code reasoning}(의도 분석/플랜/조회 판단),
 * {@code fast}(필드 생성/요약). Provider는 OpenRouter 고정이며 base-url이 OpenAI 호환 엔드포인트를
 * 가리킨다. {@code apiKey}가 비어 있으면 실제 AI를 쓰지 않고 규칙 기반 목으로 동작한다.
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
