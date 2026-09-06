package com.testforge.controller.spec;

import com.testforge.common.error.ApiException;
import com.testforge.dto.spec.SpecDetailResponse;
import com.testforge.dto.spec.SpecSummaryResponse;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.security.CurrentUser;
import com.testforge.service.spec.SpecQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 스펙 조회/관리 API. 관리자 페이지(admin.md 스펙 관리)가 소비한다.
 * 라이브러리 등록용 {@code SpecRegistrationController}와 분리되며,
 * 등록 전용 X-TestForge-Token은 이 컨트롤러에 적용하지 않는다.
 *
 * <p>권한 정책(auth.md 스펙 API 권한 — prefix 예외):
 * <ul>
 *   <li>조회(목록/상세)는 공용(로그인만) — SecurityConfig가 /api/v1/**를 인증 필수로 막는다.</li>
 *   <li>관리 액션(비활성/활성/삭제)은 ADMIN 역할만 — 비-admin이면 403.</li>
 * </ul>
 * 스펙은 소유 개념이 없어 역할(role)만으로 판정하며, role은 항상 세션(CurrentUser)에서 도출한다.
 */
@RestController
@RequestMapping("/api/v1/specs")
public class SpecQueryController {

    private final SpecQueryService queryService;

    public SpecQueryController(SpecQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 스펙 목록 (미삭제, name 오름차순).
     *
     * <p>공용 기본은 ACTIVE만 반환한다. {@code includeInactive=true}는 관리자 화면 전체 조회용이며
     * ADMIN만 유효하다. 비-admin이 true로 주더라도 조용히 무시하고 ACTIVE만 반환한다(403 아님).
     */
    @GetMapping
    public List<SpecSummaryResponse> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        boolean isAdmin = CurrentUser.role() == UserRole.ADMIN;
        // 비-admin의 includeInactive는 강제로 false 취급 (조용히 무시)
        return queryService.list(includeInactive && isAdmin, isAdmin);
    }

    /** 스펙 상세 (없거나 삭제된 경우 404). INACTIVE 스펙은 상세 조회 가능(관리자 화면 표시용). */
    @GetMapping("/{id}")
    public SpecDetailResponse detail(@PathVariable Long id) {
        return queryService.detail(id);
    }

    /** 스펙 수동 비활성화 (STATUS = INACTIVE). ADMIN만 가능. */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        requireAdmin();
        queryService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /** 스펙 활성화 (INACTIVE → ACTIVE 복귀). ADMIN만 가능. */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        requireAdmin();
        queryService.activate(id);
        return ResponseEntity.noContent().build();
    }

    /** 스펙 소프트 삭제 (DELETED_AT = now). ADMIN만 가능. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        requireAdmin();
        queryService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    /** 관리 액션 공통 ADMIN 게이트. 비-admin이면 403(forbidden). role은 세션에서 도출. */
    private void requireAdmin() {
        if (CurrentUser.role() != UserRole.ADMIN) {
            throw ApiException.forbidden("Admin role required for spec management");
        }
    }
}
