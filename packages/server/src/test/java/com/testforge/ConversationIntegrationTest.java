package com.testforge;

import com.testforge.entity.conversation.Conversation;
import com.testforge.repository.conversation.ConversationRepository;
import com.testforge.repository.conversation.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대화방/메시지 CRUD API 통합 테스트 (H2).
 * 첫 메시지로 방 생성(seq=1·제목 파생/절단)/목록(삭제제외·정렬·unread)/상세404/이름변경/
 * 읽음처리(lastReadAt 갱신)/소프트삭제/이어서 메시지 전송(seq 증가·lastMessageAt 갱신)/
 * 목록(seq순)/없는 방 404를 검증한다.
 * (SSE·락·AI 처리는 이번 스코프 아님 — 저장까지만 검증)
 */
@SpringBootTest
@ActiveProfiles("test")
class ConversationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    private MockMvc mockMvc;
    private static final long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
    }

    // ── start: 첫 메시지로 방 생성 (지정 title 사용, seq=1, IDLE) ──
    @Test
    void start_createsConversationWithFirstMessage() throws Exception {
        String body = "{\"userId\":" + USER_ID
                + ",\"content\":\"안녕하세요\",\"apiSpecId\":10,\"title\":\"회원가입 테스트\"}";

        mockMvc.perform(post("/api/v1/conversations/messages")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.conversation.title").value("회원가입 테스트"))
                .andExpect(jsonPath("$.conversation.apiSpecId").value(10))
                .andExpect(jsonPath("$.conversation.status.code").value("IDLE"))
                .andExpect(jsonPath("$.conversation.status.description").value("유휴"))
                // 첫 메시지가 방금 생겼고 아직 안 읽음(lastReadAt=null) → unread=true
                .andExpect(jsonPath("$.conversation.unread").value(true))
                .andExpect(jsonPath("$.message.seq").value(1))
                .andExpect(jsonPath("$.message.role.code").value("USER"))
                .andExpect(jsonPath("$.message.status.code").value("COMPLETED"))
                .andExpect(jsonPath("$.message.content").value("안녕하세요"));

        // 대화방이 lastMessageAt와 함께 생성됨 (빈 방 없음)
        assertThat(conversationRepository.findAll()).hasSize(1);
        assertThat(conversationRepository.findAll().get(0).getLastMessageAt()).isNotNull();
    }

    // ── start: title 미지정 → 첫 메시지 앞부분으로 임시 제목 파생 ──
    @Test
    void start_derivesTemporaryTitleFromContent() throws Exception {
        String body = "{\"userId\":" + USER_ID + ",\"content\":\"짧은 메시지\"}";

        mockMvc.perform(post("/api/v1/conversations/messages")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversation.title").value("짧은 메시지"));
    }

    // ── start: 긴 첫 메시지 → 코드포인트 안전 절단 + "…" (한글 안 깨짐) ──
    @Test
    void start_truncatesLongTitleSafely() throws Exception {
        // 한글 25자 (20자 초과) → 앞 19자 + "…"
        String longContent = "가나다라마바사아자차카타파하가나다라마바사아자차카";
        String body = "{\"userId\":" + USER_ID + ",\"content\":\"" + longContent + "\"}";

        mockMvc.perform(post("/api/v1/conversations/messages")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversation.title")
                        .value("가나다라마바사아자차카타파하가나다라마\u2026"));

        String stored = conversationRepository.findAll().get(0).getTitle();
        // 코드포인트 20자 이내로 절단됨 (한글 깨짐 없음)
        assertThat(stored.codePointCount(0, stored.length())).isLessThanOrEqualTo(20);
        assertThat(stored).endsWith("\u2026");
    }

    // ── start: userId 누락 → 400 ──
    @Test
    void start_missingUserId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── start: content 누락/공백 → 400 (빈 대화방 미생성) ──
    @Test
    void start_blankContent_returns400AndCreatesNoConversation() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        assertThat(conversationRepository.findAll()).isEmpty();
    }

    // ── list: 삭제 제외 + lastMessageAt DESC 정렬 + unread 파생값 ──
    @Test
    void list_excludesDeletedAndSortsByLastMessageDescWithUnread() throws Exception {
        // 오래된 메시지 대화방 (읽음 처리됨 → unread=false)
        Conversation older = new Conversation(USER_ID);
        older.setTitle("오래된 방");
        older.setLastMessageAt(LocalDateTime.now().minusHours(2));
        older.setLastReadAt(LocalDateTime.now().minusHours(1));
        older = conversationRepository.save(older);

        // 최신 메시지 대화방 (안 읽음 → unread=true)
        Conversation newer = new Conversation(USER_ID);
        newer.setTitle("최신 방");
        newer.setLastMessageAt(LocalDateTime.now());
        newer.setLastReadAt(LocalDateTime.now().minusHours(3));
        newer = conversationRepository.save(newer);

        // 삭제된 대화방 (목록 제외)
        Conversation deleted = new Conversation(USER_ID);
        deleted.setTitle("삭제된 방");
        deleted.setLastMessageAt(LocalDateTime.now().plusHours(1));
        deleted.setDeletedAt(LocalDateTime.now());
        conversationRepository.save(deleted);

        // 다른 사용자 대화방 (목록 제외)
        Conversation other = new Conversation(999L);
        other.setTitle("남의 방");
        other.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(other);

        mockMvc.perform(get("/api/v1/conversations").param("userId", String.valueOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("최신 방"))
                .andExpect(jsonPath("$[0].unread").value(true))
                .andExpect(jsonPath("$[1].title").value("오래된 방"))
                .andExpect(jsonPath("$[1].unread").value(false));
    }

    // ── detail: 없는 ID → 404 ──
    @Test
    void detail_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_NOT_FOUND"));
    }

    // ── detail: 삭제된 대화방 → 404 ──
    @Test
    void detail_deletedConversation_returns404() throws Exception {
        Conversation c = new Conversation(USER_ID);
        c.setDeletedAt(LocalDateTime.now());
        Long id = conversationRepository.save(c).getId();

        mockMvc.perform(get("/api/v1/conversations/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_NOT_FOUND"));
    }

    // ── updateTitle: 이름 변경 ──
    @Test
    void updateTitle_changesTitle() throws Exception {
        Long id = conversationRepository.save(new Conversation(USER_ID)).getId();

        mockMvc.perform(patch("/api/v1/conversations/{id}/title", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"바뀐 제목\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("바뀐 제목"));

        assertThat(conversationRepository.findById(id).orElseThrow().getTitle())
                .isEqualTo("바뀐 제목");
    }

    // ── markRead: lastReadAt 갱신 ──
    @Test
    void markRead_updatesLastReadAt() throws Exception {
        Conversation c = new Conversation(USER_ID);
        c.setLastMessageAt(LocalDateTime.now());
        Long id = conversationRepository.save(c).getId();
        assertThat(conversationRepository.findById(id).orElseThrow().getLastReadAt()).isNull();

        mockMvc.perform(patch("/api/v1/conversations/{id}/read", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(false));

        assertThat(conversationRepository.findById(id).orElseThrow().getLastReadAt()).isNotNull();
    }

    // ── delete: 소프트 삭제 → 목록/상세에서 사라짐 ──
    @Test
    void delete_softDeletesConversation() throws Exception {
        Long id = conversationRepository.save(new Conversation(USER_ID)).getId();

        mockMvc.perform(delete("/api/v1/conversations/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(conversationRepository.findById(id).orElseThrow().getDeletedAt()).isNotNull();

        mockMvc.perform(get("/api/v1/conversations/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ── sendMessage: 저장 + seq 증가 + lastMessageAt 갱신 ──
    @Test
    void sendMessage_storesUserMessageWithIncrementingSeq() throws Exception {
        Long id = conversationRepository.save(new Conversation(USER_ID)).getId();
        assertThat(conversationRepository.findById(id).orElseThrow().getLastMessageAt()).isNull();

        // 첫 메시지 → seq 1
        mockMvc.perform(post("/api/v1/conversations/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"content\":\"안녕하세요\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.sessionId").value(id))
                .andExpect(jsonPath("$.message.seq").value(1))
                .andExpect(jsonPath("$.message.role.code").value("USER"))
                .andExpect(jsonPath("$.message.status.code").value("COMPLETED"))
                .andExpect(jsonPath("$.message.content").value("안녕하세요"));

        // 둘째 메시지 → seq 2
        mockMvc.perform(post("/api/v1/conversations/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"content\":\"두번째\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message.seq").value(2));

        // lastMessageAt 갱신됨
        assertThat(conversationRepository.findById(id).orElseThrow().getLastMessageAt()).isNotNull();
    }

    // ── sendMessage: 빈 내용 → 400 ──
    @Test
    void sendMessage_blankContent_returns400() throws Exception {
        Long id = conversationRepository.save(new Conversation(USER_ID)).getId();

        mockMvc.perform(post("/api/v1/conversations/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    // ── sendMessage: 없는 대화방 → 404 ──
    @Test
    void sendMessage_unknownConversation_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{id}/messages", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + USER_ID + ",\"content\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_NOT_FOUND"));
    }

    // ── listMessages: seq 오름차순 ──
    @Test
    void listMessages_returnsInSeqOrder() throws Exception {
        Long id = conversationRepository.save(new Conversation(USER_ID)).getId();

        for (String content : new String[]{"첫째", "둘째", "셋째"}) {
            mockMvc.perform(post("/api/v1/conversations/{id}/messages", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":" + USER_ID + ",\"content\":\"" + content + "\"}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].seq").value(1))
                .andExpect(jsonPath("$[0].content").value("첫째"))
                .andExpect(jsonPath("$[1].seq").value(2))
                .andExpect(jsonPath("$[2].seq").value(3))
                .andExpect(jsonPath("$[2].content").value("셋째"));
    }

    // ── listMessages: 없는 대화방 → 404 ──
    @Test
    void listMessages_unknownConversation_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/{id}/messages", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_NOT_FOUND"));
    }
}
