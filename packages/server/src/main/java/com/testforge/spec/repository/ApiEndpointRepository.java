package com.testforge.spec.repository;

import com.testforge.spec.entity.ApiEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {

    /** 스펙 ID로 해당 스펙의 모든 엔드포인트 조회 (upsert 시 기존 목록 확보용) */
    List<ApiEndpoint> findByApiSpecId(Long apiSpecId);
}
