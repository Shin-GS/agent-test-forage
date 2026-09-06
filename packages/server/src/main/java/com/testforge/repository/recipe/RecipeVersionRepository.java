package com.testforge.repository.recipe;

import com.testforge.entity.recipe.RecipeVersion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecipeVersionRepository extends JpaRepository<RecipeVersion, Long> {

    /** 레시피의 버전 스냅샷 목록 (버전 내림차순, 최신 이력 우선) */
    List<RecipeVersion> findByRecipeIdOrderByVersionNoDesc(Long recipeId);

    /** 특정 레시피의 특정 버전 스냅샷 (없으면 empty → 404) */
    Optional<RecipeVersion> findByRecipeIdAndVersionNo(Long recipeId, int versionNo);

    /**
     * 버전 목록 커서 페이지 (versioning.md 버전 목록). 정렬 VERSION_NO DESC,
     * 커서 조건 {@code versionNo < :cursorVersionNo}. cursor가 null이면 첫 페이지(조건 무시).
     * hasNext 판정을 위해 서비스가 size+1건을 요청한다(Pageable로 limit 전달).
     *
     * @param recipeId        대상 레시피
     * @param cursorVersionNo 이전 페이지 마지막 versionNo (null이면 첫 페이지)
     * @param pageable        limit 용도 (정렬은 쿼리에 포함)
     */
    @Query("""
            SELECT v FROM RecipeVersion v
            WHERE v.recipeId = :recipeId
              AND (:cursorVersionNo IS NULL OR v.versionNo < :cursorVersionNo)
            ORDER BY v.versionNo DESC
            """)
    List<RecipeVersion> findByRecipeIdByCursor(@Param("recipeId") Long recipeId,
                                               @Param("cursorVersionNo") Integer cursorVersionNo,
                                               Pageable pageable);
}
