package com.testforge.controller.spec;

import com.testforge.dto.spec.RegisterRequest;
import com.testforge.dto.spec.RegisterResponse;
import com.testforge.service.spec.SpecRegistrationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 라이브러리로부터 스펙 등록 요청을 수신한다.
 * 등록은 기동당 1회 upsert이며, 유효한 X-TestForge-Token 헤더를 요구한다.
 */
@RestController
@RequestMapping("/api/v1/specs")
public class SpecRegistrationController {

    private static final String TOKEN_HEADER = "X-TestForge-Token";

    private final RegisterTokenValidator tokenValidator;
    private final SpecRegistrationService registrationService;

    public SpecRegistrationController(RegisterTokenValidator tokenValidator,
                                      SpecRegistrationService registrationService) {
        this.tokenValidator = tokenValidator;
        this.registrationService = registrationService;
    }

    /** 전체 스펙 등록 (토큰 검증 후 처리) */
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RegisterResponse register(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @RequestBody RegisterRequest request) {
        tokenValidator.validate(token);
        return registrationService.register(request);
    }
}
