package com.testforge.dto.settings;

/**
 * 설정 페이지(읽기 전용) 조회 응답. 서버에 적용된 AI/실행 설정을 표시용으로 내린다.
 * <b>API 키 등 시크릿은 절대 포함하지 않는다</b>(settings.md: 시크릿 제외).
 *
 * <p>모든 값은 서버 설정 파일에서 온 것이며 UI에서 편집할 수 없다. Provider는 OpenRouter 고정.
 *
 * @param provider        AI 프로바이더 (고정: "OpenRouter")
 * @param reasoningModel  의도 분석/플랜/조회 판단 모델명
 * @param fastModel       필드 생성/요약 모델명
 * @param historyLimit    AI에 전달하는 최근 대화 이력 건수
 * @param aiTimeoutSeconds AI 호출 타임아웃(초)
 * @param stepTimeoutSeconds   스텝 타임아웃(초)
 * @param recipeTimeoutSeconds 레시피 전체 타임아웃(초)
 * @param editable        편집 가능 여부 (항상 false — 설정 파일로만 변경)
 */
public record SettingsView(
        String provider,
        String reasoningModel,
        String fastModel,
        Integer historyLimit,
        Integer aiTimeoutSeconds,
        Integer stepTimeoutSeconds,
        Integer recipeTimeoutSeconds,
        boolean editable) {
}
