package com.testforge.dto.user;

import com.testforge.entity.user.enums.UserStatus;

/**
 * 상태 변경 요청 (ACTIVE ↔ INACTIVE). 자기보호/마지막 ACTIVE ADMIN 보호는 서비스에서 강제한다.
 */
public record UpdateStatusRequest(
        // 변경할 상태 (검증은 AdminUserService에서 수행)
        UserStatus status) {
}
