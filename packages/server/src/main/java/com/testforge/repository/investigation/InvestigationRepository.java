package com.testforge.repository.investigation;

import com.testforge.entity.investigation.Investigation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestigationRepository extends JpaRepository<Investigation, Long> {
}
