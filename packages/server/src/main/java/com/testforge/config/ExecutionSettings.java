package com.testforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 레시피 실행 관련 설정 (settings.md의 "레시피 실행 설정"). AI 설정과 마찬가지로 서버 설정 파일
 * ({@code ai-test-forge.execution.*})로만 관리하며 UI에서는 조회만 한다(설정 페이지 읽기 전용).
 *
 * <p>실제 스텝 실행은 FE 브라우저가 수행하므로 이 타임아웃은 현재 BE 로직에서 강제하지 않고,
 * FE에 내려주는 "권장 한도"이자 설정 페이지 표시값이다(추후 서버측 이어서-실행/감시에 활용 가능).
 *
 * @param stepTimeoutSeconds   개별 스텝 타임아웃(초, 기본 30)
 * @param recipeTimeoutSeconds 레시피 전체 타임아웃(초, 기본 300)
 */
@ConfigurationProperties(prefix = "ai-test-forge.execution")
public record ExecutionSettings(
        Integer stepTimeoutSeconds,
        Integer recipeTimeoutSeconds) {

    public ExecutionSettings {
        if (stepTimeoutSeconds == null || stepTimeoutSeconds <= 0) {
            stepTimeoutSeconds = 30;
        }
        if (recipeTimeoutSeconds == null || recipeTimeoutSeconds <= 0) {
            recipeTimeoutSeconds = 300;
        }
    }
}
