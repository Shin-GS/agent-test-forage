package com.testforge.repository.recipe;

import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.recipe.enums.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    /** ID로 미삭제 레시피 조회 (상세/수정/삭제 시 삭제 레시피 배제용) */
    Optional<Recipe> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 필터 조합 목록 조회 (미삭제만). 각 파라미터는 null이면 해당 조건을 무시한다.
     * 검색어(keyword)는 name/description에 대소문자 무시 LIKE로 매칭한다.
     * 정렬은 name 오름차순.
     */
    @Query("""
            SELECT r FROM Recipe r
            WHERE r.deletedAt IS NULL
              AND (:apiSpecId IS NULL OR r.apiSpecId = :apiSpecId)
              AND (:visibility IS NULL OR r.visibility = :visibility)
              AND (:ownerUserId IS NULL OR r.ownerUserId = :ownerUserId)
              AND (:keyword IS NULL
                   OR LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY r.name ASC
            """)
    List<Recipe> search(@Param("apiSpecId") Long apiSpecId,
                        @Param("visibility") Visibility visibility,
                        @Param("ownerUserId") Long ownerUserId,
                        @Param("keyword") String keyword);
}
