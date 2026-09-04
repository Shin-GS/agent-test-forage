package com.testforge.repository.spec;

import com.testforge.entity.spec.ApiSpec;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApiSpecRepository extends JpaRepository<ApiSpec, Long> {

    /** baseUrl로 미삭제 스펙 조회 (식별 키 기준) */
    Optional<ApiSpec> findByBaseUrlAndDeletedAtIsNull(String baseUrl);

    /** ID로 미삭제 스펙 조회 (상세/상태변경 시 삭제 스펙 배제용) */
    Optional<ApiSpec> findByIdAndDeletedAtIsNull(Long id);

    /** 미삭제 스펙 전체를 name 오름차순으로 조회 (관리자 목록용) */
    List<ApiSpec> findByDeletedAtIsNullOrderByNameAsc();

    /** ID 집합으로 스펙 일괄 조회 (히스토리 목록의 serviceName 매핑 N+1 방지용) */
    List<ApiSpec> findByIdIn(Collection<Long> ids);
}
