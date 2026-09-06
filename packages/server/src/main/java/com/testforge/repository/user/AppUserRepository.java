package com.testforge.repository.user;

import com.testforge.entity.user.AppUser;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.entity.user.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
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

    /** 아이디 중복 확인 (생성 시 UQ_APP_USER_USERNAME 위반 사전 차단) */
    boolean existsByUsername(String username);

    /**
     * 역할+상태 조합 계정 수. 마지막 ACTIVE ADMIN 보호 판정에 사용한다
     * (ROLE=ADMIN AND STATUS=ACTIVE 카운트, INACTIVE ADMIN은 제외).
     */
    long countByRoleAndStatus(UserRole role, UserStatus status);

    /** 사용자 관리 목록용 전체 조회 (username 오름차순). 페이징 없음(소수). q 필터는 서비스에서 수행. */
    List<AppUser> findAllByOrderByUsernameAsc();
}
