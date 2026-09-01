package com.testforge.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 통합 테스트용: {@code chatTaskExecutor}를 동기 실행기로 오버라이드한다.
 *
 * <p>운영에서는 AI 처리가 별도 스레드({@code @Async})에서 돌지만, 테스트에서 비동기로 두면
 * 커밋 후 리스너가 다른 스레드에서 실행되어 검증 시점과 경쟁 조건이 생긴다.
 * {@link SyncTaskExecutor}로 바꾸면 {@code @TransactionalEventListener(AFTER_COMMIT)}가
 * 커밋 직후 <b>같은 스레드에서 동기 실행</b>되어, AI 처리(목 resolver)까지 마친 결정적 상태를
 * 검증할 수 있다. 실제 흐름(락 → AI → 종결)을 그대로 태운다.
 *
 * <p>사용: 통합 테스트 클래스에 {@code @Import(SyncChatExecutorTestConfig.class)}.
 */
@TestConfiguration
public class SyncChatExecutorTestConfig {

    @Bean("chatTaskExecutor")
    public Executor chatTaskExecutor() {
        return new SyncTaskExecutor();
    }
}
