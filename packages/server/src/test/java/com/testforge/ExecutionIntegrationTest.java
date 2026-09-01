package com.testforge;

import com.testforge.entity.conversation.Conversation;
import com.testforge.entity.conversation.enums.ConversationStatus;
import com.testforge.entity.execution.Execution;
import com.testforge.entity.execution.enums.ExecutionStatus;
import com.testforge.entity.recipe.Recipe;
import com.testforge.lock.ConversationLock;
import com.testforge.repository.conversation.ConversationRepository;
import com.testforge.repository.execution.ExecutionRecipeRepository;
import com.testforge.repository.execution.ExecutionRepository;
import com.testforge.repository.execution.ExecutionStepRepository;
import com.testforge.repository.recipe.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 레시피 실행 시작/종료 통합 테스트 (H2).
 *
 * <ul>
 *   <li>시작: executing 전이 + EXECUTION/RECIPE/STEP 레코드 생성 + 락 점유 + 스냅샷 저장</li>
 *   <li>종료: idle 전이 + 상태 확정 + 락 해제 + 멱등 재호출</li>
 *   <li>락 경합: 이미 처리 중이면 409</li>
 *   <li>대화방 소프트 삭제: 연결된 실행의 CONVERSATION_ID는 유지됨 (FK 유지, 히스토리는 USER_ID 기준 독립)</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class ExecutionIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ExecutionRecipeRepository executionRecipeRepository;

    @Autowired
    private ExecutionStepRepository executionStepRepository;

    @Autowired
    private ConversationLock conversationLock;

    private MockMvc mockMvc;
    private static final long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        executionStepRepository.deleteAll();
        executionRecipeRepository.deleteAll();
        executionRepository.deleteAll();
        recipeRepository.deleteAll();
        conversationRepository.deleteAll();
    }

    /** 두 스텝짜리 레시피 생성 (API + SCRIPT) */
    private Long newRecipe(Long apiSpecId) {
        Recipe recipe = new Recipe(USER_ID, apiSpecId, "회원가입");
        recipe.setDescription("회원가입 레시피");
        recipe.setStepsJson("[{\"name\":\"가입 요청\",\"type\":\"api\"},{\"name\":\"결과 가공\",\"type\":\"script\"}]");
        recipe.setCurrentVersion(1);
        return recipeRepository.save(recipe).getId();
    }

    private Long newConversation(Long apiSpecId, ConversationStatus status) {
        Conversation c = new Conversation(USER_ID);
        c.setTitle("방");
        c.setApiSpecId(apiSpecId);
        c.setStatus(status);
        return conversationRepository.save(c).getId();
    }

    // ── 시작: executing 전이 + 3계층 레코드 + 락 점유 ──
    @Test
    void start_transitionsToExecuting_andCreatesRecords() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);

        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + ",\"mode\":\"AUTO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status.code").value("RUNNING"))
                .andExpect(jsonPath("$.type.code").value("SINGLE"))
                .andExpect(jsonPath("$.mode.code").value("AUTO"))
                .andExpect(jsonPath("$.title").value("회원가입"))
                .andExpect(jsonPath("$.recipes.length()").value(1))
                .andExpect(jsonPath("$.recipes[0].steps.length()").value(2))
                .andExpect(jsonPath("$.recipes[0].steps[0].stepType.code").value("API"))
                .andExpect(jsonPath("$.recipes[0].steps[1].stepType.code").value("SCRIPT"));

        // 대화방 executing 전이 + 락 점유
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.EXECUTING);
        assertThat(conversationLock.isLocked(conversationId)).isTrue();

        // 레코드 생성 확인
        List<Execution> executions = executionRepository.findAll();
        assertThat(executions).hasSize(1);
        Long executionId = executions.get(0).getId();
        assertThat(executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId)).hasSize(1);
        // 스냅샷이 저장됨 (원본 독립)
        assertThat(executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId)
                .get(0).getRecipeSnapshotJson()).contains("회원가입");
    }

    // ── 종료: idle 전이 + 상태 확정 + 락 해제 + 멱등 ──
    @Test
    void complete_transitionsToIdle_andReleasesLock_idempotent() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);

        String startResponse = mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long executionId = executionRepository.findAll().get(0).getId();

        // 종료 보고 (SUCCESS)
        mockMvc.perform(post("/api/v1/executions/{id}/complete", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCESS\",\"resultSummary\":\"완료\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.resultSummary").value("완료"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());

        // idle 전이 + 락 해제
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.IDLE);
        assertThat(conversationLock.isLocked(conversationId)).isFalse();
        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.SUCCESS);

        // 멱등: 이미 종료된 실행 재호출 → 200 no-op, 상태 유지
        mockMvc.perform(post("/api/v1/executions/{id}/complete", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("SUCCESS"));
    }

    // ── 종료: RUNNING을 최종 상태로 보고 → 400 ──
    @Test
    void complete_withRunningStatus_returns400() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);
        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + "}"))
                .andExpect(status().isCreated());
        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/v1/executions/{id}/complete", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RUNNING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 락 경합: 이미 처리 중이면 409 ──
    @Test
    void start_whenLocked_returns409() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);

        // 다른 처리가 점유 중인 상황 시뮬레이션
        assertThat(conversationLock.tryLock(conversationId)).isTrue();

        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_BUSY"));
    }

    // ── 시작: 없는 레시피 → 404 (락 해제되어 영구 잠금 없음) ──
    @Test
    void start_unknownRecipe_returns404_andReleasesLock() throws Exception {
        Long conversationId = newConversation(10L, ConversationStatus.IDLE);

        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECIPE_NOT_FOUND"));

        // 미시작이므로 락은 해제됨 (영구 잠금 방지)
        assertThat(conversationLock.isLocked(conversationId)).isFalse();
    }

    // ── 대화방 소프트 삭제: 연결(CONVERSATION_ID)은 유지된다 (FK를 끊지 않음, 히스토리는 USER_ID 기준 독립) ──
    @Test
    void deleteConversation_keepsExecutionLink() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);
        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + "}"))
                .andExpect(status().isCreated());
        Long executionId = executionRepository.findAll().get(0).getId();

        // 실행 종료 후 대화방 삭제 (실행 중 삭제는 다음 조각의 중지 흐름)
        mockMvc.perform(post("/api/v1/executions/{id}/complete", executionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCESS\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/conversations/{id}", conversationId))
                .andExpect(status().isNoContent());

        // 소프트 삭제라 대화방 row는 남고, 실행의 대화 연결도 그대로 유지된다.
        Execution execution = executionRepository.findById(executionId).orElseThrow();
        assertThat(execution.getConversationId()).isEqualTo(conversationId);
    }

    // ── 실행 상세 조회 ──
    @Test
    void detail_returnsExecution() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);
        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + "}"))
                .andExpect(status().isCreated());
        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/v1/executions/{id}", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(executionId))
                .andExpect(jsonPath("$.recipes[0].recipeName").value("회원가입"));
    }

    // ── 실행 상세: 없는 실행 → 404 ──
    @Test
    void detail_unknownExecution_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/executions/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_NOT_FOUND"));
    }
}
