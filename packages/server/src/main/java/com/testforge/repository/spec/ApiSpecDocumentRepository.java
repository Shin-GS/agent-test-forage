package com.testforge.repository.spec;

import com.testforge.entity.spec.ApiSpecDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiSpecDocumentRepository extends JpaRepository<ApiSpecDocument, Long> {

    /** 스펙 ID로 원본 문서 조회 (1:1) */
    Optional<ApiSpecDocument> findByApiSpecId(Long apiSpecId);
}
