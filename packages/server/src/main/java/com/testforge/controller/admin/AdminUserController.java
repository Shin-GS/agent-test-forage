package com.testforge.controller.admin;

import com.testforge.dto.user.AdminUserSummaryResponse;
import com.testforge.dto.user.CreateUserRequest;
import com.testforge.dto.user.UpdatePasswordRequest;
import com.testforge.dto.user.UpdateRoleRequest;
import com.testforge.dto.user.UpdateStatusRequest;
import com.testforge.service.user.AdminUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 사용자 관리 API (admin.md 사용자 관리 B). 목록/생성/역할·상태·비밀번호 변경 5종.
 *
 * <p>권한: {@code /api/v1/admin/**} prefix라 SecurityConfig가 ADMIN 역할을 강제한다(미달 시 403).
 * 따라서 컨트롤러/서비스에 별도 역할 게이트를 두지 않고, 도메인 검증/보호 규칙은 서비스가 담당한다.
 * userId/role은 세션에서 도출한다(위조 금지 — 서비스가 CurrentUser로 본인 판정).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** 사용자 목록. {@code q}는 username 부분일치(대소문자 무시). 페이징 없음. 비밀번호 해시 미포함. */
    @GetMapping
    public List<AdminUserSummaryResponse> list(@RequestParam(required = false) String q) {
        return adminUserService.list(q);
    }

    /** 사용자 생성. 검증 위반 400, 아이디 중복 400. 성공 시 생성된 요약 반환. */
    @PostMapping
    public AdminUserSummaryResponse create(@RequestBody CreateUserRequest request) {
        return adminUserService.create(request);
    }

    /** 역할 변경 (USER ↔ ADMIN). 대상 없음 404, 자기보호/마지막 ADMIN 위반 400. */
    @PatchMapping("/{id}/role")
    public AdminUserSummaryResponse updateRole(@PathVariable Long id,
                                               @RequestBody UpdateRoleRequest request) {
        return adminUserService.updateRole(id, request.role());
    }

    /** 상태 변경 (ACTIVE ↔ INACTIVE). 대상 없음 404, 자기보호/마지막 ADMIN 위반 400. */
    @PatchMapping("/{id}/status")
    public AdminUserSummaryResponse updateStatus(@PathVariable Long id,
                                                 @RequestBody UpdateStatusRequest request) {
        return adminUserService.updateStatus(id, request.status());
    }

    /** 비밀번호 지정 (관리자가 새 값 직접 지정). 대상 없음 404, 8자 미만 400. 본인도 허용. */
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> updatePassword(@PathVariable Long id,
                                               @RequestBody UpdatePasswordRequest request) {
        adminUserService.updatePassword(id, request.password());
        return ResponseEntity.noContent().build();
    }
}
