package com.testforge.lock;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ConversationLock}의 인메모리 구현.
 *
 * <p><b>단일 인스턴스 전용.</b> 락 상태를 프로세스 메모리(ConcurrentHashMap 기반 Set)에만
 * 보관하므로, 멀티 인스턴스로 확장하면 인스턴스 간 상호배제가 보장되지 않는다. 그 경우
 * Redis 등 외부 저장소 기반의 분산 락(RedisConversationLock 등)으로 교체해야 한다.
 *
 * <p>재시작 시 락 상태는 사라진다. 따라서 기동 시점에 {@code AI_RESPONDING}/{@code EXECUTING}로
 * 남은 대화방 상태를 {@code IDLE}로 정리해야 락과 상태의 불일치를 막을 수 있다(messaging.md 종결 보장).
 * 이 정리는 {@link com.testforge.service.conversation.ConversationRecoveryListener}가 담당한다.
 */
@Component
public class InMemoryConversationLock implements ConversationLock {

    /** 현재 점유 중인 대화방 ID 집합 (thread-safe). value는 사용하지 않는다. */
    private final Set<Long> locked = ConcurrentHashMap.newKeySet();

    @Override
    public boolean tryLock(Long conversationId) {
        if (conversationId == null) {
            return false;
        }
        // add()가 true면 새로 추가됨(획득 성공), false면 이미 존재(점유 중)
        return locked.add(conversationId);
    }

    @Override
    public void unlock(Long conversationId) {
        if (conversationId == null) {
            return;
        }
        locked.remove(conversationId);
    }

    @Override
    public boolean isLocked(Long conversationId) {
        if (conversationId == null) {
            return false;
        }
        return locked.contains(conversationId);
    }
}
