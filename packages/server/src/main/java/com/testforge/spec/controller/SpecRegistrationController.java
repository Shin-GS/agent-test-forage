package com.testforge.spec.controller;

import com.testforge.spec.dto.HeartbeatRequest;
import com.testforge.spec.dto.HeartbeatResponse;
import com.testforge.spec.dto.RegisterRequest;
import com.testforge.spec.dto.RegisterResponse;
import com.testforge.spec.service.HeartbeatService;
import com.testforge.spec.service.SpecRegistrationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 라이브러리로부터 스펙 등록/heartbeat 요청을 수신한다.
 * 두 엔드포인트 모두 유효한 X-TestForge-Token 헤더를 요구한다.
 */
@RestController
@RequestMapping("/api/v1/specs")
public class SpecRegistrationController {

    private static final String TOKEN_HEADER = "X-TestForge-Token";

    private final RegisterTokenValidator tokenValidator;
    private final SpecRegistrationService registrationService;
    private final HeartbeatService heartbeatService;

    public SpecRegistrationController(RegisterTokenValidator tokenValidator,
                                      SpecRegistrationService registrationService,
                                      HeartbeatService heartbeatService) {
        this.tokenValidator = tokenValidator;
        this.registrationService = registrationService;
        this.heartbeatService = heartbeatService;
    }

    /** 전체 스펙 등록 (토큰 검증 후 처리) */
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RegisterResponse register(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @RequestBody RegisterRequest request) {
        tokenValidator.validate(token);
        return registrationService.register(request);
    }

    /** Heartbeat (해시만, 토큰 검증 후 처리) */
    @PostMapping(value = "/heartbeat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public HeartbeatResponse heartbeat(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @RequestBody HeartbeatRequest request) {
        tokenValidator.validate(token);
        return heartbeatService.heartbeat(request);
    }
}
