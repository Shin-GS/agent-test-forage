package com.testforge;

import com.testforge.entity.conversation.Conversation;
import com.testforge.entity.conversation.Message;
import com.testforge.entity.conversation.MessagePart;
import com.testforge.entity.conversation.enums.MessageRole;
import com.testforge.entity.conversation.enums.PartType;
import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.recipe.enums.Visibility;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.enums.SpecStatus;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.repository.conversation.ConversationRepository;
import com.testforge.repository.conversation.MessagePartRepository;
import com.testforge.repository.conversation.MessageRepository;
import com.testforge.repository.recipe.RecipeRepository;
import com.testforge.repository.spec.ApiSpecRepository;
import com.testforge.support.SyncChatExecutorTestConfig;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지목 레시피([▶], targetRecipeId) 실행 시 대화방 서비스 자동 설정/전환 회귀 방지 통합 테스트 (H2).
 *
 * <p>{@code ChatProcessor.process}는 {@code buildContext} 이전에 {@code ensureServiceForTargetRecipe}를
 * 호출해, 마지막 USER 턴의 targetRecipeId가 가리키는 레시피의 {@code apiSpecId}로 대화방 서비스를 확정한다
 * (chat/overview.md 서비스 자동 설정/전환). 정책(핵심):
 * <ol>
 *   <li>미설정 → 자동 설정 (apiSpecId 갱신 + "변경했어요" SYSTEM 안내)</li>
 *   <li>다른 서비스 → 전환 (apiSpecId 갱신 + 전환 사유 SYSTEM 안내)</li>
 *   <li>같은 서비스 → 무변경 (SYSTEM 안내/SSE 없음, 멱등 — 이게 핵심 정책)</li>
 *   <li>남의 PRIVATE 레시피(canView 실패) → 전환 스킵 (소유 격리, auth.md 수평 권한)</li>
 * </ol>
 *
 * <p>{@link SyncChatExecutorTestConfig}로 AI 처리(목 resolver)를 동기 실행해, 전환이 일어난 최종 상태를
 * 결정적으로 검증한다. 서비스 전환은 resolver 앞단(ensureServiceForTargetRecipe)에서 일어나므로
 * resolver의 tool 결과와 무관하게 apiSpecId 결과만 검증하면 된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({SyncChatExecutorTestConfig.class, TestAuthSupport.class})
class TargetRecipeServiceSwitchIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessagePartRepository messagePartRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private ApiSpecRepository apiSpecRepository;

    @Autowired
    private TestAuthSupport testAuth;

    private MockMvc mockMvc;

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 999L;

    /** 서비스 전환 SYSTEM 안내(ensureServiceForTargetRecipe)의 고정 어구(문구 일부) */
    private static final String SWITCH_NOTICE_MARK = "변경했어요";

    private Long serviceA;
    private Long serviceB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        messagePartRepository.deleteAll();
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
        recipeRepository.deleteAll();
        apiSpecRepository.deleteAll();

        testAuth.ensureUser(USER_ID, UserRole.USER);

        serviceA = saveActiveSpec("http://service-a.local", "서비스A").getId();
        serviceB = saveActiveSpec("http://service-b.local", "서비스B").getId();
    }

    // ── 1. 미설정 대화방 + 본인 소유 레시피 지목 → 자동 설정 + SYSTEM 안내 ──
    @Test
    void unsetConversation_targetOwnedRecipe_setsServiceWithNotice() throws Exception {
        Long recipeId = savePrivateRecipe(USER_ID, serviceA, "회원가입 레시피").getId();

        // apiSpecId 미설정 대화방
        Long conversationId = conversationRepository.save(new Conversation(USER_ID)).getId();

        sendTargetRecipeMessage(conversationId, recipeId, "실행");

        // 대화방 서비스가 레시피 서비스(A)로 설정됨
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getApiSpecId())
                .isEqualTo(serviceA);
        // 전환 SYSTEM 안내가 1건 생성됨
        assertThat(hasServiceSwitchNotice(conversationId)).isTrue();
    }

    // ── 2. 다른 서비스 대화방 + 레시피 지목 → 전환 + SYSTEM 안내 ──
    @Test
    void otherServiceConversation_targetRecipe_switchesServiceWithNotice() throws Exception {
        Long recipeId = savePrivateRecipe(USER_ID, serviceB, "결제 레시피").getId();

        // 서비스A로 설정된 대화방 → 레시피는 서비스B 소속
        Conversation conversation = new Conversation(USER_ID);
        conversation.setApiSpecId(serviceA);
        Long conversationId = conversationRepository.save(conversation).getId();

        sendTargetRecipeMessage(conversationId, recipeId, "실행");

        // 대화방 서비스가 레시피 서비스(B)로 전환됨
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getApiSpecId())
                .isEqualTo(serviceB);
        assertThat(hasServiceSwitchNotice(conversationId)).isTrue();
    }

    // ── 3. 같은 서비스 대화방 + 레시피 지목 → 무변경 + 전환 안내 없음 (멱등, 핵심 정책) ──
    @Test
    void sameServiceConversation_targetRecipe_isIdempotentWithoutNotice() throws Exception {
        Long recipeId = savePrivateRecipe(USER_ID, serviceA, "회원가입 레시피").getId();

        // 이미 서비스A로 설정된 대화방 (레시피도 서비스A)
        Conversation conversation = new Conversation(USER_ID);
        conversation.setApiSpecId(serviceA);
        Long conversationId = conversationRepository.save(conversation).getId();

        sendTargetRecipeMessage(conversationId, recipeId, "실행");

        // 서비스는 그대로 A
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getApiSpecId())
                .isEqualTo(serviceA);
        // 서비스 전환 안내는 생성되지 않는다(멱등). AI 처리 자체의 다른 턴은 생길 수 있으므로
        // "변경했어요" 문구를 가진 SYSTEM 턴이 없음을 검증한다.
        assertThat(hasServiceSwitchNotice(conversationId)).isFalse();
    }

    // ── 4. 남의 PRIVATE 레시피 지목(canView 실패) → 전환 스킵 (소유 격리) ──
    @Test
    void othersPrivateRecipe_targetRecipe_skipsServiceSwitch() throws Exception {
        // 다른 사용자(OTHER_USER_ID) 소유 PRIVATE 레시피 (서비스B)
        Long othersRecipeId = savePrivateRecipe(OTHER_USER_ID, serviceB, "남의 비공개 레시피").getId();

        // 요청자(USER_ID)의 미설정 대화방
        Long conversationId = conversationRepository.save(new Conversation(USER_ID)).getId();

        sendTargetRecipeMessage(conversationId, othersRecipeId, "실행");

        // 남의 PRIVATE는 canView 실패 → 서비스 전환 스킵. 대화방 서비스는 레시피 서비스(B)로 바뀌지 않는다.
        assertThat(conversationRepository.findById(conversationId).orElseThrow().getApiSpecId())
                .isNotEqualTo(serviceB);
        // 전환 안내도 생성되지 않는다.
        assertThat(hasServiceSwitchNotice(conversationId)).isFalse();
    }

    // ── 픽스처/헬퍼 ──

    private ApiSpec saveActiveSpec(String baseUrl, String name) {
        ApiSpec spec = new ApiSpec(baseUrl);
        spec.setName(name);
        spec.setStatus(SpecStatus.ACTIVE);
        return apiSpecRepository.save(spec);
    }

    private Recipe savePrivateRecipe(Long ownerUserId, Long apiSpecId, String name) {
        Recipe recipe = new Recipe(ownerUserId, apiSpecId, name);
        recipe.setVisibility(Visibility.PRIVATE);
        return recipeRepository.save(recipe);
    }

    /** targetRecipeId를 담은 사용자 메시지를 전송(동기 AI 처리까지 완료). */
    private void sendTargetRecipeMessage(Long conversationId, Long targetRecipeId, String content)
            throws Exception {
        String body = "{\"userId\":" + USER_ID
                + ",\"content\":\"" + content + "\""
                + ",\"targetRecipeId\":" + targetRecipeId + "}";
        mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId)
                        .with(testAuth.as(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    /** 대화방에 "서비스 전환 안내"(SYSTEM 턴 + "변경했어요" 문구 TEXT 파트)가 존재하는지. */
    private boolean hasServiceSwitchNotice(Long conversationId) {
        List<Message> turns = messageRepository.findByConversationIdOrderByIdAsc(conversationId);
        List<Long> systemTurnIds = turns.stream()
                .filter(m -> m.getRole() == MessageRole.SYSTEM)
                .map(Message::getId)
                .toList();
        if (systemTurnIds.isEmpty()) {
            return false;
        }
        return messagePartRepository.findByMessageIdInOrderByIdAsc(systemTurnIds).stream()
                .anyMatch(p -> p.getType() == PartType.TEXT
                        && p.getContent() != null
                        && p.getContent().contains(SWITCH_NOTICE_MARK));
    }
}
