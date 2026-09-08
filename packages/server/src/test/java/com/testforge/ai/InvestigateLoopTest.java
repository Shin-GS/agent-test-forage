package com.testforge.ai;

import com.testforge.ai.config.AiSettings;
import com.testforge.ai.connector.Connector;
import com.testforge.ai.connector.ConnectorResult;
import com.testforge.ai.openai.OpenAiClient;
import com.testforge.ai.openai.OpenAiDtos;
import com.testforge.dto.conversation.AssistantMessageDraft;
import com.testforge.dto.conversation.PartDraft;
import com.testforge.entity.conversation.enums.MessageRole;
import com.testforge.entity.conversation.enums.PartType;
import com.testforge.repository.investigation.InvestigationRepository;
import com.testforge.repository.investigation.InvestigationStepRepository;
import com.testforge.entity.investigation.Investigation;
import com.testforge.service.conversation.ConversationService;
import com.testforge.service.conversation.InvestigateLoop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InvestigateLoop 단위 테스트 (investigation.md 루프 안전장치). 스텁 OpenAiClient(스크립트된 응답)와
 * 목 ConversationService, 가짜 Connector로 루프를 검증한다:
 * <ul>
 *   <li>조회 → 재호출 → chat 종료 (references payload 저장)</li>
 *   <li>5회 조회 후 chat 강제 (tool_choice 강제 호출)</li>
 *   <li>못 찾음/AI 실패 → FE 고정 안내 폴백 + 진행 블록 failed</li>
 *   <li>미지원 source(figma 등) 스킵도 카운터 소비</li>
 *   <li>종결 보장: 어떤 경로든 completeAssistantTurn 호출(idle 복귀)</li>
 * </ul>
 * 서비스 지정(apiSpecId != null) 컨텍스트만 다룬다(미지정 hard guard는 ChatProcessor 책임).
 */
class InvestigateLoopTest {

    private static final Long CONVERSATION_ID = 100L;
    private static final Long API_SPEC_ID = 7L;
    private static final Long PROGRESS_MESSAGE_ID = 500L;

    private ScriptedOpenAiClient client;
    private ConversationService conversationService;
    private InvestigationRepository investigationRepository;
    private InvestigationStepRepository investigationStepRepository;

    @BeforeEach
    void setUp() {
        client = new ScriptedOpenAiClient();
        conversationService = mock(ConversationService.class);
        investigationRepository = mock(InvestigationRepository.class);
        investigationStepRepository = mock(InvestigationStepRepository.class);
        // INVESTIGATION 저장은 id가 채워진 레코드를 돌려주도록 스텁(투자 파트가 investigationId를 참조).
        when(investigationRepository.save(any(Investigation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // 진행 블록(INVESTIGATE 파트) 생성 → 파트 ID 반환. (conversationId, investigationId, payload, content)
        when(conversationService.createInvestigateProgressMessage(anyLong(), any(), any(), any()))
                .thenReturn(PROGRESS_MESSAGE_ID);
        // 기본: 정상 종결(AI_RESPONDING)로 간주 → 비-null 반환. finalize가 진행 블록을 done/failed로 확정.
        // 취소/폐기 케이스는 개별 테스트에서 null 반환으로 오버라이드한다.
        // 조회 답변은 진행(INVESTIGATE) 파트와 같은 턴에 append + 종결한다(completeInvestigateTurn).
        // hard-guard(서비스 미지정, 진행 블록 미생성)만 새 턴(completeAssistantTurn)으로 남긴다.
        when(conversationService.completeInvestigateTurn(anyLong(), any(), any())).thenReturn(completedView());
        when(conversationService.completeAssistantTurn(anyLong(), any())).thenReturn(completedView());
    }

    /** finalize의 non-null 분기(정상 종결)를 태우기 위한 최소 MessageResponse (필드 값은 미검증) */
    private static com.testforge.dto.conversation.MessageResponse completedView() {
        return new com.testforge.dto.conversation.MessageResponse(
                1L, CONVERSATION_ID, null, null, null, null, null, List.of());
    }

    private InvestigateLoop loop(Connector... connectors) {
        return new InvestigateLoop(client, settings(), conversationService,
                investigationRepository, investigationStepRepository, List.of(connectors));
    }

    private AiSettings settings() {
        return new AiSettings(null, "test-key", "openai/gpt-4o", "openai/gpt-4o-mini", 15, 30);
    }

    private IntentContext ctx(String utterance) {
        return new IntentContext(1L, CONVERSATION_ID, utterance, API_SPEC_ID,
                List.of(), List.of(), null, List.of());
    }

    // ── 정상 흐름: 조회 → 재호출 → chat 종료 + references payload ──

    @Test
    void queryThenChat_finalizesWithReferencesPayload() {
        // 1) AI: investigate(api_spec, "회원가입") 2) AI: chat(최종 답변)
        client.enqueueToolCall("investigate", "{\"source\":\"api_spec\",\"query\":\"회원가입\"}");
        client.enqueueToolCall("chat", "{\"message\":\"약관 동의는 필수입니다.\"}");

        Connector apiSpec = fakeConnector("api_spec", ConnectorResult.found(
                "회원가입 스키마: agreementYn 필수",
                List.of(new ConnectorResult.Reference("api_spec", "POST /api/v1/users", "/specs/7/endpoints/1"))));

        loop(apiSpec).run(ctx("회원가입 정책이 뭐야?"));

        // 진행 블록 생성 1 + running 갱신(조회 1회) + done 확정 = update 최소 2회 이상.
        verify(conversationService).createInvestigateProgressMessage(eq(CONVERSATION_ID), any(), any(), any());

        // 최종 답변: 진행 파트와 같은 턴에 append(completeInvestigateTurn). TEXT 파트 + REFERENCES 파트.
        ArgumentCaptor<AssistantMessageDraft> draft = ArgumentCaptor.forClass(AssistantMessageDraft.class);
        verify(conversationService).completeInvestigateTurn(eq(CONVERSATION_ID), eq(PROGRESS_MESSAGE_ID), draft.capture());
        AssistantMessageDraft finalDraft = draft.getValue();
        assertThat(finalDraft.role()).isEqualTo(MessageRole.ASSISTANT);
        PartDraft textPart = finalDraft.parts().stream()
                .filter(p -> p.type() == PartType.TEXT).findFirst().orElseThrow();
        assertThat(textPart.content()).contains("약관");
        PartDraft refPart = finalDraft.parts().stream()
                .filter(p -> p.type() == PartType.REFERENCES).findFirst().orElseThrow();
        assertThat(refPart.payloadJson()).contains("references")
                .contains("POST /api/v1/users").contains("/specs/7/endpoints/1");

        // done 상태로 확정하는 진행 갱신이 발행됨.
        verifyProgressStatusPublished("done");
    }

    // ── chat 즉시 반환(조회 0회) ──

    @Test
    void immediateChat_noQuery_finalizes() {
        client.enqueueToolCall("chat", "{\"message\":\"바로 답변합니다.\"}");
        Connector apiSpec = fakeConnector("api_spec", ConnectorResult.found("x", List.of()));

        loop(apiSpec).run(ctx("안녕"));

        // 조회 없이 종결. connector.query 호출 없음.
        assertThat(((FakeConnector) apiSpec).queryCount).isZero();
        verify(conversationService).completeInvestigateTurn(eq(CONVERSATION_ID), eq(PROGRESS_MESSAGE_ID), any());
    }

    // ── 5회 조회 후 chat 강제 (tool_choice 강제) ──

    @Test
    void fiveQueries_thenForcedChat() {
        // AI가 계속 investigate만 반환하도록 6번 이상 큐잉(마지막 턴은 강제 chat 응답).
        for (int i = 0; i < 6; i++) {
            client.enqueueToolCall("investigate", "{\"source\":\"api_spec\",\"query\":\"q" + i + "\"}");
        }
        // 마지막 강제 chat 호출에 대한 응답.
        client.enqueueToolCall("chat", "{\"message\":\"수집한 정보로 답합니다.\"}");

        FakeConnector apiSpec = (FakeConnector) fakeConnector("api_spec",
                ConnectorResult.found("data", List.of()));

        loop(apiSpec).run(ctx("정책 알려줘"));

        // 실제 조회는 최대 5회로 수렴.
        assertThat(apiSpec.queryCount).isEqualTo(5);
        // 마지막 턴은 tool_choice 강제 호출(forceFunction)로 이뤄졌다.
        assertThat(client.forcedChatCalls).isGreaterThanOrEqualTo(1);
        verify(conversationService).completeInvestigateTurn(eq(CONVERSATION_ID), eq(PROGRESS_MESSAGE_ID), any());
    }

    // ── 미지원 source(예: figma) 스킵도 카운터 소비 ──

    @Test
    void unsupportedSource_skippedButCountsTowardLimit() {
        // AI가 매번 figma(미등록 커넥터)를 반환 → 스킵. 5회 스킵 후 강제 chat으로 수렴해야 한다.
        for (int i = 0; i < 6; i++) {
            client.enqueueToolCall("investigate", "{\"source\":\"figma\",\"query\":\"문서" + i + "\"}");
        }
        client.enqueueToolCall("chat", "{\"message\":\"정보를 찾지 못했습니다.\"}");

        // api_spec 커넥터만 등록(figma 없음). figma는 findConnector null → 스킵.
        FakeConnector apiSpec = (FakeConnector) fakeConnector("api_spec",
                ConnectorResult.found("data", List.of()));

        loop(apiSpec).run(ctx("figma 문서 알려줘"));

        // figma는 실제 조회되지 않음(카운터는 소비되지만 커넥터 query는 호출 안 됨).
        assertThat(apiSpec.queryCount).isZero();
        // 무진전 반복도 5회로 수렴 후 강제 chat 종결.
        assertThat(client.forcedChatCalls).isGreaterThanOrEqualTo(1);
        verify(conversationService).completeInvestigateTurn(eq(CONVERSATION_ID), eq(PROGRESS_MESSAGE_ID), any());
    }

    // ── 중복 (source, query) 캐시 재사용도 카운터 소비 ──

    @Test
    void duplicateQuery_reusesCacheButCountsTowardLimit() {
        // 항상 같은 (api_spec, "회원가입") 반복 → 첫 회만 실제 조회, 이후 캐시 재사용(skipped). 5회로 수렴.
        for (int i = 0; i < 6; i++) {
            client.enqueueToolCall("investigate", "{\"source\":\"api_spec\",\"query\":\"회원가입\"}");
        }
        client.enqueueToolCall("chat", "{\"message\":\"답변\"}");

        FakeConnector apiSpec = (FakeConnector) fakeConnector("api_spec",
                ConnectorResult.found("data", List.of()));

        loop(apiSpec).run(ctx("회원가입"));

        // 동일 질의 반복이므로 실제 커넥터 조회는 1회(첫 회)만, 이후는 캐시.
        assertThat(apiSpec.queryCount).isEqualTo(1);
        assertThat(client.forcedChatCalls).isGreaterThanOrEqualTo(1);
        verify(conversationService).completeInvestigateTurn(eq(CONVERSATION_ID), eq(PROGRESS_MESSAGE_ID), any());
    }

    // ── 못 찾음: 근거 없으면 references 없이 답변(억지 인용 금지) ──

    @Test
    void notFound_finalizesWithoutReferences() {
        client.enqueueToolCall("investigate", "{\"source\":\"api_spec\",\"query\":\"없는거\"}");
        client.enqueueToolCall("chat", "{\"message\":\"정보를 찾지 못했습니다.\"}");

        Connector apiSpec = fakeConnector("api_spec", ConnectorResult.notFound("매칭 없음"));

        loop(apiSpec).run(ctx("없는 정책"));

        ArgumentCaptor<AssistantMessageDraft> draft = ArgumentCaptor.forClass(AssistantMessageDraft.class);
        verify(conversationService).completeInvestigateTurn(eq(CONVERSATION_ID), eq(PROGRESS_MESSAGE_ID), draft.capture());
        // 조회 근거가 없으므로 REFERENCES 파트 없음(순수 TEXT 파트만).
        assertThat(draft.getValue().parts()).noneMatch(p -> p.type() == PartType.REFERENCES);
    }

    // ── AI 호출 실패 → AI 비의존 폴백 + 진행 블록 failed + idle 종결 ──

    @Test
    void aiFailure_fallsBackToFixedNotice() {
        client.enqueueFailure(); // 첫 AI 호출부터 예외

        Connector apiSpec = fakeConnector("api_spec", ConnectorResult.found("x", List.of()));

        loop(apiSpec).run(ctx("정책 질문"));

        // AI 실패에도 종결 보장: 진행 블록 failed 확정 + completeInvestigateTurn(idle) 호출.
        verifyProgressStatusPublished("failed");
        verify(conversationService).completeInvestigateTurn(eq(CONVERSATION_ID), eq(PROGRESS_MESSAGE_ID), any());
    }

    // ── 비-ApiException AI 실패도 callAi에서 폴백 처리(못찾음 고정 방지) ──

    @Test
    void nonApiAiFailure_fallsBackWithoutPropagating() {
        // callAi가 RuntimeException을 삼켜 null 반환 → 잔여 시간이 남았으므로 "못 찾음" 폴백으로 종결.
        client.enqueueFailure();

        Connector apiSpec = fakeConnector("api_spec", ConnectorResult.found("x", List.of()));

        loop(apiSpec).run(ctx("정책 질문"));

        verifyProgressStatusPublished("failed");
        ArgumentCaptor<AssistantMessageDraft> draft = ArgumentCaptor.forClass(AssistantMessageDraft.class);
        verify(conversationService).completeInvestigateTurn(eq(CONVERSATION_ID), eq(PROGRESS_MESSAGE_ID), draft.capture());
        PartDraft textPart = draft.getValue().parts().stream()
                .filter(p -> p.type() == PartType.TEXT).findFirst().orElseThrow();
        assertThat(textPart.content()).isEqualTo("정보를 찾지 못했습니다.");
    }

    // ── 취소 경쟁: completeAssistantTurn이 null(폐기) 반환 시 진행 블록 done 확정 스킵 ──

    @Test
    void cancelledDuringLoop_skipsDoneFinalization() {
        client.enqueueToolCall("chat", "{\"message\":\"답변\"}");
        Connector apiSpec = fakeConnector("api_spec", ConnectorResult.found("x", List.of()));

        // 취소/중지로 이미 IDLE → 지각 결과 폐기(null 반환).
        when(conversationService.completeInvestigateTurn(anyLong(), any(), any())).thenReturn(null);

        loop(apiSpec).run(ctx("질문"));

        // 결과 확정은 시도했지만(호출됨), done 잔상 방지를 위해 done 진행 갱신은 발행되지 않는다.
        verify(conversationService).completeInvestigateTurn(eq(CONVERSATION_ID), eq(PROGRESS_MESSAGE_ID), any());
        verify(conversationService, never()).updateInvestigateProgressMessage(
                eq(CONVERSATION_ID), eq(PROGRESS_MESSAGE_ID),
                org.mockito.ArgumentMatchers.contains("\"status\":\"done\""), any());
    }

    // ── 종결 보장: 진행 블록이 반드시 종료 상태로 확정됨 ──

    @Test
    void terminationGuarantee_alwaysCompletesTurn() {
        client.enqueueToolCall("chat", "{\"message\":\"답변\"}");
        Connector apiSpec = fakeConnector("api_spec", ConnectorResult.found("x", List.of()));

        loop(apiSpec).run(ctx("질문"));

        // completeInvestigateTurn은 status가 AI_RESPONDING일 때 idle 전이 + 락 해제를 수행한다.
        verify(conversationService, times(1)).completeInvestigateTurn(eq(CONVERSATION_ID), eq(PROGRESS_MESSAGE_ID), any());
    }

    // ── 방어: 서비스 미지정 컨텍스트가 루프에 도달하면 조회 없이 종결 ──

    @Test
    void noServiceReachesLoop_releasesWithoutQuery() {
        IntentContext noService = new IntentContext(1L, CONVERSATION_ID, "질문", null,
                List.of(), List.of(), null, List.of());
        FakeConnector apiSpec = (FakeConnector) fakeConnector("api_spec",
                ConnectorResult.found("x", List.of()));

        loop(apiSpec).run(noService);

        assertThat(apiSpec.queryCount).isZero();
        // 조회 진행 블록도 만들지 않고 즉시 종결.
        verify(conversationService, never()).createInvestigateProgressMessage(anyLong(), any(), any(), any());
        verify(conversationService).completeAssistantTurn(eq(CONVERSATION_ID), any());
    }

    // ── helpers ──

    /** 특정 status 문자열을 담은 진행 payload로 update가 발행되었는지 확인 */
    private void verifyProgressStatusPublished(String status) {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(conversationService, atLeastOnce()).updateInvestigateProgressMessage(
                eq(CONVERSATION_ID), eq(PROGRESS_MESSAGE_ID), payload.capture(), any());
        assertThat(payload.getAllValues()).anyMatch(p -> p.contains("\"status\":\"" + status + "\""));
    }

    private Connector fakeConnector(String source, ConnectorResult result) {
        return new FakeConnector(source, result);
    }

    /** 고정 결과를 반환하는 가짜 커넥터. 조회 횟수를 센다. */
    private static final class FakeConnector implements Connector {
        private final String source;
        private final ConnectorResult result;
        int queryCount = 0;

        FakeConnector(String source, ConnectorResult result) {
            this.source = source;
            this.result = result;
        }

        @Override
        public String source() {
            return source;
        }

        @Override
        public ConnectorResult query(Long apiSpecId, String query) {
            queryCount++;
            return result;
        }
    }

    /**
     * 스크립트된 응답을 순서대로 내보내는 스텁 OpenAiClient. 실제 HTTP 호출 없이 chatWithTools를
     * 오버라이드한다. 마지막 턴의 tool_choice 강제(forceFunction) 호출 횟수를 별도로 센다.
     */
    private static final class ScriptedOpenAiClient extends OpenAiClient {
        private final Deque<Object> script = new ArrayDeque<>();
        int forcedChatCalls = 0;

        ScriptedOpenAiClient() {
            super(new AiSettings(null, "test-key", "openai/gpt-4o", "openai/gpt-4o-mini", 15, 30));
        }

        void enqueueToolCall(String functionName, String argumentsJson) {
            script.add(new OpenAiDtos.ToolCall("call_" + script.size(), "function",
                    new OpenAiDtos.FunctionCall(functionName, argumentsJson)));
        }

        void enqueueFailure() {
            script.add(new RuntimeException("scripted AI failure"));
        }

        @Override
        public OpenAiDtos.ChatResponse chatWithTools(String model,
                                                     List<OpenAiDtos.ChatMessage> messages,
                                                     List<OpenAiDtos.Tool> tools,
                                                     Object toolChoice) {
            // tool_choice가 특정 함수 강제(map 형태)면 마지막 턴 chat 강제로 간주.
            if (toolChoice instanceof java.util.Map) {
                forcedChatCalls++;
            }
            Object next = script.poll();
            if (next == null) {
                // 스크립트 소진 시(예상 밖 추가 호출) chat으로 종료 유도.
                return response(new OpenAiDtos.ToolCall("call_end", "function",
                        new OpenAiDtos.FunctionCall("chat", "{\"message\":\"종료\"}")));
            }
            if (next instanceof RuntimeException ex) {
                throw ex;
            }
            return response((OpenAiDtos.ToolCall) next);
        }

        private OpenAiDtos.ChatResponse response(OpenAiDtos.ToolCall toolCall) {
            List<OpenAiDtos.ToolCall> calls = new ArrayList<>();
            calls.add(toolCall);
            OpenAiDtos.ResponseMessage message = new OpenAiDtos.ResponseMessage(null, calls);
            return new OpenAiDtos.ChatResponse(List.of(new OpenAiDtos.Choice(message)));
        }
    }
}
