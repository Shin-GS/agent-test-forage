package com.testforge.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 서버 상태 확인용 헬스체크 엔드포인트.
 * 뼈대 검증 및 배포 후 기동 확인에 사용.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "ai-test-forge-server",
                "timestamp", Instant.now().toString()
        );
    }
}
