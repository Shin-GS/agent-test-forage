package com.testforge.repository.recipe;

import com.testforge.entity.recipe.RecipeVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeVersionRepository extends JpaRepository<RecipeVersion, Long> {

    /** 레시피의 버전 스냅샷 목록 (버전 내림차순, 최신 이력 우선) */
    List<RecipeVersion> findByRecipeIdOrderByVersionNoDesc(Long recipeId);
}
