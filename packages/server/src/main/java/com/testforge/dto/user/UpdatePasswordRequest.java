package com.testforge.dto.user;

/**
 * 비밀번호 지정 요청 (관리자가 새 비밀번호를 직접 지정). 최소 8자 규칙은 서비스에서 검증하고 bcrypt로 저장한다.
 * 비밀번호 변경은 기존 세션에 영향을 주지 않는다(세션 재확인은 STATUS/ROLE만).
 */
public record UpdatePasswordRequest(
        // 새 비밀번호 평문 (최소 8자 — 검증은 AdminUserService에서 수행)
        String password) {
}
