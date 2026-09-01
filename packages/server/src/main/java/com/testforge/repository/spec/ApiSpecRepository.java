package com.testforge.repository.spec;

import com.testforge.entity.spec.ApiSpec;
import com.testforge.entity.spec.enums.SpecStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ApiSpecRepository extends JpaRepository<ApiSpec, Long> {

    /** baseUrl로 미삭제 스펙 조회 (식별 키 기준) */
    Optional<ApiSpec> findByBaseUrlAndDeletedAtIsNull(String baseUrl);

    /** ID로 미삭제 스펙 조회 (상세/상태변경 시 삭제 스펙 배제용) */
    Optional<ApiSpec> findByIdAndDeletedAtIsNull(Long id);

    /** 미삭제 스펙 전체를 name 오름차순으로 조회 (관리자 목록용) */
    List<ApiSpec> findByDeletedAtIsNullOrderByNameAsc();

    /** 지정 상태이면서 임계 시각 이전에 heartbeat가 멈춘 미삭제 스펙 조회 (STALE 전이 대상) */
    List<ApiSpec> findByStatusAndLastHeartbeatAtBeforeAndDeletedAtIsNull(
            SpecStatus status, LocalDateTime threshold);

    /** 지정 상태가 아니면서 임계 시각 이전에 heartbeat가 멈춘 미삭제 스펙 조회 (소프트 삭제 대상, INACTIVE 제외용) */
    List<ApiSpec> findByStatusNotAndLastHeartbeatAtBeforeAndDeletedAtIsNull(
            SpecStatus status, LocalDateTime threshold);
}
