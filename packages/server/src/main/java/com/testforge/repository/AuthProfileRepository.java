package com.testforge.repository;

import com.testforge.entity.AuthProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AuthProfileRepository extends JpaRepository<AuthProfile, Long> {

    /** 스펙 ID로 인증 프로필 목록 조회 */
    List<AuthProfile> findByApiSpecId(Long apiSpecId);

    /** 스펙 ID 기준 인증 프로필 전체 삭제 (재등록 시 전체 교체용) */
    @Transactional
    void deleteByApiSpecId(Long apiSpecId);
}
