package com.testforge.service.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link ChatRequestedEvent}를 <b>메시지 저장 트랜잭션 커밋 이후</b> 비동기로 받아
 * {@link ChatProcessor}를 구동한다.
 *
 * <ul>
 *   <li>{@code @TransactionalEventListener(AFTER_COMMIT)}: 사용자 메시지가 실제로 커밋된 뒤에만
 *       처리를 시작한다. AI가 아직 안 보이는(미커밋) 발화를 읽는 경합을 막는다.</li>
 *   <li>{@code fallbackExecution = true}: 활성 트랜잭션이 <b>없을 때</b>도 이벤트를 즉시 처리한다.
 *       기본값(false)이면 트랜잭션 밖 발행 시 이벤트가 조용히 무시되어 AI 처리가 영영 안 돌고
 *       대화방이 {@code ai_responding}에 갇힌다. 이 안전망으로 그 취약성을 원천 차단한다.</li>
 *   <li>{@code @Async}: 별도 스레드에서 실행하여 전송 API는 즉시 리턴한다(낙관적 UI, messaging.md).</li>
 * </ul>
 *
 * <p>처리 실패는 ChatProcessor 내부에서 대화방을 idle로 종결하며 흡수한다(종결 보장).
 * 여기서는 리스너 진입/이탈만 로깅한다.
 */
@Component
public class ChatRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(ChatRequestedListener.class);

    private final ChatProcessor chatProcessor;

    public ChatRequestedListener(ChatProcessor chatProcessor) {
        this.chatProcessor = chatProcessor;
    }

    @Async("chatTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChatRequested(ChatRequestedEvent event) {
        log.debug("Chat processing triggered: conversationId={}, userId={}",
                event.conversationId(), event.userId());
        chatProcessor.process(event.conversationId(), event.userId());
    }
}
