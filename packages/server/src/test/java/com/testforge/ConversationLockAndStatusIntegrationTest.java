package com.testforge;

import com.testforge.entity.conversation.Conversation;
import com.testforge.entity.conversation.enums.ConversationStatus;
import com.testforge.lock.ConversationLock;
import com.testforge.repository.conversation.ConversationRepository;
import com.testforge.repository.conversation.MessageRepository;
import com.testforge.service.conversation.ConversationService;
import com.testforge.sse.SseEvent;
import com.testforge.sse.SseEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대화방 단위 락 + 취소/중지 API + session_status 전이 + 서버 기동 복구 통합 테스트 (H2).
 *
 * <ul>
 *   <li>락 경합: 수동 tryLock 후 sendMessage → 409 CONVERSATION_BUSY, 해제 후 정상 접수</li>
 *   <li>취소/중지 멱등: 진행 중 → IDLE 전이 + session_status(idle) 발행, 재호출은 no-op(200)</li>
 *   <li>없는 대화방: 취소/중지 404</li>
 *   <li>transitionStatus: session_status SSE 발행</li>
 *   <li>기동 복구: AI_RESPONDING/EXECUTING 대화방을 IDLE로 정리</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class ConversationLockAndStatusIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationLock conversationLock;

    // SSE 발행 검증을 위해 publisher를 spy로 대체 (실제 발행 로직 유지 + 호출 캡처)
    @MockitoSpyBean
    private SseEventPublisher ssePublisher;

    private MockMvc mockMvc;
    private static final long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
    }

    private Long newConversation(ConversationStatus status) {
        Conversation c = new Conversation(USER_ID);
        c.setTitle("방");
        c.setStatus(status);
        return conversationRepository.save(c).getId();
    }

    // ── 락 경합: 이미 락 잡힌 대화방에 메시지 전송 → 409, 해제 후 정상 ──
    @Test
    void sendMessage_whenLocked_returns409_thenSucceedsAfterUnlock() throws Exception {
        Long id = newConversation(ConversationStatus.IDLE);

        // 다른 처리가 점유 중인 상황을 수동 락으로 시뮬레이션
        assertThat(conversationLock.tryLock(id)).isTrue();

        mockMvc.perform(post("/api/v1/conversations/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"content\":\"처리중 전송\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_BUSY"));

        // 점유 해제 후에는 정상 접수
        conversationLock.unlock(id);
        mockMvc.perform(post("/api/v1/conversations/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"content\":\"이제 됨\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message.seq").value(1));

        // 정상 접수 후 락은 해제된 상태여야 함(짧은 점유)
        assertThat(conversationLock.isLocked(id)).isFalse();
    }

    // ── 취소: 진행 중 → IDLE 전이 + session_status(idle) 발행 + 재호출 멱등(no-op) ──
    @Test
    void cancel_transitionsToIdle_andIsIdempotent() throws Exception {
        Long id = newConversation(ConversationStatus.WAITING_INPUT);

        mockMvc.perform(post("/api/v1/conversations/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("IDLE"));

        assertThat(conversationRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.IDLE);

        // 두 번째 호출도 200 (이미 IDLE → 멱등 no-op)
        mockMvc.perform(post("/api/v1/conversations/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("IDLE"));
    }

    // ── 중지: 진행 중 → IDLE 전이 + 안내 메시지 저장 ──
    @Test
    void stop_transitionsToIdle_andStoresSystemNotice() throws Exception {
        Long id = newConversation(ConversationStatus.EXECUTING);

        mockMvc.perform(post("/api/v1/conversations/{id}/stop", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value("IDLE"));

        // 시스템 안내 메시지가 저장됨
        assertThat(messageRepository.findByConversationIdOrderBySeqAsc(id)).hasSize(1);
        assertThat(messageRepository.findByConversationIdOrderBySeqAsc(id).get(0).getContent())
                .contains("중지");
    }

    // ── 취소/중지: 없는 대화방 → 404 ──
    @Test
    void cancel_unknownConversation_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{id}/cancel", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_NOT_FOUND"));
    }

    @Test
    void stop_unknownConversation_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{id}/stop", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_NOT_FOUND"));
    }

    // ── transitionStatus: session_status SSE 발행 검증 ──
    @Test
    void transitionStatus_publishesSessionStatusEvent() {
        Long id = newConversation(ConversationStatus.IDLE);

        conversationService.transitionStatus(id, ConversationStatus.AI_RESPONDING);

        assertThat(conversationRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.AI_RESPONDING);

        // publisher.toUser(userId, event)로 session_status가 발행됨
        ArgumentCaptor<SseEvent> captor = ArgumentCaptor.forClass(SseEvent.class);
        verify(ssePublisher, atLeastOnce()).toUser(eq(USER_ID), captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(e -> "session_status".equals(e.type()));
    }

    // ── transitionStatus: 같은 상태로 전이는 멱등 no-op (발행 없음) ──
    @Test
    void transitionStatus_sameStatus_isNoOp() {
        Long id = newConversation(ConversationStatus.IDLE);

        conversationService.transitionStatus(id, ConversationStatus.IDLE);

        assertThat(conversationRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.IDLE);
    }

    // ── 기동 복구: AI_RESPONDING/EXECUTING → IDLE, IDLE/삭제는 그대로 ──
    @Test
    void recoverInProgressConversations_resetsStuckToIdle() {
        Long aiResponding = newConversation(ConversationStatus.AI_RESPONDING);
        Long executing = newConversation(ConversationStatus.EXECUTING);
        Long idle = newConversation(ConversationStatus.IDLE);

        // 삭제된 진행 중 방은 복구 대상에서 제외되어야 함
        Conversation deleted = new Conversation(USER_ID);
        deleted.setStatus(ConversationStatus.EXECUTING);
        deleted.setDeletedAt(java.time.LocalDateTime.now());
        Long deletedId = conversationRepository.save(deleted).getId();

        int recovered = conversationService.recoverInProgressConversations();

        assertThat(recovered).isEqualTo(2);
        assertThat(conversationRepository.findById(aiResponding).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.IDLE);
        assertThat(conversationRepository.findById(executing).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.IDLE);
        assertThat(conversationRepository.findById(idle).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.IDLE);
        // 삭제 방은 상태 유지(복구 대상 아님)
        assertThat(conversationRepository.findById(deletedId).orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.EXECUTING);
    }
}
