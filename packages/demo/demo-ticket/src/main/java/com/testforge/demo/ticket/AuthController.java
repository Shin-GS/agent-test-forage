package com.testforge.demo.ticket;

import com.testforge.client.annotation.TestForgeExclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 로그인 API. 성공 시 {@code TICKET_SESSION} 쿠키를 발급한다.
 *
 * <p>사용자가 직접 로그인하는 용도이며 레시피/AI가 호출하지 않으므로
 * {@link TestForgeExclude}로 AI 노출에서 제외한다.
 */
@RestController
@Tag(name = "인증", description = "로그인 API (사용자 전용)")
@TestForgeExclude(reason = "사용자 로그인 전용 API. 레시피/AI가 직접 호출하지 않는다.")
public class AuthController {

    /** 세션 쿠키 이름 */
    static final String SESSION_COOKIE = "TICKET_SESSION";

    private final TicketStore store;

    public AuthController(TicketStore store) {
        this.store = store;
    }

    @Operation(summary = "로그인", description = "username/password로 로그인한다. 성공 시 TICKET_SESSION 세션 쿠키를 발급한다. 실패 시 401.")
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

    /**
     * 로그인 페이지. 인증 프로필의 loginPageUrl(경로 /login-page)이 가리키는 화면이다.
     * 데모 계정(demo/demo1234)이 미리 채워진 폼을 제공하고, [로그인] 클릭 시 POST /login을
     * 호출해 TICKET_SESSION 쿠키를 발급받는다(same-origin이라 브라우저가 Set-Cookie를 자동 저장).
     */
    @GetMapping(value = "/login-page", produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage() {
        return LOGIN_PAGE_HTML;
    }

    /** 로그인 요청 모델 */
    public record LoginRequest(String username, String password) {
    }

    /** 로그인 페이지 HTML. 데모 계정이 미리 채워진 폼 + [로그인] 버튼 + 상태 메시지. */
    private static final String LOGIN_PAGE_HTML = """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>demo-ticket 로그인</title>
              <style>
                body { font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
                       background: #f4f5f7; display: flex; align-items: center;
                       justify-content: center; min-height: 100vh; margin: 0; }
                .card { background: #fff; padding: 32px; border-radius: 12px;
                        box-shadow: 0 4px 16px rgba(0,0,0,0.08); width: 320px; }
                h1 { font-size: 18px; margin: 0 0 4px; }
                p.sub { color: #6b7280; font-size: 13px; margin: 0 0 20px; }
                label { display: block; font-size: 13px; color: #374151; margin: 12px 0 4px; }
                input { width: 100%; box-sizing: border-box; padding: 10px 12px;
                        border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; }
                button { width: 100%; margin-top: 20px; padding: 11px; border: none;
                         border-radius: 8px; background: #6366f1; color: #fff; font-size: 15px;
                         font-weight: 600; cursor: pointer; }
                button:hover { background: #4f46e5; }
                button:disabled { background: #a5b4fc; cursor: default; }
                .msg { margin-top: 16px; font-size: 14px; text-align: center; min-height: 20px; }
                .msg.success { color: #16a34a; font-weight: 600; }
                .msg.error { color: #dc2626; }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>demo-ticket 로그인</h1>
                <p class="sub">이슈 트래커 데모 서버</p>
                <form id="loginForm">
                  <label for="username">아이디</label>
                  <input id="username" name="username" type="text" value="demo" autocomplete="username" />
                  <label for="password">비밀번호</label>
                  <input id="password" name="password" type="password" value="demo1234" autocomplete="current-password" />
                  <button id="submitBtn" type="submit">로그인</button>
                </form>
                <div id="msg" class="msg" role="status" aria-live="polite"></div>
              </div>
              <script>
                const form = document.getElementById("loginForm");
                const btn = document.getElementById("submitBtn");
                const msg = document.getElementById("msg");
                form.addEventListener("submit", async (e) => {
                  e.preventDefault();
                  btn.disabled = true;
                  msg.className = "msg";
                  msg.textContent = "로그인 중...";
                  try {
                    const res = await fetch("/login", {
                      method: "POST",
                      headers: { "Content-Type": "application/json" },
                      credentials: "same-origin",
                      body: JSON.stringify({
                        username: document.getElementById("username").value,
                        password: document.getElementById("password").value,
                      }),
                    });
                    if (res.ok) {
                      msg.className = "msg success";
                      msg.textContent = "로그인 되었습니다";
                      form.style.display = "none";
                    } else {
                      const data = await res.json().catch(() => ({}));
                      msg.className = "msg error";
                      msg.textContent = data.message || "로그인에 실패했습니다";
                      btn.disabled = false;
                    }
                  } catch (err) {
                    msg.className = "msg error";
                    msg.textContent = "요청 중 오류가 발생했습니다";
                    btn.disabled = false;
                  }
                });
              </script>
            </body>
            </html>
            """;
}
