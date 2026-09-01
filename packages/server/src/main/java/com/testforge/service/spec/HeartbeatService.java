package com.testforge.service.spec;

import com.testforge.dto.spec.HeartbeatRequest;
import com.testforge.dto.spec.HeartbeatResponse;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.enums.SpecStatus;
import com.testforge.repository.spec.ApiSpecRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 가벼운 heartbeat를 처리한다. 클라이언트가 보낸 스펙 해시를 저장된 해시와 비교하여,
 * 일치하면 LAST_HEARTBEAT_AT를 갱신하고 "none"을 반환한다. 불일치이거나 미등록
 * baseUrl이면 "resend"를 반환하여 라이브러리가 재등록하도록 한다.
 * STALE 상태의 스펙은 일치하는 heartbeat 수신 시 ACTIVE로 복귀한다.
 */
@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    private final ApiSpecRepository specRepository;

    public HeartbeatService(ApiSpecRepository specRepository) {
        this.specRepository = specRepository;
    }

    @Transactional
    public HeartbeatResponse heartbeat(HeartbeatRequest request) {
        if (request.baseUrl() == null || request.baseUrl().isBlank()) {
            return HeartbeatResponse.resend();
        }

        Optional<ApiSpec> found = specRepository.findByBaseUrlAndDeletedAtIsNull(request.baseUrl());
        if (found.isEmpty()) {
            log.debug("Heartbeat for unknown baseUrl={} → resend", request.baseUrl());
            return HeartbeatResponse.resend();
        }

        ApiSpec spec = found.get();
        if (!Objects.equals(spec.getSpecHash(), request.specHash())) {
            log.debug("Heartbeat hash mismatch for baseUrl={} → resend", request.baseUrl());
            return HeartbeatResponse.resend();
        }

        spec.setLastHeartbeatAt(LocalDateTime.now());
        if (spec.getStatus() == SpecStatus.STALE) {
            spec.setStatus(SpecStatus.ACTIVE);
        }
        specRepository.save(spec);
        return HeartbeatResponse.none();
    }
}
