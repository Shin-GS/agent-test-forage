package com.testforge.client;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * 주기적으로 heartbeat를 ai-test-forge에 전송.
 * heartbeatInterval(기본 30초) 주기. 해시만 전송하고, 변경 시 서버가 재등록을 요청한다.
 */
public class SpecPushScheduler {

    private final SpecRegistrationService registrationService;

    public SpecPushScheduler(SpecRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @Scheduled(fixedDelayString = "${ai-test-forge.heartbeat-interval:30000}")
    public void heartbeat() {
        registrationService.sendHeartbeat();
    }
}
