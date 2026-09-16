package com.testforge.controller.spec;

import com.testforge.common.error.ApiException;
import com.testforge.dto.spec.DuplicateEndpointRequest;
import com.testforge.dto.spec.EndpointDetailResponse;
import com.testforge.dto.spec.ManualEndpointRequest;
import com.testforge.dto.spec.ManualSpecRequest;
import com.testforge.dto.spec.SpecDetailResponse;
import com.testforge.dto.spec.SpecSummaryResponse;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.security.CurrentUser;
import com.testforge.service.spec.SpecCommandService;
import com.testforge.service.spec.SpecQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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
    private final SpecCommandService commandService;

    public SpecQueryController(SpecQueryService queryService,
                               SpecCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
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

    // ─────────────────────── 관리자 수동 등록/편집 (모두 ADMIN) ───────────────────────

    /**
     * 서버 수동 생성. ADMIN만 가능. 경로를 {@code /manual}로 분리한 이유:
     * {@code POST /api/v1/specs}는 SecurityConfig에서 라이브러리 토큰용 permitAll이므로
     * 세션 인증을 우회한다. 관리자 생성은 세션 인증 경로를 타야 하므로 별도 경로를 쓴다.
     * baseUrl이 기존 미삭제 스펙과 같으면 병합하고 그 스펙 id를 반환한다.
     */
    @PostMapping("/manual")
    public ResponseEntity<Map<String, Long>> createManual(@RequestBody ManualSpecRequest request) {
        requireAdmin();
        Long id = commandService.createSpec(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    /** 서버 메타 수정. ADMIN만 가능. baseUrl은 요청에 와도 무시(식별 키 변경 금지). */
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateManual(@PathVariable Long id,
                                             @RequestBody ManualSpecRequest request) {
        requireAdmin();
        commandService.updateSpec(id, request);
        return ResponseEntity.noContent().build();
    }

    /** 편집용 엔드포인트 단건 조회 (operationJson + source 포함). ADMIN만 가능. */
    @GetMapping("/{id}/endpoints/{endpointId}")
    public EndpointDetailResponse getEndpoint(@PathVariable Long id,
                                              @PathVariable Long endpointId) {
        requireAdmin();
        return commandService.getEndpoint(id, endpointId);
    }

    /** 엔드포인트 수동 생성 (source=MANUAL). ADMIN만 가능. */
    @PostMapping("/{id}/endpoints")
    public ResponseEntity<Map<String, Long>> createEndpoint(@PathVariable Long id,
                                                            @RequestBody ManualEndpointRequest request) {
        requireAdmin();
        Long endpointId = commandService.createEndpoint(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", endpointId));
    }

    /** 엔드포인트 수정. MANUAL은 전체, LIBRARY는 메타만. ADMIN만 가능. */
    @PatchMapping("/{id}/endpoints/{endpointId}")
    public ResponseEntity<Void> updateEndpoint(@PathVariable Long id,
                                               @PathVariable Long endpointId,
                                               @RequestBody ManualEndpointRequest request) {
        requireAdmin();
        commandService.updateEndpoint(id, endpointId, request);
        return ResponseEntity.noContent().build();
    }

    /** 엔드포인트 소프트 삭제 (DEPRECATED 전이, 참조 보호). ADMIN만 가능. */
    @DeleteMapping("/{id}/endpoints/{endpointId}")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable Long id,
                                               @PathVariable Long endpointId) {
        requireAdmin();
        commandService.deleteEndpoint(id, endpointId);
        return ResponseEntity.noContent().build();
    }

    /** 엔드포인트 복제 (사본은 항상 MANUAL). ADMIN만 가능. */
    @PostMapping("/{id}/endpoints/{endpointId}/duplicate")
    public ResponseEntity<Map<String, Long>> duplicateEndpoint(@PathVariable Long id,
                                                               @PathVariable Long endpointId,
                                                               @RequestBody(required = false)
                                                               DuplicateEndpointRequest request) {
        requireAdmin();
        Long newId = commandService.duplicateEndpoint(id, endpointId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", newId));
    }

    /** 관리 액션 공통 ADMIN 게이트. 비-admin이면 403(forbidden). role은 세션에서 도출. */
    private void requireAdmin() {
        if (CurrentUser.role() != UserRole.ADMIN) {
            throw ApiException.forbidden("Admin role required for spec management");
        }
    }
}
