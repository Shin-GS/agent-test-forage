package com.testforge.entity.spec;

import com.testforge.entity.common.BaseEntity;
import com.testforge.entity.spec.enums.AuthProfileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 스펙의 인증 프로필 (이름 + 401/403 시 안내할 로그인 URL).
 * 재등록 시 (specId, name) 키로 upsert하며, 사라진 프로필은 삭제하지 않고
 * INACTIVE로 표시한다(다시 나타나면 ACTIVE로 복귀).
 */
@Entity
@Table(
        name = "AUTH_PROFILE",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_AUTH_PROFILE_SPEC_NAME",
                columnNames = {"API_SPEC_ID", "NAME"}
        ),
        indexes = @Index(name = "IDX_AUTH_PROFILE_SPEC", columnList = "API_SPEC_ID")
)
public class AuthProfile extends BaseEntity {

    /** 프로필 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 소속 스펙 ID (FK) */
    @Column(name = "API_SPEC_ID", nullable = false)
    private Long apiSpecId;

    /** 프로필 이름 (예: 일반/관리자) */
    @Column(name = "NAME", length = 100)
    private String name;

    /** 401/403 시 안내할 로그인 페이지 URL */
    @Column(name = "LOGIN_PAGE_URL", length = 500)
    private String loginPageUrl;

    /** 생명주기 상태: ACTIVE / INACTIVE(스펙에서 사라짐) */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private AuthProfileStatus status = AuthProfileStatus.ACTIVE;

    protected AuthProfile() {
    }

    public AuthProfile(Long apiSpecId, String name, String loginPageUrl) {
        this.apiSpecId = apiSpecId;
        this.name = name;
        this.loginPageUrl = loginPageUrl;
    }

    public Long getId() {
        return id;
    }

    public Long getApiSpecId() {
        return apiSpecId;
    }

    public void setApiSpecId(Long apiSpecId) {
        this.apiSpecId = apiSpecId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLoginPageUrl() {
        return loginPageUrl;
    }

    public void setLoginPageUrl(String loginPageUrl) {
        this.loginPageUrl = loginPageUrl;
    }

    public AuthProfileStatus getStatus() {
        return status;
    }

    public void setStatus(AuthProfileStatus status) {
        this.status = status;
    }
}
