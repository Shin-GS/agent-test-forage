package com.testforge.controller.recipe;

import com.testforge.dto.common.CursorPage;
import com.testforge.dto.recipe.RecipeCreateRequest;
import com.testforge.dto.recipe.RecipeDetailResponse;
import com.testforge.dto.recipe.RecipeSummaryResponse;
import com.testforge.dto.recipe.RecipeUpdateRequest;
import com.testforge.dto.recipe.RecipeVersionDetailResponse;
import com.testforge.dto.recipe.RecipeVersionSummary;
import com.testforge.security.CurrentUser;
import com.testforge.service.recipe.RecipeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 레시피 정의/관리 API (실행 엔진 제외 — 정의/관리 + 버전/복제).
 * 레시피 편집 화면과 목록/사이드 패널이 소비한다.
 *
 * <p>권한(auth.md 레시피 권한 매트릭스)은 서비스 레이어({@code RecipeAccessPolicy} + 게이팅)에서
 * 매 요청 강제한다: 남의 PRIVATE/삭제/없음 = 404(존재 은폐), 공통을 non-admin이 변경 시도 = 403.
 * {@code userId}/{@code role}은 {@link CurrentUser}(세션)에서만 도출하며 요청 바디를 신뢰하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    /** 레시피 생성 (v1, 생성 시 검증 수행). 순환 참조면 400, 공통 생성은 ADMIN만(아니면 403). */
    @PostMapping
    public ResponseEntity<RecipeDetailResponse> create(@RequestBody RecipeCreateRequest request) {
        // ownerUserId(작성자)는 세션에서 도출 (클라이언트 값 무시).
        RecipeCreateRequest secured = new RecipeCreateRequest(
                CurrentUser.id(), request.apiSpecId(), request.name(), request.description(),
                request.visibility(), request.tags(), request.variables(), request.steps(),
                request.resultDefinition(), request.resultTemplate());
        RecipeDetailResponse created = recipeService.create(secured);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** 레시피 상세. 없음/삭제/남의 PRIVATE면 404. */
    @GetMapping("/{id}")
    public RecipeDetailResponse detail(@PathVariable Long id) {
        return recipeService.detail(id);
    }

    /**
     * 레시피 목록. "COMMON 전체 + 요청자 본인 PRIVATE"만 반환(소유 격리).
     * 다중 선택 필터(apiSpecId/visibility/tag)는 각 축 복수 선택 가능(빈 선택=전체).
     * keyword는 name/description LIKE. 정렬은 sort(recent/usage/name/updated) + direction(asc/desc), 기본 recent.
     */
    @GetMapping
    public List<RecipeSummaryResponse> list(
            @RequestParam(required = false) List<Long> apiSpecId,
            @RequestParam(required = false) List<String> visibility,
            @RequestParam(required = false) List<String> tag,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return recipeService.list(apiSpecId, visibility, tag, keyword, sort, direction);
    }

    /**
     * 레시피 수정 (버전 스냅샷 + CURRENT_VERSION 증가 + 재검증). 순환 참조면 400.
     * 없음/삭제/남의 PRIVATE=404, 공통을 non-admin이 수정 시도=403.
     */
    @PutMapping("/{id}")
    public RecipeDetailResponse update(@PathVariable Long id,
                                       @RequestBody RecipeUpdateRequest request) {
        return recipeService.update(id, request);
    }

    /** 레시피 소프트 삭제 (DELETED_AT = now). 없음/삭제/남의 PRIVATE=404, 공통 non-admin=403. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recipeService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 레시피 복제 → 개인 사본(PRIVATE, 요청자 소유). 공통 복제도 개인 사본이 된다.
     * 원본 없음/삭제/남의 PRIVATE=404.
     */
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<RecipeDetailResponse> duplicate(@PathVariable Long id) {
        RecipeDetailResponse copy = recipeService.duplicate(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(copy);
    }

    // ── 버전 API (versioning.md) ──

    /** 버전 목록 (커서 페이징, VERSION_NO DESC). 권한=조회 규칙. 없음/삭제/남의 PRIVATE=404. */
    @GetMapping("/{id}/versions")
    public CursorPage<RecipeVersionSummary> versions(
            @PathVariable Long id,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return recipeService.versions(id, cursor, size);
    }

    /** 특정 버전 내용 (RecipeDetailResponse 호환 + versionNo/createdAt). 권한=조회 규칙. 없으면 404. */
    @GetMapping("/{id}/versions/{versionNo}")
    public RecipeVersionDetailResponse versionDetail(@PathVariable Long id,
                                                     @PathVariable int versionNo) {
        return recipeService.versionDetail(id, versionNo);
    }

    /**
     * 해당 버전으로 복원 (새 버전 커밋). INVALID여도 복원 성공. 응답=복원 후 상세.
     * 권한=변경 규칙: 없음/삭제/남의 PRIVATE=404, 공통을 non-admin이 복원 시도=403.
     */
    @PostMapping("/{id}/versions/{versionNo}/restore")
    public RecipeDetailResponse restore(@PathVariable Long id,
                                        @PathVariable int versionNo) {
        return recipeService.restore(id, versionNo);
    }
}
