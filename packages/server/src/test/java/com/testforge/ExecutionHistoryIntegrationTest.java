package com.testforge;

import com.testforge.entity.conversation.Conversation;
import com.testforge.entity.execution.Execution;
import com.testforge.entity.execution.enums.ExecutionMode;
import com.testforge.entity.execution.enums.ExecutionStatus;
import com.testforge.entity.execution.enums.ExecutionType;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.repository.conversation.ConversationRepository;
import com.testforge.repository.execution.ExecutionRepository;
import com.testforge.support.TestAuthSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실행 히스토리 커서 페이징 통합 테스트 (H2).
 *
 * <ul>
 *   <li>커서 페이징: 최신순 + size 제한 + hasNext/nextCursor + 다음 페이지 이어짐(중복 없음)</li>
 *   <li>필터: 상태 / 키워드(title)</li>
 *   <li>size 상한, 빈 목록, 대화방별 조회</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestAuthSupport.class)
class ExecutionHistoryIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private TestAuthSupport testAuth;

    private MockMvc mockMvc;
    private static final long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        executionRepository.deleteAll();
        conversationRepository.deleteAll();
        testAuth.ensureUser(USER_ID, UserRole.USER);
    }

    /**
     * 실행 레코드를 직접 만든다(start API는 대화방당 1건 락 제약이 있어 목록 테스트엔 부적합).
     * startedAt을 baseTime에서 i분씩 뒤로 두어 최신순 정렬을 결정적으로 만든다.
     */
    private Execution newExecution(String title, ExecutionStatus status, int minutesAgo) {
        Execution e = new Execution(USER_ID, ExecutionType.SINGLE, ExecutionMode.AUTO);
        e.setTitle(title);
        e.setStatus(status);
        e.setStartedAt(LocalDateTime.of(2026, 1, 1, 12, 0).minusMinutes(minutesAgo));
        return executionRepository.save(e);
    }

    // ── 커서 페이징: 최신순(id DESC) + size 제한 + 다음 페이지 이어짐 ──
    @Test
    void history_cursorPaging_walksAllPagesWithoutDuplicates() throws Exception {
        // 5건 순차 저장 → id 오름차순(실행0<...<실행4). 정렬은 id DESC라 실행4가 가장 최신.
        for (int i = 0; i < 5; i++) {
            newExecution("실행" + i, ExecutionStatus.SUCCESS, i);
        }

        // 1페이지: size=2 → 최신 2건(실행4, 실행3), hasNext=true
        String firstResp = mockMvc.perform(get("/api/v1/executions").with(testAuth.as(USER_ID))
                        .param("userId", String.valueOf(USER_ID))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("실행4"))
                .andExpect(jsonPath("$.items[1].title").value("실행3"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String cursor1 = readCursor(firstResp);

        // 2페이지: 실행2, 실행1
        String secondResp = mockMvc.perform(get("/api/v1/executions").with(testAuth.as(USER_ID))
                        .param("userId", String.valueOf(USER_ID))
                        .param("size", "2")
                        .param("cursor", cursor1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("실행2"))
                .andExpect(jsonPath("$.items[1].title").value("실행1"))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();

        String cursor2 = readCursor(secondResp);

        // 3페이지: 실행0 하나 남고 hasNext=false, nextCursor=null
        mockMvc.perform(get("/api/v1/executions").with(testAuth.as(USER_ID))
                        .param("userId", String.valueOf(USER_ID))
                        .param("size", "2")
                        .param("cursor", cursor2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("실행0"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    // ── 상태 필터 ──
    @Test
    void history_filterByStatus() throws Exception {
        newExecution("성공건", ExecutionStatus.SUCCESS, 1);
        newExecution("취소건", ExecutionStatus.CANCELLED, 2);
        newExecution("중지건", ExecutionStatus.STOPPED, 3);

        mockMvc.perform(get("/api/v1/executions").with(testAuth.as(USER_ID))
                        .param("userId", String.valueOf(USER_ID))
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("취소건"))
                .andExpect(jsonPath("$.items[0].status.code").value("CANCELLED"));
    }

    // ── 키워드 필터 (title 부분일치) ──
    @Test
    void history_filterByKeyword() throws Exception {
        newExecution("회원가입 테스트", ExecutionStatus.SUCCESS, 1);
        newExecution("주문 생성", ExecutionStatus.SUCCESS, 2);
        newExecution("회원 탈퇴", ExecutionStatus.SUCCESS, 3);

        // 정렬은 id DESC(최신 저장 우선) → "회원 탈퇴"(나중 저장)가 먼저
        mockMvc.perform(get("/api/v1/executions").with(testAuth.as(USER_ID))
                        .param("userId", String.valueOf(USER_ID))
                        .param("keyword", "회원"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("회원 탈퇴"))
                .andExpect(jsonPath("$.items[1].title").value("회원가입 테스트"));
    }

    // ── 키워드 LIKE 와일드카드 이스케이프 (% 를 리터럴로 매칭) ──
    @Test
    void history_keywordWildcardEscaped() throws Exception {
        newExecution("50% 할인 테스트", ExecutionStatus.SUCCESS, 1);
        newExecution("완전 다른 건", ExecutionStatus.SUCCESS, 2);

        // "50%"는 리터럴로 매칭되어야 함 (와일드카드로 "50"+아무거나가 아니라)
        mockMvc.perform(get("/api/v1/executions").with(testAuth.as(USER_ID))
                        .param("userId", String.valueOf(USER_ID))
                        .param("keyword", "50%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("50% 할인 테스트"));
    }

    // ── size 상한 (50 초과 요청 시 50으로 클램프) ──
    @Test
    void history_sizeClampedToMax() throws Exception {
        for (int i = 0; i < 3; i++) {
            newExecution("실행" + i, ExecutionStatus.SUCCESS, i);
        }
        // size=1000 요청해도 에러 없이 정상 응답 (전 건이 50 이하라 전부 반환)
        mockMvc.perform(get("/api/v1/executions").with(testAuth.as(USER_ID))
                        .param("userId", String.valueOf(USER_ID))
                        .param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    // ── 빈 목록 ──
    @Test
    void history_empty_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/executions").with(testAuth.as(USER_ID))
                        .param("userId", String.valueOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    // ── 미인증 → 401 (userId는 세션에서 도출하므로 쿼리 userId는 신뢰하지 않는다) ──
    // 인증 도입 후 "userId 파라미터 누락 → 400"은 성립하지 않는다(컨트롤러가 세션값 사용).
    // 본인 히스토리만 조회하는 계약의 원천이 세션이므로, 미인증 접근 거부를 검증한다.
    @Test
    void history_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/executions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ── 대화방별 조회 ──
    @Test
    void historyByConversation_returnsOnlyThatConversation() throws Exception {
        Conversation conv = new Conversation(USER_ID);
        conv.setTitle("방");
        Long conversationId = conversationRepository.save(conv).getId();

        Execution linked = newExecution("이 방 실행", ExecutionStatus.SUCCESS, 1);
        linked.setConversationId(conversationId);
        executionRepository.save(linked);
        newExecution("다른 실행", ExecutionStatus.SUCCESS, 2); // conversationId 없음

        mockMvc.perform(get("/api/v1/conversations/{id}/executions", conversationId).with(testAuth.as(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("이 방 실행"));
    }

    // ── 대화방별 조회: 없는 대화방 → 404 ──
    @Test
    void historyByConversation_unknownConversation_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/{id}/executions", 999999L).with(testAuth.as(USER_ID)))
                .andExpect(status().isNotFound());
    }

    /** 응답 JSON에서 nextCursor 값을 뽑는다(간단 파싱). */
    private String readCursor(String json) {
        int idx = json.indexOf("\"nextCursor\":\"");
        if (idx < 0) {
            return null;
        }
        int start = idx + "\"nextCursor\":\"".length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
