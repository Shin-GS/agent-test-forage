package com.testforge.controller.recipe;

import com.testforge.dto.recipe.RecipeCreateRequest;
import com.testforge.dto.recipe.RecipeDetailResponse;
import com.testforge.dto.recipe.RecipeSummaryResponse;
import com.testforge.dto.recipe.RecipeUpdateRequest;
import com.testforge.entity.recipe.enums.Visibility;
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
 * 레시피 정의/관리 API (실행 엔진 제외 — 정의/관리만).
 * 레시피 편집 화면과 목록/사이드 패널이 소비한다.
 *
 * <p>TODO: 권한 체크 (auth 도메인 구현 후: 관리자만 COMMON 생성, 본인 PRIVATE만 접근).
 * 현재는 인증/인가 도메인이 없어 CRUD/검증 로직만 노출한다.
 */
@RestController
@RequestMapping("/api/v1/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    /** 레시피 생성 (v1, 생성 시 검증 수행). 순환 참조면 400. */
    @PostMapping
    public ResponseEntity<RecipeDetailResponse> create(@RequestBody RecipeCreateRequest request) {
        // TODO: 권한 체크 (auth 도메인 구현 후: 관리자만 COMMON 생성, 본인 PRIVATE만 접근)
        RecipeDetailResponse created = recipeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** 레시피 상세 (없거나 삭제 시 404) */
    @GetMapping("/{id}")
    public RecipeDetailResponse detail(@PathVariable Long id) {
        return recipeService.detail(id);
    }

    /**
     * 레시피 목록. 필터(apiSpecId/visibility/ownerUserId/keyword)는 모두 선택.
     * keyword는 name/description LIKE, 삭제 제외, name 오름차순.
     */
    @GetMapping
    public List<RecipeSummaryResponse> list(
            @RequestParam(required = false) Long apiSpecId,
            @RequestParam(required = false) Visibility visibility,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String keyword) {
        return recipeService.list(apiSpecId, visibility, ownerUserId, keyword);
    }

    /** 레시피 수정 (버전 스냅샷 + CURRENT_VERSION 증가 + 재검증). 순환 참조면 400. */
    @PutMapping("/{id}")
    public RecipeDetailResponse update(@PathVariable Long id,
                                       @RequestBody RecipeUpdateRequest request) {
        // TODO: 권한 체크 (auth 도메인 구현 후: 관리자만 COMMON 생성, 본인 PRIVATE만 접근)
        return recipeService.update(id, request);
    }

    /** 레시피 소프트 삭제 (DELETED_AT = now) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // TODO: 권한 체크 (auth 도메인 구현 후: 관리자만 COMMON 생성, 본인 PRIVATE만 접근)
        recipeService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
