package com.testforge.controller.settings;

import com.testforge.ai.config.AiSettings;
import com.testforge.config.ExecutionSettings;
import com.testforge.dto.settings.SettingsView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 설정 조회 API (읽기 전용). 서버 설정 파일에 적용된 AI/실행 설정을 표시용으로 내린다.
 * 저장/수정 API는 두지 않는다 — 설정 변경은 서버 설정 파일(.env/application.yml)로만 한다
 * (settings.md: 설정 페이지는 읽기 전용, 사용자·관리자 모두 UI 편집 불가).
 *
 * <p>단순 조회라 서비스 계층 없이 설정 창구(AiSettings/ExecutionSettings)를 그대로 매핑한다.
 * <b>API 키 등 시크릿은 응답에 포함하지 않는다.</b>
 */
@RestController
@RequestMapping("/api/v1")
public class SettingsController {

    // Provider는 OpenRouter 고정 (OpenAI 호환 API). 설정으로 바꾸지 않는다.
    private static final String PROVIDER = "OpenRouter";

    private final AiSettings aiSettings;
    private final ExecutionSettings executionSettings;

    public SettingsController(AiSettings aiSettings, ExecutionSettings executionSettings) {
        this.aiSettings = aiSettings;
        this.executionSettings = executionSettings;
    }

    /** 현재 적용된 설정 조회 (읽기 전용, 시크릿 제외) */
    @GetMapping("/settings")
    public SettingsView get() {
        // TODO: 인증/권한 (auth 도메인 구현 후: 로그인 사용자로 제한)
        return new SettingsView(
                PROVIDER,
                aiSettings.reasoningModel(),
                aiSettings.fastModel(),
                aiSettings.historyLimit(),
                aiSettings.timeoutSeconds(),
                executionSettings.stepTimeoutSeconds(),
                executionSettings.recipeTimeoutSeconds(),
                false);
    }
}
