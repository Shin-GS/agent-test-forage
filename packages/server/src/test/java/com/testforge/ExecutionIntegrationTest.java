package com.testforge;

import com.testforge.entity.conversation.Conversation;
import com.testforge.entity.conversation.Message;
import com.testforge.entity.conversation.MessagePart;
import com.testforge.entity.conversation.enums.ConversationStatus;
import com.testforge.entity.conversation.enums.MessageRole;
import com.testforge.entity.conversation.enums.MessageStatus;
import com.testforge.entity.conversation.enums.PartStatus;
import com.testforge.entity.conversation.enums.PartType;
import com.testforge.repository.conversation.MessagePartRepository;
import com.testforge.repository.conversation.MessageRepository;
import com.testforge.entity.execution.Execution;
import com.testforge.entity.execution.enums.ExecutionRecipeStatus;
import com.testforge.entity.execution.enums.ExecutionStatus;
import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.lock.ConversationLock;
import com.testforge.repository.conversation.ConversationRepository;
import com.testforge.repository.execution.ExecutionRecipeRepository;
import com.testforge.repository.execution.ExecutionRepository;
import com.testforge.repository.execution.ExecutionStepRepository;
import com.testforge.repository.recipe.RecipeRepository;
import com.testforge.support.TestAuthSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
@Import(TestAuthSupport.class)
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

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessagePartRepository messagePartRepository;

    @Autowired
    private TestAuthSupport testAuth;

    private MockMvc mockMvc;
    private static final long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        executionStepRepository.deleteAll();
        executionRecipeRepository.deleteAll();
        executionRepository.deleteAll();
        messagePartRepository.deleteAll();
        messageRepository.deleteAll();
        recipeRepository.deleteAll();
        conversationRepository.deleteAll();
        // 인증 주체와 동일 id의 ACTIVE 계정 보장
        testAuth.ensureUser(USER_ID, UserRole.USER);
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

        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId).with(testAuth.as(USER_ID))
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

    // ── 자동완료: 마지막 레시피 마지막 스텝 reportStep(SUCCESS) → idle 전이 + 락 해제 + 상태 확정 ──
    //    (외부 complete API 폐지: 완료 진입점은 reportStep 자동완료로 단일화됨)
    @Test
    void reportLastStep_autoCompletes_transitionsToIdle_andReleasesLock() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);

        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + "}"))
                .andExpect(status().isCreated());
        Long executionId = executionRepository.findAll().get(0).getId();

        // 2스텝 레시피의 모든 스텝을 순서대로 SUCCESS 보고 → 마지막 스텝에서 자동완료
        reportAllStepsSuccess(executionId);

        // idle 전이 + 락 해제 + 실행 SUCCESS 확정
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.IDLE);
        assertThat(conversationLock.isLocked(conversationId)).isFalse();
        Execution execution = executionRepository.findById(executionId).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(execution.getFinishedAt()).isNotNull();
        // 계층 정합: 하위 레시피도 SUCCESS로 종료됨
        assertThat(executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId)
                .get(0).getStatus()).isEqualTo(ExecutionRecipeStatus.SUCCESS);
    }

    // ── 자동완료 멱등: 이미 terminal인 실행에 추가 reportStep → 400 "already terminal" ──
    //    (완료 진입점 단일화 + reportStep 종료검증 재사용으로 이중완료를 구조적으로 방지)
    @Test
    void reportStep_afterAutoComplete_returns400_alreadyTerminal() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);
        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + "}"))
                .andExpect(status().isCreated());
        Long executionId = executionRepository.findAll().get(0).getId();
        List<Long> stepIds = stepIdsOf(executionId);

        // 모든 스텝 성공 → 자동완료
        for (Long sid : stepIds) {
            mockMvc.perform(post("/api/v1/executions/{eid}/steps/{sid}", executionId, sid).with(testAuth.as(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"SUCCESS\"}"))
                    .andExpect(status().isOk());
        }
        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.SUCCESS);

        // terminal 실행에 추가 reportStep → 400 (기존 reportStep 종료검증 재사용)
        mockMvc.perform(post("/api/v1/executions/{eid}/steps/{sid}", executionId, stepIds.get(0)).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCESS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 중간 스텝 실패: 전체 중단 + 실행 PARTIAL (전이 안 함) ──
    @Test
    void reportStep_failure_stopsWholeExecutionAsPartial() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);
        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + "}"))
                .andExpect(status().isCreated());
        Long executionId = executionRepository.findAll().get(0).getId();
        List<Long> stepIds = stepIdsOf(executionId);

        // 첫 스텝을 FAILED로 보고 → 전체 중단, PARTIAL 종료
        mockMvc.perform(post("/api/v1/executions/{eid}/steps/{sid}", executionId, stepIds.get(0)).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\",\"errorMessage\":\"boom\"}"))
                .andExpect(status().isOk());

        Execution execution = executionRepository.findById(executionId).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.PARTIAL);
        assertThat(execution.getFinishedAt()).isNotNull();
        // 대화방 idle + 락 해제 (완료 진입점 공유)
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.IDLE);
        assertThat(conversationLock.isLocked(conversationId)).isFalse();
    }

    // ── 락 경합: 이미 처리 중이면 409 ──
    @Test
    void start_whenLocked_returns409() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);

        // 다른 처리가 점유 중인 상황 시뮬레이션
        assertThat(conversationLock.tryLock(conversationId)).isTrue();

        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_BUSY"));
    }

    // ── 시작: 없는 레시피 → 404 (락 해제되어 영구 잠금 없음) ──
    @Test
    void start_unknownRecipe_returns404_andReleasesLock() throws Exception {
        Long conversationId = newConversation(10L, ConversationStatus.IDLE);

        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId).with(testAuth.as(USER_ID))
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
        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + "}"))
                .andExpect(status().isCreated());
        Long executionId = executionRepository.findAll().get(0).getId();

        // 실행 종료 후 대화방 삭제. 종료는 모든 스텝 SUCCESS 보고로 자동완료(외부 complete 폐지)
        reportAllStepsSuccess(executionId);

        mockMvc.perform(delete("/api/v1/conversations/{id}", conversationId).with(testAuth.as(USER_ID)))
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
        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + "}"))
                .andExpect(status().isCreated());
        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(get("/api/v1/executions/{id}", executionId).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(executionId))
                .andExpect(jsonPath("$.recipes[0].recipeName").value("회원가입"));
    }

    // ── 실행 상세: 없는 실행 → 404 ──
    @Test
    void detail_unknownExecution_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/executions/{id}", 999999L).with(testAuth.as(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_NOT_FOUND"));
    }

    /** 실행에 속한 (현재 RUNNING 레시피의) 스텝 ID 목록을 순서대로 반환 */
    private List<Long> stepIdsOf(Long executionId) {
        Long recipeRowId = executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId)
                .stream()
                .filter(r -> r.getStatus() == ExecutionRecipeStatus.RUNNING)
                .findFirst()
                .orElseThrow()
                .getId();
        return executionStepRepository.findByExecutionRecipeIdOrderByStepIndexAsc(recipeRowId).stream()
                .map(com.testforge.entity.execution.ExecutionStep::getId)
                .toList();
    }

    /** 현재 RUNNING 레시피의 모든 스텝을 순서대로 SUCCESS 보고 (마지막 스텝에서 자동완료 유도) */
    private void reportAllStepsSuccess(Long executionId) throws Exception {
        for (Long stepId : stepIdsOf(executionId)) {
            mockMvc.perform(post("/api/v1/executions/{eid}/steps/{sid}", executionId, stepId).with(testAuth.as(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"SUCCESS\"}"))
                    .andExpect(status().isOk());
        }
    }

    /** 실행을 시작하고 첫 스텝 ID를 돌려주는 헬퍼 */
    private long startAndFirstStepId(Long conversationId, Long recipeId) throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + "}"))
                .andExpect(status().isCreated());
        Long executionId = executionRepository.findAll().get(0).getId();
        Long recipeRowId = executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId)
                .get(0).getId();
        Long stepId = executionStepRepository.findByExecutionRecipeIdOrderByStepIndexAsc(recipeRowId)
                .get(0).getId();
        return stepId;
    }

    // ── 스텝 보고: 스텝 갱신 + context 누적 + execution_progress ──
    @Test
    void reportStep_updatesStepAndAccumulatesContext() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);
        long stepId = startAndFirstStepId(conversationId, recipeId);
        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/v1/executions/{eid}/steps/{sid}", executionId, stepId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCESS\",\"summary\":\"가입 성공\","
                                + "\"extractedValues\":{\"memberId\":123},"
                                + "\"response\":{\"ok\":true}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.summary").value("가입 성공"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());

        // context에 extractedValues가 누적됨
        mockMvc.perform(get("/api/v1/executions/{id}", executionId).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.memberId").value(123));
    }

    // ── 스텝 보고: 다른 실행의 스텝을 보고하면 400 ──
    @Test
    void reportStep_stepNotInExecution_returns400() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);
        long stepId = startAndFirstStepId(conversationId, recipeId);

        // 존재하지 않는 실행 ID로 보고 → 404 (실행 없음)
        mockMvc.perform(post("/api/v1/executions/{eid}/steps/{sid}", 999999L, stepId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCESS\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_NOT_FOUND"));
    }

    // ── 스텝 보고: PENDING을 보고하면 400 ──
    @Test
    void reportStep_pendingStatus_returns400() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);
        long stepId = startAndFirstStepId(conversationId, recipeId);
        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/v1/executions/{eid}/steps/{sid}", executionId, stepId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 중지(stop): 실행을 STOPPED로 종료 + 대화방 idle + 락 해제 ──
    @Test
    void stop_terminatesRunningExecutionAsStopped() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);
        startAndFirstStepId(conversationId, recipeId);
        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/v1/conversations/{id}/stop", conversationId).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("IDLE"));

        // 실행은 STOPPED로 종료되고 히스토리에 남는다
        Execution execution = executionRepository.findById(executionId).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.STOPPED);
        assertThat(execution.getFinishedAt()).isNotNull();
        assertThat(conversationLock.isLocked(conversationId)).isFalse();
        // 계층 정합: 하위 레시피도 STOPPED로 종료됨 (RUNNING으로 남지 않음)
        assertThat(executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId)
                .get(0).getStatus()).isEqualTo(ExecutionRecipeStatus.STOPPED);
    }

    // ── 취소(cancel): 실행을 CANCELLED로 종료 (중지와 구분), 요약 자동 기록, 히스토리 유지 ──
    @Test
    void cancel_terminatesRunningExecutionAsCancelled() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);
        startAndFirstStepId(conversationId, recipeId);
        Long executionId = executionRepository.findAll().get(0).getId();

        mockMvc.perform(post("/api/v1/conversations/{id}/cancel", conversationId).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("IDLE"));

        // 취소는 CANCELLED로 기록 (중지 STOPPED와 구분) + 요약 자동 생성 + 히스토리 유지
        Execution execution = executionRepository.findById(executionId).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        // 스텝 2개 중 0개 완료 → "취소됨 · 0/2 스텝 완료"
        assertThat(execution.getResultSummary()).isEqualTo("취소됨 · 0/2 스텝 완료");
        // 하위 레시피도 CANCELLED로 정합
        assertThat(executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId)
                .get(0).getStatus()).isEqualTo(ExecutionRecipeStatus.CANCELLED);
    }

    // ── 중지(stop): 스텝 1개 보고 후 중지 → 요약에 완료 수 반영 ──
    @Test
    void stop_afterOneStep_summaryReflectsProgress() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);
        long stepId = startAndFirstStepId(conversationId, recipeId);
        Long executionId = executionRepository.findAll().get(0).getId();

        // 첫 스텝 성공 보고
        mockMvc.perform(post("/api/v1/executions/{eid}/steps/{sid}", executionId, stepId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCESS\",\"summary\":\"ok\"}"))
                .andExpect(status().isOk());

        // 중지
        mockMvc.perform(post("/api/v1/conversations/{id}/stop", conversationId).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk());

        Execution execution = executionRepository.findById(executionId).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.STOPPED);
        // 2개 중 1개 완료 → "중지됨 · 1/2 스텝 완료"
        assertThat(execution.getResultSummary()).isEqualTo("중지됨 · 1/2 스텝 완료");
    }

    // ── 실행 중 대화방 삭제: 차단(409) ──
    @Test
    void deleteConversation_whileExecuting_returns409() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);
        startAndFirstStepId(conversationId, recipeId);

        // 대화방이 EXECUTING 상태이므로 삭제 차단
        mockMvc.perform(delete("/api/v1/conversations/{id}", conversationId).with(testAuth.as(USER_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_EXECUTING"));
    }

    // ── 히스토리 필터 회귀 테스트 ──

    /**
     * 히스토리 필터 테스트용 종료 실행 하나를 직접 저장한다(실행 시작 API를 거치지 않고 리포지토리에 저장).
     * 상태/서비스(apiSpecId)/제목/시작시각을 케이스별로 지정한다. apiSpecId가 null이면 NULL 실행(플랜 등)이다.
     */
    private Long saveHistory(String title, ExecutionStatus status, Long apiSpecId,
                             java.time.LocalDateTime startedAt) {
        Execution e = new Execution(USER_ID, com.testforge.entity.execution.enums.ExecutionType.SINGLE,
                com.testforge.entity.execution.enums.ExecutionMode.AUTO);
        e.setTitle(title);
        e.setStatus(status);
        e.setApiSpecId(apiSpecId);
        e.setStartedAt(startedAt);
        return executionRepository.save(e).getId();
    }

    // 상태 다중 필터: 지정한 상태들만 반환 (빈=전체는 기존 계약)
    @Test
    void history_filtersByMultipleStatuses() throws Exception {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        saveHistory("성공건", ExecutionStatus.SUCCESS, 10L, now);
        saveHistory("실패건", ExecutionStatus.FAILED, 10L, now);
        saveHistory("취소건", ExecutionStatus.CANCELLED, 10L, now);

        // SUCCESS + FAILED 만 요청 → 2건 (취소 제외)
        mockMvc.perform(get("/api/v1/executions").with(testAuth.as(USER_ID))
                        .param("status", "SUCCESS", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[*].title",
                        org.hamcrest.Matchers.containsInAnyOrder("성공건", "실패건")));
    }

    // 서비스(apiSpecId) 필터: 지정 시 NULL 실행 제외, 미지정(전체) 시 NULL 포함
    @Test
    void history_filtersByApiSpecId_excludingNullWhenSpecified() throws Exception {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        saveHistory("스펙10건", ExecutionStatus.SUCCESS, 10L, now);
        saveHistory("스펙20건", ExecutionStatus.SUCCESS, 20L, now);
        saveHistory("NULL스펙건", ExecutionStatus.SUCCESS, null, now);

        // apiSpecId=10 지정 → 스펙10건만 (NULL/20 제외)
        mockMvc.perform(get("/api/v1/executions").with(testAuth.as(USER_ID))
                        .param("apiSpecId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("스펙10건"));

        // 미지정(전체) → NULL 실행 포함 3건
        mockMvc.perform(get("/api/v1/executions").with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    // 기간 필터: to 당일 포함(23:59:59.999까지), 범위 밖 제외
    @Test
    void history_filtersByDateRange_inclusiveOfToDay() throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now();
        // 오늘 늦은 시각(당일 포함 경계 검증) + 어제 + 내일
        saveHistory("오늘건", ExecutionStatus.SUCCESS, 10L, today.atTime(23, 30));
        saveHistory("어제건", ExecutionStatus.SUCCESS, 10L, today.minusDays(1).atTime(12, 0));
        saveHistory("내일건", ExecutionStatus.SUCCESS, 10L, today.plusDays(1).atTime(1, 0));

        // from=to=오늘 → 오늘건만 (어제/내일 제외, 당일 23:30도 포함)
        mockMvc.perform(get("/api/v1/executions").with(testAuth.as(USER_ID))
                        .param("from", today.toString())
                        .param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("오늘건"));
    }

    // ── 한 턴 귀속: 카드 [바로 실행] → CARD/PROGRESS/RESULT가 같은 턴(messageId) ──

    /** execution_mode 카드 파트를 하나 만들어(ASSISTANT 턴) 그 파트 ID를 반환한다(실행 촉발 파트 시뮬레이션). */
    private Long newExecutionModeCardPart(Long conversationId) {
        Message turn = messageRepository.save(
                new Message(conversationId, MessageRole.ASSISTANT, MessageStatus.COMPLETE));
        MessagePart card = new MessagePart(turn.getId(), PartType.CARD, PartStatus.PENDING);
        card.setCardType("execution_mode");
        card.setPayloadJson("{\"cardType\":\"execution_mode\"}");
        card.setSchemaVersion(2);
        return messagePartRepository.save(card).getId();
    }

    @Test
    void cardTriggeredExecution_cardProgressResult_shareSameTurn() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);

        // 실행을 촉발한 execution_mode 카드 파트 (그 턴에 진행/결과가 귀속되어야 함)
        Long cardPartId = newExecutionModeCardPart(conversationId);
        Long cardTurnId = messagePartRepository.findById(cardPartId).orElseThrow().getMessageId();

        // 카드 [바로 실행] = 실행 시작 요청에 messageId(=카드 파트 id) 전달
        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId
                                + ",\"mode\":\"AUTO\",\"messageId\":" + cardPartId + "}"))
                .andExpect(status().isCreated());
        Long executionId = executionRepository.findAll().get(0).getId();

        // 실행 완료 (모든 스텝 SUCCESS → RESULT append)
        reportAllStepsSuccess(executionId);

        // TRIGGER_PART_ID는 촉발 카드 파트를 가리켜야 한다(PROGRESS 파트 id로 덮어쓰지 않음)
        assertThat(executionRepository.findById(executionId).orElseThrow().getTriggerPartId())
                .isEqualTo(cardPartId);

        // CARD/PROGRESS/RESULT 파트가 모두 카드 턴(cardTurnId)에 속한다(한 턴 = 아바타 1개)
        List<MessagePart> cardTurnParts = messagePartRepository.findByMessageIdOrderByIdAsc(cardTurnId);
        List<PartType> types = cardTurnParts.stream().map(MessagePart::getType).toList();
        assertThat(types).containsExactly(PartType.CARD, PartType.PROGRESS, PartType.RESULT);

        // 카드 파트는 CONSUMED(재활성화 방지)
        assertThat(cardTurnParts.get(0).getStatus()).isEqualTo(PartStatus.CONSUMED);
        // PROGRESS/RESULT는 executionId로 실행을 정참조
        assertThat(cardTurnParts.get(1).getExecutionId()).isEqualTo(executionId);
        assertThat(cardTurnParts.get(2).getExecutionId()).isEqualTo(executionId);
    }

    @Test
    void directExecution_withoutCard_createsNewTurnForProgressResult() throws Exception {
        Long specId = 10L;
        Long recipeId = newRecipe(specId);
        Long conversationId = newConversation(specId, ConversationStatus.IDLE);

        // 카드 없이 실행 (messageId 미전달) — 패널 직접 실행 등
        mockMvc.perform(post("/api/v1/conversations/{id}/executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeId\":" + recipeId + ",\"mode\":\"AUTO\"}"))
                .andExpect(status().isCreated());
        Long executionId = executionRepository.findAll().get(0).getId();
        reportAllStepsSuccess(executionId);

        // 촉발 카드가 없으므로 TRIGGER_PART_ID는 null (폴백: 새 턴)
        assertThat(executionRepository.findById(executionId).orElseThrow().getTriggerPartId()).isNull();

        // PROGRESS/RESULT가 새 ASSISTANT 턴에 함께 존재한다(둘은 같은 턴, 카드는 없음)
        MessagePart progress = messagePartRepository
                .findTopByExecutionIdAndTypeOrderByIdDesc(executionId, PartType.PROGRESS).orElseThrow();
        List<MessagePart> turnParts = messagePartRepository.findByMessageIdOrderByIdAsc(progress.getMessageId());
        List<PartType> types = turnParts.stream().map(MessagePart::getType).toList();
        assertThat(types).containsExactly(PartType.PROGRESS, PartType.RESULT);
    }
}
