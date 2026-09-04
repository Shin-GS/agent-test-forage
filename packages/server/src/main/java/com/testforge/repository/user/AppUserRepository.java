package com.testforge.repository.user;

import com.testforge.entity.user.AppUser;
import com.testforge.entity.user.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * APP_USER 조회. 로그인 시 username으로 조회하고, 인증 필터가 매 요청마다 id로 재조회해
 * 현재 STATUS/ROLE을 반영한다(auth.md 세션 상태/역할 재확인).
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** 로그인 아이디로 조회 (로그인/재확인) */
    Optional<AppUser> findByUsername(String username);

    /** 특정 역할 계정 존재 여부 (관리자 seed 스킵 판단용) */
    boolean existsByRole(UserRole role);
}
