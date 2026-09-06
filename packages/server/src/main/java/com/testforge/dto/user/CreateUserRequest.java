package com.testforge.dto.user;

import com.testforge.entity.user.enums.UserRole;

/**
 * 사용자 생성 요청 (admin.md 사용자 추가 모달).
 *
 * <p>모든 검증(username trim 후 3~50자·공백 불가, 중복, password 8자, name 100자, null/blank 방어)은
 * 일관성을 위해 {@code AdminUserService}에서 명시 검증하고 {@link com.testforge.common.error.ApiException}으로 처리한다.
 */
public record CreateUserRequest(
        // 로그인 아이디 (trim 후 3~50자, 공백 불가 — 검증은 AdminUserService에서 수행)
        String username,
        // 비밀번호 평문 (최소 8자 — 검증은 AdminUserService에서 수행, 저장은 bcrypt)
        String password,
        // 표시 이름 (선택, 입력 시 100자 이하 — 검증은 AdminUserService에서 수행)
        String name,
        // 역할 (USER / ADMIN — 검증은 AdminUserService에서 수행)
        UserRole role) {
}
