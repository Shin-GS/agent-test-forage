package com.testforge.demo.shop;

import com.testforge.client.annotation.TestForgeExclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 로그인 API. 성공 시 {@code SHOP_SESSION} 쿠키를 발급한다.
 *
 * <p>사용자가 직접 로그인하는 용도이며 레시피/AI가 호출하지 않으므로
 * {@link TestForgeExclude}로 AI 노출에서 제외한다.
 */
@RestController
@Tag(name = "인증", description = "로그인 API (사용자 전용)")
@TestForgeExclude(reason = "사용자 로그인 전용 API. 레시피/AI가 직접 호출하지 않는다.")
public class AuthController {

    /** 세션 쿠키 이름 */
    static final String SESSION_COOKIE = "SHOP_SESSION";

    private final ShopStore store;

    public AuthController(ShopStore store) {
        this.store = store;
    }

    @Operation(summary = "로그인", description = "username/password로 로그인한다. 성공 시 SHOP_SESSION 세션 쿠키를 발급한다. 실패 시 401.")
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        if (!store.authenticate(request.username(), request.password())) {
            return ResponseEntity.status(401).body(Map.of("message", "아이디 또는 비밀번호가 올바르지 않습니다."));
        }

        String token = store.createSession();
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, token)
                .path("/")
                .httpOnly(true)
                .sameSite("Lax")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("message", "로그인 성공"));
    }

    /** 로그인 요청 모델 */
    public record LoginRequest(String username, String password) {
    }
}
