package com.testforge.repository.spec;

import com.testforge.entity.spec.ApiEndpoint;
import com.testforge.entity.spec.enums.EndpointStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {

    /** 스펙 ID로 해당 스펙의 모든 엔드포인트 조회 (upsert 시 기존 목록 확보용) */
    List<ApiEndpoint> findByApiSpecId(Long apiSpecId);

    /** (id, specId)로 엔드포인트 단건 조회 (수동 편집/삭제 시 스펙 소속 검증 겸용) */
    Optional<ApiEndpoint> findByIdAndApiSpecId(Long id, Long apiSpecId);

    /** (specId, method, path) 중복 존재 여부 (수동 생성/복제 시 유니크 충돌 사전 차단) */
    boolean existsByApiSpecIdAndHttpMethodAndPath(Long apiSpecId, String httpMethod, String path);

    /** 스펙 ID로 엔드포인트 조회 (상세 화면 표시용, method+path 정렬) */
    List<ApiEndpoint> findByApiSpecIdOrderByPathAscHttpMethodAsc(Long apiSpecId);

    /** 스펙의 특정 상태 엔드포인트 개수 (목록의 API 수 = ACTIVE 카운트) */
    long countByApiSpecIdAndStatus(Long apiSpecId, EndpointStatus status);

    /** ID 목록으로 엔드포인트 일괄 조회 (레시피 검증 시 참조 유효성 확인용) */
    List<ApiEndpoint> findByIdIn(List<Long> ids);
}
