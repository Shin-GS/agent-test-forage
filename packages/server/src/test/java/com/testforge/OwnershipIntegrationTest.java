package com.testforge;

import com.testforge.entity.conversation.Conversation;
import com.testforge.entity.execution.Execution;
import com.testforge.entity.execution.enums.ExecutionMode;
import com.testforge.entity.execution.enums.ExecutionStatus;
import com.testforge.entity.execution.enums.ExecutionType;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.repository.conversation.ConversationRepository;
import com.testforge.repository.conversation.MessageRepository;
import com.testforge.repository.execution.ExecutionRepository;
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

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 리소스 소유권(IDOR) 통합 테스트 (H2).
 *
 * <p>인증 도입 이후 컨트롤러는 세션에서 userId를 도출하고, 서비스는 리소스의 소유자와 요청자를
 * 비교하여 타인 소유 리소스에 대해서는 <b>존재를 노출하지 않고 404</b>로 응답한다(auth.md 수평 권한).
 *
 * <p>사용자 A(id=1)와 B(id=2)를 ACTIVE로 심고, A 소유 리소스를 B 인증으로 접근하면 404,
 * 본인(A) 인증으로 접근하면 정상(200/정상 흐름)임을 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestAuthSupport.class)
class OwnershipIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private TestAuthSupport testAuth;

    private MockMvc mockMvc;
    private static final long USER_A = 1L;
    private static final long USER_B = 2L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        messageRepository.deleteAll();
        executionRepository.deleteAll();
        conversationRepository.deleteAll();
        testAuth.ensureUser(USER_A, UserRole.USER);
        testAuth.ensureUser(USER_B, UserRole.USER);
    }

    /** A 소유 대화방을 만든다. */
    private Long conversationOwnedByA() {
        Conversation c = new Conversation(USER_A);
        c.setTitle("A의 방");
        c.setLastMessageAt(LocalDateTime.now());
        return conversationRepository.save(c).getId();
    }

    /** A 소유 실행을 만든다. */
    private Long executionOwnedByA() {
        Execution e = new Execution(USER_A, ExecutionType.SINGLE, ExecutionMode.AUTO);
        e.setTitle("A의 실행");
        e.setStatus(ExecutionStatus.SUCCESS);
        e.setStartedAt(LocalDateTime.now());
        return executionRepository.save(e).getId();
    }

    // ── 대화방 상세: B 접근 → 404, A 접근 → 200 ──
    @Test
    void conversationDetail_otherUser404_owner200() throws Exception {
        Long id = conversationOwnedByA();

        // B가 남의 방을 조회 → 존재 노출 없이 404
        mockMvc.perform(get("/api/v1/conversations/{id}", id).with(testAuth.as(USER_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_NOT_FOUND"));

        // A 본인은 정상 조회
        mockMvc.perform(get("/api/v1/conversations/{id}", id).with(testAuth.as(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    // ── 대화방 메시지 목록: B 접근 → 404, A 접근 → 200 ──
    @Test
    void conversationMessages_otherUser404_owner200() throws Exception {
        Long id = conversationOwnedByA();

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", id).with(testAuth.as(USER_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", id).with(testAuth.as(USER_A)))
                .andExpect(status().isOk());
    }

    // ── 대화방 이름 변경: B 접근 → 404, A 접근 → 200 ──
    @Test
    void conversationUpdateTitle_otherUser404_owner200() throws Exception {
        Long id = conversationOwnedByA();

        mockMvc.perform(patch("/api/v1/conversations/{id}/title", id).with(testAuth.as(USER_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"침입\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_NOT_FOUND"));

        mockMvc.perform(patch("/api/v1/conversations/{id}/title", id).with(testAuth.as(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"내 방 이름\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("내 방 이름"));
    }

    // ── 대화방 삭제: B 접근 → 404 (삭제되지 않음), A 접근 → 204 ──
    @Test
    void conversationDelete_otherUser404_owner204() throws Exception {
        Long id = conversationOwnedByA();

        mockMvc.perform(delete("/api/v1/conversations/{id}", id).with(testAuth.as(USER_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_NOT_FOUND"));

        // 타인 삭제 시도로 실제 삭제되지 않았어야 한다
        org.assertj.core.api.Assertions
                .assertThat(conversationRepository.findById(id).orElseThrow().getDeletedAt())
                .isNull();

        // A 본인 삭제는 정상
        mockMvc.perform(delete("/api/v1/conversations/{id}", id).with(testAuth.as(USER_A)))
                .andExpect(status().isNoContent());
    }

    // ── 대화방 실행 시작: B가 A의 방에 실행 생성 → 404 (남의 방에 실행 생성 방지) ──
    @Test
    void startExecutionOnOthersConversation_returns404() throws Exception {
        Long id = conversationOwnedByA();

        mockMvc.perform(post("/api/v1/conversations/{id}/executions", id).with(testAuth.as(USER_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_NOT_FOUND"));
    }

    // ── 실행 상세: B 접근 → 404, A 접근 → 200 ──
    @Test
    void executionDetail_otherUser404_owner200() throws Exception {
        Long id = executionOwnedByA();

        mockMvc.perform(get("/api/v1/executions/{id}", id).with(testAuth.as(USER_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/executions/{id}", id).with(testAuth.as(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }
}
