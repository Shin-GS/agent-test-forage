package com.testforge.dto.user;

import com.testforge.entity.user.enums.UserRole;

/**
 * 역할 변경 요청 (USER ↔ ADMIN). 자기보호/마지막 ACTIVE ADMIN 보호는 서비스에서 강제한다.
 */
public record UpdateRoleRequest(
        // 변경할 역할 (검증은 AdminUserService에서 수행)
        UserRole role) {
}
