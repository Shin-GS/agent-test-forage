package com.testforge.entity.user;

import com.testforge.entity.common.BaseEntity;
import com.testforge.entity.user.enums.UserRole;
import com.testforge.entity.user.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 사용자 계정 = 1행 (APP_USER). 로그인 아이디/비밀번호(bcrypt)/표시 이름/역할/상태를 보관한다
 * (db/user.md). MySQL 예약어 회피를 위해 테이블명은 {@code USER}가 아닌 {@code APP_USER}.
 * AI/실행 설정은 DB가 아니라 서버 설정 파일로만 관리하므로 여기에 두지 않는다.
 */
@Entity
@Table(
        name = "APP_USER",
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_APP_USER_USERNAME", columnNames = "USERNAME")
        }
)
public class AppUser extends BaseEntity {

    /** 사용자 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 로그인 아이디 (UNIQUE) */
    @Column(name = "USERNAME", length = 50, nullable = false)
    private String username;

    /** 비밀번호 bcrypt 해시. 평문 저장 금지. */
    @Column(name = "PASSWORD", length = 255, nullable = false)
    private String password;

    /** 표시 이름 */
    @Column(name = "NAME", length = 100)
    private String name;

    /** 역할 (USER / ADMIN) */
    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", length = 20, nullable = false)
    private UserRole role = UserRole.USER;

    /** 계정 상태 (ACTIVE / INACTIVE). 관리자가 비활성화 가능 */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    /** 마지막 로그인 시각 (로그인 성공 시 갱신) */
    @Column(name = "LAST_LOGIN_AT")
    private LocalDateTime lastLoginAt;

    /** JPA용 기본 생성자 */
    protected AppUser() {
    }

    /** 신규 계정 생성 (username/bcrypt password/name/role 필수, status는 ACTIVE 기본) */
    public AppUser(String username, String password, String name, UserRole role) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
