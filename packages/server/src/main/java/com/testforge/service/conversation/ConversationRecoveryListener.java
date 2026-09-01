package com.testforge.service.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버 기동 복구 리스너. 애플리케이션이 완전히 뜬 뒤({@link ApplicationReadyEvent})
 * {@code AI_RESPONDING}/{@code EXECUTING}로 남은 대화방을 IDLE로 정리한다.
 *
 * <p>인메모리 락은 재시작 시 사라지므로, 상태만 진행 중으로 굳어 있으면 대화방이 영구히 잠긴 것처럼
 * 보인다. 기동 시 이를 해제하여 락과 상태의 불일치를 막는다(messaging.md 종결 보장 / 서버 기동 복구).
 * 실제 정리 로직은 {@link ConversationService#recoverInProgressConversations()}가 트랜잭션으로 수행한다.
 */
@Component
public class ConversationRecoveryListener {

    private static final Logger log = LoggerFactory.getLogger(ConversationRecoveryListener.class);

    private final ConversationService conversationService;

    public ConversationRecoveryListener(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        int recovered = conversationService.recoverInProgressConversations();
        log.info("Startup recovery completed: {} conversation(s) reset to IDLE", recovered);
    }
}
