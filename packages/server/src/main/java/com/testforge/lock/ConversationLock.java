package com.testforge.lock;

/**
 * 대화방 단위 동시성 락. 한 대화방에서 동시에 여러 처리(메시지 접수 → AI 응답 → 실행)가
 * 겹치지 않도록 보장한다(overview.md "대화방 단위 락"). 이중 전송/중복 실행을 막는 뼈대.
 *
 * <p>이 추상화는 <b>구현 교체</b>를 전제로 한다. 단일 인스턴스에서는 인메모리
 * ({@link InMemoryConversationLock})로 충분하지만, 멀티 인스턴스 배포에서는 분산 락
 * (예: Redis SETNX 기반) 구현으로 교체할 수 있도록 서비스는 이 인터페이스에만 의존한다.
 */
public interface ConversationLock {

    /**
     * 대화방 락 획득을 시도한다. 이미 다른 처리가 점유 중이면 즉시 {@code false}를 반환한다
     * (블로킹 없음). 같은 대화방에 대한 재획득도 실패한다(재진입 불가).
     *
     * @param conversationId 대상 대화방 ID
     * @return 획득 성공 시 true, 이미 점유 중이면 false
     */
    boolean tryLock(Long conversationId);

    /**
     * 대화방 락을 해제한다. 잡고 있지 않은 대화방에 대한 호출은 no-op(멱등).
     *
     * @param conversationId 대상 대화방 ID
     */
    void unlock(Long conversationId);

    /**
     * 대화방이 현재 락으로 점유 중인지 조회한다(주로 상태 표시/테스트용).
     *
     * @param conversationId 대상 대화방 ID
     * @return 점유 중이면 true
     */
    boolean isLocked(Long conversationId);
}
