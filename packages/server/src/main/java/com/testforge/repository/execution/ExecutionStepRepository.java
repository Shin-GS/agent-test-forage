package com.testforge.repository.execution;

import com.testforge.entity.execution.ExecutionStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionStepRepository extends JpaRepository<ExecutionStep, Long> {

    /** 레시피 실행에 속한 스텝 목록 (스텝 순서대로) */
    List<ExecutionStep> findByExecutionRecipeIdOrderByStepIndexAsc(Long executionRecipeId);
}
