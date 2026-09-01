package com.testforge.controller.spec;

import com.testforge.dto.spec.SpecDetailResponse;
import com.testforge.dto.spec.SpecSummaryResponse;
import com.testforge.service.spec.SpecQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 스펙 조회/관리 API. 관리자 페이지(admin.md 스펙 관리)가 소비한다.
 * 라이브러리 등록용 {@code SpecRegistrationController}와 분리되며,
 * 등록 전용 X-TestForge-Token은 이 컨트롤러에 적용하지 않는다.
 *
 * <p>TODO: 관리자 권한 체크 (auth 도메인 구현 후 적용).
 * 현재는 인증/인가 도메인이 없어 조회/관리 로직만 노출한다.
 */
@RestController
@RequestMapping("/api/v1/specs")
public class SpecQueryController {

    private final SpecQueryService queryService;

    public SpecQueryController(SpecQueryService queryService) {
        this.queryService = queryService;
    }

    /** 스펙 목록 (미삭제, name 오름차순) */
    @GetMapping
    public List<SpecSummaryResponse> list() {
        return queryService.list();
    }

    /** 스펙 상세 (없거나 삭제된 경우 404) */
    @GetMapping("/{id}")
    public SpecDetailResponse detail(@PathVariable Long id) {
        return queryService.detail(id);
    }

    /** 스펙 수동 비활성화 (STATUS = INACTIVE) */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        // TODO: 관리자 권한 체크 (auth 도메인 구현 후 적용)
        queryService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /** 스펙 활성화 (INACTIVE → ACTIVE 복귀) */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        // TODO: 관리자 권한 체크 (auth 도메인 구현 후 적용)
        queryService.activate(id);
        return ResponseEntity.noContent().build();
    }

    /** 스펙 소프트 삭제 (DELETED_AT = now) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // TODO: 관리자 권한 체크 (auth 도메인 구현 후 적용)
        queryService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
