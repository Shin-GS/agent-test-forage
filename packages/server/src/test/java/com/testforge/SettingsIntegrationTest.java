package com.testforge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 설정 조회 API 통합 테스트. 읽기 전용 조회 + 시크릿(API 키) 미노출을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SettingsIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // ── 설정 조회: 고정 계약(provider/editable) + 값 존재/타입 ──
    // 모델명/타임아웃 구체값은 환경변수(AI_MODEL_*, EXEC_*)로 덮일 수 있으므로 값을 단언하지 않는다.
    // (환경 의존 단언은 CI/다른 개발자 환경에서 깨진다) 계약상 고정된 것만 값으로 검증한다.
    @Test
    void getSettings_returnsReadOnlyContract() throws Exception {
        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                // Provider는 OpenRouter 고정, 편집 불가(설정 파일로만 변경) — 계약
                .andExpect(jsonPath("$.provider").value("OpenRouter"))
                .andExpect(jsonPath("$.editable").value(false))
                // 나머지는 존재 + 타입만 확인 (구체값은 환경에 따라 달라질 수 있음)
                .andExpect(jsonPath("$.reasoningModel").isString())
                .andExpect(jsonPath("$.fastModel").isString())
                .andExpect(jsonPath("$.historyLimit").isNumber())
                .andExpect(jsonPath("$.aiTimeoutSeconds").isNumber())
                .andExpect(jsonPath("$.stepTimeoutSeconds").isNumber())
                .andExpect(jsonPath("$.recipeTimeoutSeconds").isNumber());
    }

    // ── 시크릿 미노출: 응답 어디에도 apiKey/시크릿이 없어야 함 ──
    @Test
    void getSettings_doesNotExposeSecrets() throws Exception {
        String body = mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // API 키 관련 키/값이 응답에 절대 포함되지 않아야 한다
        org.assertj.core.api.Assertions.assertThat(body.toLowerCase())
                .doesNotContain("apikey")
                .doesNotContain("api-key")
                .doesNotContain("api_key")
                .doesNotContain("sk-");
    }
}
