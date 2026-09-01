package com.testforge.repository.execution;

import com.testforge.entity.execution.ExecutionRecipe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionRecipeRepository extends JpaRepository<ExecutionRecipe, Long> {

    /** 실행에 속한 레시피 실행 목록 (플랜 순서대로) */
    List<ExecutionRecipe> findByExecutionIdOrderBySequenceAsc(Long executionId);
}
