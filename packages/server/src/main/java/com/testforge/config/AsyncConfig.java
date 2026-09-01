package com.testforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 실행 설정. 사용자 메시지 접수 후 AI 처리를 요청 스레드와 분리해 별도 스레드풀에서 돌린다
 * (전송 API 즉시 리턴 → 낙관적 UI, messaging.md).
 *
 * <p>{@code chatTaskExecutor}는 {@link com.testforge.service.conversation.ChatRequestedListener}가
 * {@code @Async("chatTaskExecutor")}로 사용한다. 큐가 가득 차면 {@code CallerRunsPolicy}로
 * 호출 스레드에서 실행하여 작업 유실을 막는다(대화방 락이 이미 폭주를 억제하므로 드문 상황).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    // test 프로파일에서는 등록하지 않는다. 통합 테스트는 동기 실행기(SyncChatExecutorTestConfig)로
    // 대체하여 커밋 후 AI 처리를 결정적으로 검증한다.
    @Bean("chatTaskExecutor")
    @Profile("!test")
    public Executor chatTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-ai-");
        // 큐 포화 시 유실 대신 호출 스레드에서 실행 (best-effort backpressure)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 종료 시 진행 중 작업을 마무리하도록 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
