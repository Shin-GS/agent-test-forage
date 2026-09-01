package com.testforge.scheduler;

import com.testforge.config.TestForgeServerProperties;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.enums.SpecStatus;
import com.testforge.repository.spec.ApiSpecRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * heartbeat 신선도에 따라 스펙 생명주기를 주기적으로 전이시킨다.
 * <ul>
 *   <li>ACTIVE인데 stale 기간 동안 heartbeat 없음 → STALE</li>
 *   <li>INACTIVE가 아닌 스펙이 delete 기간 동안 heartbeat 없음 → 소프트 삭제</li>
 * </ul>
 * INACTIVE 스펙은 자동 삭제 대상에서 제외한다.
 *
 * <p>프로토타입 단계에서는 기본 비활성이다.
 * {@code ai-test-forge.scheduler.enabled=true}일 때만 빈으로 등록된다.
 */
@Component
@ConditionalOnProperty(prefix = "ai-test-forge.scheduler", name = "enabled",
        havingValue = "true", matchIfMissing = false)
public class SpecLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(SpecLifecycleScheduler.class);

    private final ApiSpecRepository specRepository;
    private final TestForgeServerProperties properties;

    public SpecLifecycleScheduler(ApiSpecRepository specRepository,
                                  TestForgeServerProperties properties) {
        this.specRepository = specRepository;
        this.properties = properties;
    }

    /** 1분마다 실행 */
    @Scheduled(fixedDelay = 60_000L)
    @Transactional
    public void sweep() {
        markStale();
        softDeleteExpired();
    }

    /** stale 기간 동안 heartbeat 없는 ACTIVE 스펙을 STALE로 전이 */
    void markStale() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(properties.getStaleAfterMinutes());
        List<ApiSpec> stale = specRepository
                .findByStatusAndLastHeartbeatAtBeforeAndDeletedAtIsNull(SpecStatus.ACTIVE, threshold);
        for (ApiSpec spec : stale) {
            spec.setStatus(SpecStatus.STALE);
        }
        if (!stale.isEmpty()) {
            specRepository.saveAll(stale);
            log.info("Marked {} spec(s) STALE (no heartbeat since {})", stale.size(), threshold);
        }
    }

    /** delete 기간 동안 heartbeat 없는 스펙을 소프트 삭제 (INACTIVE 제외) */
    void softDeleteExpired() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(properties.getDeleteAfterHours());
        // INACTIVE는 자동 삭제 대상에서 제외.
        List<ApiSpec> expired = specRepository
                .findByStatusNotAndLastHeartbeatAtBeforeAndDeletedAtIsNull(SpecStatus.INACTIVE, threshold);
        LocalDateTime now = LocalDateTime.now();
        for (ApiSpec spec : expired) {
            spec.setDeletedAt(now);
        }
        if (!expired.isEmpty()) {
            specRepository.saveAll(expired);
            log.info("Soft-deleted {} spec(s) (no heartbeat since {})", expired.size(), threshold);
        }
    }
}
