package com.testforge.dto.auth;

/**
 * 로그인 요청. 아이디 + 비밀번호로 검증하며, 성공 시 세션이 생성되고 세션 쿠키가 발급된다(auth.md).
 */
public record LoginRequest(
        // 로그인 아이디
        String username,
        // 평문 비밀번호 (서버가 bcrypt로 검증)
        String password) {
}
