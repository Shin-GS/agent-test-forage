package com.testforge.support;

import com.testforge.entity.user.enums.UserRole;
import com.testforge.entity.user.enums.UserStatus;
import com.testforge.security.AppUserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * 통합 테스트용 인증 지원 헬퍼.
 *
 * <p>인증 도입(세션 쿠키) 이후 보호된 {@code /api/v1/**} 엔드포인트는 세션 주체가 필요하고,
 * {@code SessionUserRecheckFilter}가 매 요청마다 {@code AppUserRepository.findById(principal.id)}로
 * 계정을 재확인한다. 따라서 테스트가 심는 인증 주체는 반드시 APP_USER에 <b>동일 id·ACTIVE</b>로
 * 존재해야 통과한다.
 *
 * <p>기존 통합 테스트는 {@code USER_ID = 1L} 같은 고정 id로 리소스를 만들고 소유권을 비교하므로,
 * 주체 id와 APP_USER row id가 반드시 일치해야 한다. {@code @GeneratedValue(IDENTITY)}는 명시 id를
 * 보장하지 못하므로, 여기서는 네이티브 INSERT로 원하는 id를 강제 저장한다(테스트 전용).
 *
 * <p>사용 (권장 패턴 A):
 * <pre>
 * mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
 * testAuth.ensureUser(USER_ID, UserRole.USER);           // ACTIVE 계정 보장
 * mockMvc.perform(get("/api/v1/...").with(testAuth.as(USER_ID)));
 * </pre>
 */
@TestComponent
public class TestAuthSupport {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 주어진 id의 ACTIVE 계정을 APP_USER에 보장한다(멱등). 이미 있으면 ROLE/STATUS를 갱신한다.
     * 명시 id를 강제하기 위해 네이티브 upsert를 사용한다(IDENTITY 컬럼이라도 H2/MySQL은 명시 id 허용).
     */
    @Transactional
    public void ensureUser(Long userId, UserRole role) {
        Number existing = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM APP_USER WHERE ID = :id")
                .setParameter("id", userId)
                .getSingleResult();

        if (existing.longValue() > 0) {
            entityManager.createNativeQuery(
                            "UPDATE APP_USER SET ROLE = :role, STATUS = :status WHERE ID = :id")
                    .setParameter("role", role.name())
                    .setParameter("status", UserStatus.ACTIVE.name())
                    .setParameter("id", userId)
                    .executeUpdate();
        } else {
            entityManager.createNativeQuery(
                            "INSERT INTO APP_USER (ID, USERNAME, PASSWORD, NAME, ROLE, STATUS, CREATED_AT, UPDATED_AT) "
                                    + "VALUES (:id, :username, :password, :name, :role, :status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")
                    .setParameter("id", userId)
                    .setParameter("username", "user" + userId)
                    .setParameter("password", "{noop}test")
                    .setParameter("name", "Test User " + userId)
                    .setParameter("role", role.name())
                    .setParameter("status", UserStatus.ACTIVE.name())
                    .executeUpdate();
        }
        entityManager.flush();
    }

    /** 주어진 id/역할의 세션 주체를 요청에 싣는 RequestPostProcessor (USER 역할 기본). */
    public RequestPostProcessor as(Long userId) {
        return as(userId, UserRole.USER);
    }

    /** 주어진 id/역할의 세션 주체를 요청에 싣는 RequestPostProcessor. */
    public RequestPostProcessor as(Long userId, UserRole role) {
        AppUserPrincipal principal =
                new AppUserPrincipal(userId, "user" + userId, "Test User " + userId, role);
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        return authentication(auth);
    }

    /**
     * 비-웹(서비스 직접 호출) 테스트에서 SecurityContext에 주체를 심는다. 사용 후 {@link #clear()} 호출 권장.
     */
    public void setContext(Long userId, UserRole role) {
        AppUserPrincipal principal =
                new AppUserPrincipal(userId, "user" + userId, "Test User " + userId, role);
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** SecurityContext 비움 */
    public void clear() {
        SecurityContextHolder.clearContext();
    }
}
