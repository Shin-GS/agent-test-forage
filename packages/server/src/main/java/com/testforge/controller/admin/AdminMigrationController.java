package com.testforge.controller.admin;

import com.testforge.dto.recipe.StepApiSpecIdMigrationResponse;
import com.testforge.service.recipe.MigrationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 일회성 마이그레이션 API. 정본: docs/db/recipe.md "apiSpecId 역산 마이그레이션(일회성)".
 *
 * <p>권한: {@code /api/v1/admin/**} prefix라 SecurityConfig가 ADMIN 역할을 강제한다(미달 시 403).
 * AdminUserController 선례처럼 컨트롤러/서비스에 별도 역할 게이트를 두지 않는다.
 *
 * <p>이 컨트롤러의 엔드포인트는 배포 전 데이터 백필용 <b>임시(one-off)</b>다. 완료 후 제거를 권장한다.
 */
@RestController
@RequestMapping("/api/v1/admin/migrations")
public class AdminMigrationController {

    private final MigrationService migrationService;

    public AdminMigrationController(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    /**
     * API 스텝의 {@code apiSpecId} 역산 백필. RECIPE.STEPS_JSON + RECIPE_VERSION.SNAPSHOT_JSON 내부
     * stepsJson을 보정한다(실행 히스토리 제외). idempotent — 재실행해도 안전.
     */
    @PostMapping("/step-api-spec-id")
    public StepApiSpecIdMigrationResponse migrateStepApiSpecId() {
        return migrationService.migrateStepApiSpecId();
    }
}
