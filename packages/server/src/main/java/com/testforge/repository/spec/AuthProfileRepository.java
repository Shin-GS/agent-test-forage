package com.testforge.repository.spec;

import com.testforge.entity.spec.AuthProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuthProfileRepository extends JpaRepository<AuthProfile, Long> {

    /** 스펙 ID로 인증 프로필 목록 조회 (INACTIVE 포함 전체 — upsert/부활 판단용) */
    List<AuthProfile> findByApiSpecId(Long apiSpecId);
}
