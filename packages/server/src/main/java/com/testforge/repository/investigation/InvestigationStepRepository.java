package com.testforge.repository.investigation;

import com.testforge.entity.investigation.InvestigationStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestigationStepRepository extends JpaRepository<InvestigationStep, Long> {

    /** 한 조회 루프의 스텝 목록 (ID 오름차순 = 조회 순서) */
    List<InvestigationStep> findByInvestigationIdOrderByIdAsc(Long investigationId);
}
