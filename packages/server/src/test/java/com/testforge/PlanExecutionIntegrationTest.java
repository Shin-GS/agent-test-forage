package com.testforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.entity.conversation.Conversation;
import com.testforge.entity.conversation.Message;
import com.testforge.entity.conversation.enums.ConversationStatus;
import com.testforge.entity.conversation.enums.MessageType;
import com.testforge.entity.execution.Execution;
import com.testforge.entity.execution.ExecutionRecipe;
import com.testforge.entity.execution.ExecutionStep;
import com.testforge.entity.execution.enums.ExecutionRecipeStatus;
import com.testforge.entity.execution.enums.ExecutionStatus;
import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.lock.ConversationLock;
import com.testforge.repository.conversation.ConversationRepository;
import com.testforge.repository.conversation.MessageRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 플랜 실행(1단계 BE 오케스트레이션) 통합 테스트 (H2).
 *
 * <ul>
 *   <li>플랜 시작: EXECUTION(TYPE=PLAN) + EXECUTION_RECIPE N개. 첫 레시피만 RUNNING+스텝, 나머지 PENDING</li>
 *   <li>레시피 전이: 앞 레시피 완료 시 다음 레시피 RUNNING 전이(스텝 생성 + usageCount 갱신)</li>
 *   <li>자동완료: 마지막 레시피 마지막 스텝 SUCCESS → 실행 SUCCESS + idle + 락 해제</li>
 *   <li>중간 실패: FAILED → 전체 중단 + PARTIAL (전이 안 함)</li>
 *   <li>전이 시 pre-run 액션 피커: 다음 레시피 필수값 미충족 → WAITING_INPUT</li>
 *   <li>respond 현재 레시피 인식: 현재 RUNNING 레시피 스냅샷 기준 재검증/재개</li>
 *   <li>usageCount 레시피별: RUNNING 전이 시점에 원본 레시피별로 갱신</li>
 *   <li>recipeIds 1개면 SINGLE로 수렴</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestAuthSupport.class)
class PlanExecutionIntegrationTest {

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
    private TestAuthSupport testAuth;

    // payload(metadataJson) 파싱 전용. 컨텍스트에 ObjectMapper 빈이 없어도 무방(읽기만 함).
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private static final long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        executionStepRepository.deleteAll();
        executionRecipeRepository.deleteAll();
        executionRepository.deleteAll();
        recipeRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        testAuth.ensureUser(USER_ID, UserRole.USER);
    }

    /** 스텝 1개짜리 레시피 (변수 없음) */
    private Long newRecipe(String name, Long apiSpecId) {
        Recipe recipe = new Recipe(USER_ID, apiSpecId, name);
        recipe.setStepsJson("[{\"name\":\"" + name + " 스텝\",\"type\":\"api\"}]");
        recipe.setCurrentVersion(1);
        return recipeRepository.save(recipe).getId();
    }

    /** 스텝 1개 + 필수 변수 1개(required, default 없음)짜리 레시피 (pre-run 액션 피커 유발용) */
    private Long newRecipeWithRequiredVar(String name, Long apiSpecId, String varName) {
        Recipe recipe = new Recipe(USER_ID, apiSpecId, name);
        recipe.setStepsJson("[{\"name\":\"" + name + " 스텝\",\"type\":\"api\"}]");
        recipe.setVariablesJson("[{\"name\":\"" + varName + "\",\"type\":\"string\",\"required\":true}]");
        recipe.setCurrentVersion(1);
        return recipeRepository.save(recipe).getId();
    }

    private Long newConversation(Long apiSpecId) {
        Conversation c = new Conversation(USER_ID);
        c.setTitle("방");
        c.setApiSpecId(apiSpecId);
        c.setStatus(ConversationStatus.IDLE);
        return conversationRepository.save(c).getId();
    }

    private void startPlan(Long conversationId, String recipeIdsJson) throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{id}/plan-executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeIds\":" + recipeIdsJson + "}"))
                .andExpect(status().isCreated());
    }

    /** 현재 RUNNING 레시피의 첫 스텝을 SUCCESS 보고 */
    private void reportRunningRecipeStep(Long executionId) throws Exception {
        ExecutionRecipe running = executionRecipeRepository
                .findByExecutionIdAndStatusOrderBySequenceAsc(executionId, ExecutionRecipeStatus.RUNNING)
                .get(0);
        ExecutionStep step = executionStepRepository
                .findByExecutionRecipeIdOrderByStepIndexAsc(running.getId()).get(0);
        mockMvc.perform(post("/api/v1/executions/{eid}/steps/{sid}", executionId, step.getId()).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCESS\"}"))
                .andExpect(status().isOk());
    }

    // ── 플랜 시작: 첫 레시피만 RUNNING+스텝, 나머지 PENDING(스텝 없음) ──
    @Test
    void startPlan_firstRunningRestPending() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("가입", specId);
        Long r2 = newRecipe("로그인", specId);
        Long conversationId = newConversation(specId);

        mockMvc.perform(post("/api/v1/conversations/{id}/plan-executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeIds\":[" + r1 + "," + r2 + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type.code").value("PLAN"))
                .andExpect(jsonPath("$.recipes.length()").value(2));

        Long executionId = executionRepository.findAll().get(0).getId();
        List<ExecutionRecipe> recipes = executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId);
        assertThat(recipes.get(0).getStatus()).isEqualTo(ExecutionRecipeStatus.RUNNING);
        assertThat(recipes.get(1).getStatus()).isEqualTo(ExecutionRecipeStatus.PENDING);
        // 첫 레시피만 스텝 생성됨
        assertThat(executionStepRepository.findByExecutionRecipeIdOrderByStepIndexAsc(recipes.get(0).getId())).hasSize(1);
        assertThat(executionStepRepository.findByExecutionRecipeIdOrderByStepIndexAsc(recipes.get(1).getId())).isEmpty();
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.EXECUTING);
    }

    // ── 레시피 전이: 앞 레시피 완료 → 다음 레시피 RUNNING + 스텝 생성 ──
    @Test
    void reportStep_advancesToNextRecipe() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("가입", specId);
        Long r2 = newRecipe("로그인", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        // 첫 레시피(1스텝) 완료 → 두 번째 레시피로 전이
        reportRunningRecipeStep(executionId);

        List<ExecutionRecipe> recipes = executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId);
        assertThat(recipes.get(0).getStatus()).isEqualTo(ExecutionRecipeStatus.SUCCESS);
        assertThat(recipes.get(1).getStatus()).isEqualTo(ExecutionRecipeStatus.RUNNING);
        // 두 번째 레시피의 스텝이 이제 생성됨
        assertThat(executionStepRepository.findByExecutionRecipeIdOrderByStepIndexAsc(recipes.get(1).getId())).hasSize(1);
        // 아직 실행 진행 중 (자동완료 전)
        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.RUNNING);
    }

    // ── 자동완료: 마지막 레시피 마지막 스텝 SUCCESS → 실행 SUCCESS + idle + 락 해제 ──
    @Test
    void reportLastStep_autoCompletesPlan() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("가입", specId);
        Long r2 = newRecipe("로그인", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        reportRunningRecipeStep(executionId); // r1 완료 → r2 전이
        reportRunningRecipeStep(executionId); // r2 완료 → 자동완료

        Execution execution = executionRepository.findById(executionId).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(execution.getFinishedAt()).isNotNull();
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.IDLE);
        assertThat(conversationLock.isLocked(conversationId)).isFalse();
        List<ExecutionRecipe> recipes = executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId);
        assertThat(recipes).allMatch(r -> r.getStatus() == ExecutionRecipeStatus.SUCCESS);
    }

    // ── 자동완료(SKIPPED): 마지막 스텝을 SKIPPED로 보고해도 실행 SUCCESS + idle + 락 해제 ──
    // orchestrateAfterStep의 성공/스킵 화이트리스트가 SKIPPED를 성공 취급으로 자동완료 전이하는지 검증.
    @Test
    void reportLastStep_skipped_autoCompletesPlan() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("가입", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        // 유일한 스텝을 SKIPPED로 보고 → 남은 pending 없음 → 자동완료
        ExecutionRecipe running = executionRecipeRepository
                .findByExecutionIdAndStatusOrderBySequenceAsc(executionId, ExecutionRecipeStatus.RUNNING).get(0);
        ExecutionStep step = executionStepRepository
                .findByExecutionRecipeIdOrderByStepIndexAsc(running.getId()).get(0);
        mockMvc.perform(post("/api/v1/executions/{eid}/steps/{sid}", executionId, step.getId()).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SKIPPED\"}"))
                .andExpect(status().isOk());

        Execution execution = executionRepository.findById(executionId).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(execution.getFinishedAt()).isNotNull();
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.IDLE);
        assertThat(conversationLock.isLocked(conversationId)).isFalse();
    }

    // ── 중간 실패: 두 번째 레시피에서 FAILED → 전체 PARTIAL, 세 번째로 전이 안 함 ──
    @Test
    void reportStep_failureInMiddle_stopsAsPartial() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("가입", specId);
        Long r2 = newRecipe("로그인", specId);
        Long r3 = newRecipe("주문", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "," + r3 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        reportRunningRecipeStep(executionId); // r1 SUCCESS → r2 전이

        // r2 스텝 FAILED
        ExecutionRecipe running = executionRecipeRepository
                .findByExecutionIdAndStatusOrderBySequenceAsc(executionId, ExecutionRecipeStatus.RUNNING).get(0);
        ExecutionStep step = executionStepRepository
                .findByExecutionRecipeIdOrderByStepIndexAsc(running.getId()).get(0);
        mockMvc.perform(post("/api/v1/executions/{eid}/steps/{sid}", executionId, step.getId()).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\",\"errorMessage\":\"boom\"}"))
                .andExpect(status().isOk());

        Execution execution = executionRepository.findById(executionId).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.PARTIAL);
        List<ExecutionRecipe> recipes = executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId);
        assertThat(recipes.get(0).getStatus()).isEqualTo(ExecutionRecipeStatus.SUCCESS);
        assertThat(recipes.get(1).getStatus()).isEqualTo(ExecutionRecipeStatus.FAILED);
        // r3는 전이되지 않아 스텝이 없고, 미실행이므로 PENDING으로 보존된다(재개 시작 지점 판별용, plan.md 이어서 실행)
        assertThat(executionStepRepository.findByExecutionRecipeIdOrderByStepIndexAsc(recipes.get(2).getId())).isEmpty();
        assertThat(recipes.get(2).getStatus()).isEqualTo(ExecutionRecipeStatus.PENDING);
        assertThat(conversationLock.isLocked(conversationId)).isFalse();
    }

    // ── 전이 시 pre-run 액션 피커: 다음 레시피 필수값 미충족 → WAITING_INPUT ──
    @Test
    void reportStep_transitionRequiresActionPicker_whenNextRecipeMissingRequired() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("가입", specId);
        Long r2 = newRecipeWithRequiredVar("로그인", specId, "token"); // 필수값 token, default 없음
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        // r1 완료 → r2 전이. r2는 필수값 미충족이라 WAITING_INPUT
        reportRunningRecipeStep(executionId);

        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.WAITING_INPUT);
        List<ExecutionRecipe> recipes = executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId);
        assertThat(recipes.get(1).getStatus()).isEqualTo(ExecutionRecipeStatus.RUNNING);
        // 락 유지 (respond에서 해제)
        assertThat(conversationLock.isLocked(conversationId)).isTrue();
    }

    // ── respond 현재 레시피 인식: 두 번째 레시피 필수값을 respond로 채우면 재개(EXECUTING) ──
    @Test
    void respondActionPicker_recognizesCurrentRunningRecipe() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("가입", specId);
        Long r2 = newRecipeWithRequiredVar("로그인", specId, "token");
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        reportRunningRecipeStep(executionId); // r1 완료 → r2 WAITING_INPUT

        // 현재 RUNNING 레시피(r2) 기준 필수값 token 제출 → 재개
        mockMvc.perform(post("/api/v1/action-picker/respond").with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionId\":" + executionId + ",\"stepIndex\":-1,\"values\":{\"token\":\"abc\"}}"))
                .andExpect(status().isOk());

        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.EXECUTING);
    }

    // ── detail pendingInputs: WAITING_INPUT 실행은 미충족 변수 목록을 반환 (FE 입력대기 판정 단일화) ──
    @Test
    void detail_returnsPendingInputs_whenExecutionWaitingInput() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("가입", specId);
        Long r2 = newRecipeWithRequiredVar("로그인", specId, "token"); // 필수값 token, default 없음
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        // r1 완료 → r2 전이. r2 필수값 미충족 → WAITING_INPUT
        reportRunningRecipeStep(executionId);
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.WAITING_INPUT);

        // detail 응답에 현재 대기 중인 미충족 변수(token)가 담긴다
        mockMvc.perform(get("/api/v1/executions/{executionId}", executionId).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("RUNNING"))
                .andExpect(jsonPath("$.pendingInputs.length()").value(1))
                .andExpect(jsonPath("$.pendingInputs[0].name").value("token"));
    }

    // ── detail pendingInputs: EXECUTING(입력 충족) 실행은 빈 리스트 ──
    @Test
    void detail_returnsEmptyPendingInputs_whenExecuting() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("가입", specId);
        Long r2 = newRecipe("로그인", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        // 시작 직후: 첫 레시피는 변수 없음 → EXECUTING (대기 아님)
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.EXECUTING);

        mockMvc.perform(get("/api/v1/executions/{executionId}", executionId).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("RUNNING"))
                .andExpect(jsonPath("$.pendingInputs.length()").value(0));
    }

    // ── detail pendingInputs: 종료된(SUCCESS) 실행은 빈 리스트 ──
    @Test
    void detail_returnsEmptyPendingInputs_whenFinished() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("가입", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        reportRunningRecipeStep(executionId); // 자동완료(SUCCESS)
        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.SUCCESS);

        mockMvc.perform(get("/api/v1/executions/{executionId}", executionId).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.pendingInputs.length()").value(0));
    }

    // ── usageCount 레시피별: RUNNING 전이 시점에 원본 레시피별로 갱신 ──
    @Test
    void usageCount_incrementedPerRecipeOnRunningTransition() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("가입", specId);
        Long r2 = newRecipe("로그인", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        // 시작 직후: 첫 레시피만 RUNNING 전이 → r1 usageCount=1, r2=0
        assertThat(recipeRepository.findById(r1).orElseThrow().getUsageCount()).isEqualTo(1);
        assertThat(recipeRepository.findById(r2).orElseThrow().getUsageCount()).isEqualTo(0);

        // r1 완료 → r2 RUNNING 전이 → r2 usageCount=1
        reportRunningRecipeStep(executionId);
        assertThat(recipeRepository.findById(r2).orElseThrow().getUsageCount()).isEqualTo(1);
    }

    // ── recipeIds 1개면 SINGLE로 수렴 ──
    @Test
    void startPlan_singleRecipe_convergesToSingle() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("가입", specId);
        Long conversationId = newConversation(specId);

        mockMvc.perform(post("/api/v1/conversations/{id}/plan-executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeIds\":[" + r1 + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type.code").value("SINGLE"))
                .andExpect(jsonPath("$.recipes.length()").value(1));

        // 단일 실행과 동일하게 동작: 1스텝 SUCCESS → 자동완료
        Long executionId = executionRepository.findAll().get(0).getId();
        reportRunningRecipeStep(executionId);
        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.SUCCESS);
    }

    // ── 플랜 시작: recipeIds 비면 400 ──
    @Test
    void startPlan_emptyRecipeIds_returns400() throws Exception {
        Long conversationId = newConversation(10L);
        mockMvc.perform(post("/api/v1/conversations/{id}/plan-executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── PROGRESS/RESULT payload 스키마 (schemaVersion 2, 레시피 그룹/레시피별 구조) ──

    /** 대화방의 최신 특정 타입 메시지 payloadJson(=metadataJson)을 파싱해 돌려준다. 없으면 실패. */
    private JsonNode latestPayload(Long conversationId, MessageType type) throws Exception {
        List<Message> messages = messageRepository.findByConversationIdOrderBySeqAsc(conversationId);
        Message target = null;
        for (Message m : messages) {
            if (m.getType() == type) {
                target = m; // 최신(SEQ 오름차순의 마지막)
            }
        }
        assertThat(target).as("expected a %s message", type).isNotNull();
        return objectMapper.readTree(target.getMetadataJson());
    }

    // 플랜 PROGRESS: 레시피 그룹 구조 + recipeProgress(k/N) + 현재 레시피만 스텝 펼침
    @Test
    void planProgress_payloadHasRecipeGroupStructure() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("이력서 작성", specId);
        Long r2 = newRecipe("포지션 탐색", specId);
        Long r3 = newRecipe("입사지원", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "," + r3 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        // r1 완료 → r2 진행 중. progress 메시지는 message_update로 최신 상태 반영
        reportRunningRecipeStep(executionId);

        JsonNode payload = latestPayload(conversationId, MessageType.PROGRESS);
        assertThat(payload.get("kind").asText()).isEqualTo("progress");
        assertThat(payload.get("schemaVersion").asInt()).isEqualTo(2);
        assertThat(payload.get("title").asText()).isNotEmpty();
        assertThat(payload.get("overallStatus").asText()).isEqualTo("running");

        // recipeProgress: 완료(r1) + 현재(r2) = 2 / 전체 3
        assertThat(payload.get("recipeProgress").get("current").asInt()).isEqualTo(2);
        assertThat(payload.get("recipeProgress").get("total").asInt()).isEqualTo(3);

        JsonNode recipes = payload.get("recipes");
        assertThat(recipes).hasSize(3);
        // r1: 완료(success), 스텝 존재
        assertThat(recipes.get(0).get("sequence").asInt()).isEqualTo(0);
        assertThat(recipes.get(0).get("recipeName").asText()).isEqualTo("이력서 작성");
        assertThat(recipes.get(0).get("status").asText()).isEqualTo("success");
        // r2: 진행 중(running), 스텝 펼침
        assertThat(recipes.get(1).get("status").asText()).isEqualTo("running");
        assertThat(recipes.get(1).get("steps")).isNotEmpty();
        // r3: 대기(pending), 스텝 없음(아직 전이 전)
        assertThat(recipes.get(2).get("status").asText()).isEqualTo("pending");
        assertThat(recipes.get(2).get("steps")).isEmpty();
    }

    // 플랜 RESULT: 레시피별 결과 배열 + overallStatus + 레시피별 상태
    @Test
    void planResult_payloadHasPerRecipeResults() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("이력서 작성", specId);
        Long r2 = newRecipe("입사지원", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        reportRunningRecipeStep(executionId); // r1 완료 → r2 전이
        reportRunningRecipeStep(executionId); // r2 완료 → 자동완료(SUCCESS) → RESULT 발행

        JsonNode payload = latestPayload(conversationId, MessageType.RESULT);
        assertThat(payload.get("kind").asText()).isEqualTo("result");
        assertThat(payload.get("schemaVersion").asInt()).isEqualTo(2);
        assertThat(payload.get("overallStatus").asText()).isEqualTo("success");

        JsonNode recipes = payload.get("recipes");
        assertThat(recipes).hasSize(2);
        assertThat(recipes.get(0).get("sequence").asInt()).isEqualTo(0);
        assertThat(recipes.get(0).get("recipeName").asText()).isEqualTo("이력서 작성");
        assertThat(recipes.get(0).get("status").asText()).isEqualTo("success");
        assertThat(recipes.get(0).has("resultValues")).isTrue();
        assertThat(recipes.get(0).has("summary")).isTrue();
        assertThat(recipes.get(1).get("recipeName").asText()).isEqualTo("입사지원");
        assertThat(recipes.get(1).get("status").asText()).isEqualTo("success");
    }

    // 단일 실행(N=1): PROGRESS/RESULT 모두 recipes 배열 1개로 통일
    @Test
    void singleExecution_payloadsUnifiedAsRecipesArrayOfOne() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("회원가입", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "]"); // 1개 → SINGLE 수렴
        Long executionId = executionRepository.findAll().get(0).getId();

        // 시작 시점 PROGRESS: recipes 1개, recipeProgress total=1
        JsonNode running = latestPayload(conversationId, MessageType.PROGRESS);
        assertThat(running.get("schemaVersion").asInt()).isEqualTo(2);
        assertThat(running.get("recipes")).hasSize(1);
        assertThat(running.get("recipeProgress").get("total").asInt()).isEqualTo(1);
        assertThat(running.get("recipes").get(0).get("recipeName").asText()).isEqualTo("회원가입");

        reportRunningRecipeStep(executionId); // 자동완료(SUCCESS)

        // RESULT: recipes 1개로 통일
        JsonNode result = latestPayload(conversationId, MessageType.RESULT);
        assertThat(result.get("schemaVersion").asInt()).isEqualTo(2);
        assertThat(result.get("recipes")).hasSize(1);
        assertThat(result.get("recipes").get(0).get("recipeName").asText()).isEqualTo("회원가입");
        assertThat(result.get("recipes").get(0).get("status").asText()).isEqualTo("success");
    }

    // ─────────────────────────────────────────────────────────────
    // 값 사전 편집 (recipeInputs) — 레시피별 시드 / 우선순위 / required 충족
    // ─────────────────────────────────────────────────────────────

    /** recipeInputs 포함 플랜 시작 (recipeInputs 는 JSON 배열 문자열) */
    private void startPlanWithInputs(Long conversationId, String recipeIdsJson,
                                     String recipeInputsJson) throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{id}/plan-executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeIds\":" + recipeIdsJson
                                + ",\"recipeInputs\":" + recipeInputsJson + "}"))
                .andExpect(status().isCreated());
    }

    /** 실행 context.userInput 에서 특정 key 값을 문자열로 꺼낸다 (없으면 null) */
    private String userInputValue(Long executionId, String key) {
        try {
            String contextJson = executionRepository.findById(executionId).orElseThrow().getContextJson();
            JsonNode userInput = objectMapper.readTree(contextJson).get("userInput");
            if (userInput == null || userInput.get(key) == null || userInput.get(key).isNull()) {
                return null;
            }
            return userInput.get(key).asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── required 변수를 recipeInputs 로 채우면 pre-run 액션 피커 없이 통과(자동 실행 논스톱) ──
    @Test
    void planWithInputs_requiredFilled_skipsPreRunPicker() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipeWithRequiredVar("이력서 작성", specId, "career");
        Long conversationId = newConversation(specId);

        // r1 의 required 변수 career 를 사전 편집값으로 채움 → 액션 피커 없이 바로 EXECUTING
        startPlanWithInputs(conversationId, "[" + r1 + "]", "[{\"career\":\"3년\"}]");

        Long executionId = executionRepository.findAll().get(0).getId();
        // pendingInputs 없이 EXECUTING (WAITING_INPUT 아님)
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.EXECUTING);
        // 사전 편집값이 첫 레시피 userInput 에 시드됨
        assertThat(userInputValue(executionId, "career")).isEqualTo("3년");
    }

    // ── required 변수를 안 채우면 기존대로 pre-run 액션 피커(WAITING_INPUT) ──
    @Test
    void planWithoutInputs_requiredMissing_waitsForPicker() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipeWithRequiredVar("이력서 작성", specId, "career");
        Long conversationId = newConversation(specId);

        // recipeInputs 미전달 → career 미충족 → WAITING_INPUT
        startPlan(conversationId, "[" + r1 + "]");

        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.WAITING_INPUT);
    }

    // ── 레시피별 시드: 2번째 레시피의 recipeInputs 는 그 레시피 전이 시점에 시드된다 ──
    @Test
    void planWithInputs_secondRecipeSeededOnTransition() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("이력서 작성", specId); // 변수 없음
        Long r2 = newRecipeWithRequiredVar("입사지원", specId, "memo");
        Long conversationId = newConversation(specId);

        // r1 은 미편집({}), r2 는 memo 사전 편집
        startPlanWithInputs(conversationId, "[" + r1 + "," + r2 + "]",
                "[{},{\"memo\":\"지원합니다\"}]");
        Long executionId = executionRepository.findAll().get(0).getId();

        // 시작 시점엔 r1 이 RUNNING, r2 는 PENDING → r2 userInput 아직 시드 전
        assertThat(userInputValue(executionId, "memo")).isNull();

        // r1 완료 → r2 전이: 이 시점에 r2 의 사전 편집값(memo)이 시드되어 required 충족 → 논스톱
        reportRunningRecipeStep(executionId);

        assertThat(userInputValue(executionId, "memo")).isEqualTo("지원합니다");
        // memo 가 채워져 액션 피커 없이 EXECUTING 유지
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.EXECUTING);
    }

    // ── 우선순위: 사전 편집값이 발화값(initialContext)을 덮는다 (사전편집 > 발화) ──
    @Test
    void planWithInputs_prerunOverridesInitialContext() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipeWithRequiredVar("이력서 작성", specId, "career");
        Long conversationId = newConversation(specId);

        // 발화값 career=1년, 사전편집 career=3년 → 사전편집이 이김
        mockMvc.perform(post("/api/v1/conversations/{id}/plan-executions", conversationId).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"recipeIds\":[" + r1 + "]"
                                + ",\"initialContext\":{\"career\":\"1년\"}"
                                + ",\"recipeInputs\":[{\"career\":\"3년\"}]}"))
                .andExpect(status().isCreated());

        Long executionId = executionRepository.findAll().get(0).getId();
        assertThat(userInputValue(executionId, "career")).isEqualTo("3년");
    }

    // ─────────────────────────────────────────────────────────────
    // 이어서 실행 (PARTIAL 재개) — plan.md "이어서 실행"
    // ─────────────────────────────────────────────────────────────

    /** 현재 RUNNING 레시피의 첫 스텝을 FAILED 보고 (중간 실패 유발) */
    private void reportRunningRecipeStepFailed(Long executionId) throws Exception {
        ExecutionRecipe running = executionRecipeRepository
                .findByExecutionIdAndStatusOrderBySequenceAsc(executionId, ExecutionRecipeStatus.RUNNING)
                .get(0);
        ExecutionStep step = executionStepRepository
                .findByExecutionRecipeIdOrderByStepIndexAsc(running.getId()).get(0);
        mockMvc.perform(post("/api/v1/executions/{eid}/steps/{sid}", executionId, step.getId()).with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\",\"errorMessage\":\"boom\"}"))
                .andExpect(status().isOk());
    }

    // ── PENDING 보존: PARTIAL 종료 후 미실행 레시피는 PENDING(FAILED 아님)으로 남는다 ──
    @Test
    void resume_partialPreservesPendingRecipe() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("이력서 작성", specId);
        Long r2 = newRecipe("포지션 탐색", specId);
        Long r3 = newRecipe("입사지원", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "," + r3 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        reportRunningRecipeStep(executionId);       // r1 SUCCESS → r2 전이
        reportRunningRecipeStepFailed(executionId); // r2 FAILED → PARTIAL 확정

        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.PARTIAL);
        List<ExecutionRecipe> recipes = executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId);
        assertThat(recipes.get(0).getStatus()).isEqualTo(ExecutionRecipeStatus.SUCCESS);
        assertThat(recipes.get(1).getStatus()).isEqualTo(ExecutionRecipeStatus.FAILED);
        // r3는 미실행 → PENDING 보존 (재개 시작 지점 판별용)
        assertThat(recipes.get(2).getStatus()).isEqualTo(ExecutionRecipeStatus.PENDING);
    }

    // ── 재개: PARTIAL 실행 resume → 첫 미완료(FAILED) 레시피부터 RUNNING 복귀 + 스텝 재생성, 앞 SUCCESS 유지 ──
    @Test
    void resume_restartsFromFirstIncompleteRecipe() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("이력서 작성", specId);
        Long r2 = newRecipe("포지션 탐색", specId);
        Long r3 = newRecipe("입사지원", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "," + r3 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        reportRunningRecipeStep(executionId);       // r1 SUCCESS → r2 전이
        reportRunningRecipeStepFailed(executionId); // r2 FAILED → PARTIAL

        // 재개 호출
        mockMvc.perform(post("/api/v1/executions/{eid}/resume", executionId).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("RUNNING"));

        // EXECUTION RUNNING 복귀 + finishedAt 초기화
        Execution execution = executionRepository.findById(executionId).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(execution.getFinishedAt()).isNull();

        List<ExecutionRecipe> recipes = executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId);
        // r1은 SUCCESS 그대로 유지 (앞 완료 레시피 재실행 안 함)
        assertThat(recipes.get(0).getStatus()).isEqualTo(ExecutionRecipeStatus.SUCCESS);
        // r2가 재개 시작 지점 → RUNNING + 스텝 재생성
        assertThat(recipes.get(1).getStatus()).isEqualTo(ExecutionRecipeStatus.RUNNING);
        assertThat(executionStepRepository.findByExecutionRecipeIdOrderByStepIndexAsc(recipes.get(1).getId())).hasSize(1);
        // r3는 아직 PENDING
        assertThat(recipes.get(2).getStatus()).isEqualTo(ExecutionRecipeStatus.PENDING);
        // 대화방 EXECUTING + 락 유지
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.EXECUTING);
        assertThat(conversationLock.isLocked(conversationId)).isTrue();
    }

    // ── 재개 후 완주: resume 후 남은 스텝 SUCCESS 보고 → 전체 SUCCESS ──
    @Test
    void resume_thenCompletesRemaining_endsSuccess() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("이력서 작성", specId);
        Long r2 = newRecipe("포지션 탐색", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        reportRunningRecipeStep(executionId);       // r1 SUCCESS → r2 전이
        reportRunningRecipeStepFailed(executionId); // r2 FAILED → PARTIAL

        mockMvc.perform(post("/api/v1/executions/{eid}/resume", executionId).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk());

        // 재개된 r2 스텝 SUCCESS 보고 → 남은 레시피 없음 → 자동완료(SUCCESS)
        reportRunningRecipeStep(executionId);

        Execution execution = executionRepository.findById(executionId).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.IDLE);
        assertThat(conversationLock.isLocked(conversationId)).isFalse();
        List<ExecutionRecipe> recipes = executionRecipeRepository.findByExecutionIdOrderBySequenceAsc(executionId);
        assertThat(recipes).allMatch(r -> r.getStatus() == ExecutionRecipeStatus.SUCCESS);
    }

    // ── 거부: SUCCESS 실행 resume → 400 ──
    @Test
    void resume_successExecution_returns400() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("가입", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        reportRunningRecipeStep(executionId); // 자동완료(SUCCESS)
        assertThat(executionRepository.findById(executionId).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.SUCCESS);

        mockMvc.perform(post("/api/v1/executions/{eid}/resume", executionId).with(testAuth.as(USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── 락: 재개 대상 대화방이 이미 처리 중(락 점유)이면 409 ──
    @Test
    void resume_conversationBusy_returns409() throws Exception {
        Long specId = 10L;
        Long r1 = newRecipe("이력서 작성", specId);
        Long r2 = newRecipe("포지션 탐색", specId);
        Long conversationId = newConversation(specId);
        startPlan(conversationId, "[" + r1 + "," + r2 + "]");
        Long executionId = executionRepository.findAll().get(0).getId();

        reportRunningRecipeStep(executionId);       // r1 SUCCESS → r2 전이
        reportRunningRecipeStepFailed(executionId); // r2 FAILED → PARTIAL (락 해제됨)

        // 대화방을 외부에서 선점(다른 처리 중 상황 시뮬레이션)
        assertThat(conversationLock.tryLock(conversationId)).isTrue();

        mockMvc.perform(post("/api/v1/executions/{eid}/resume", executionId).with(testAuth.as(USER_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_BUSY"));

        conversationLock.unlock(conversationId);
    }
}
