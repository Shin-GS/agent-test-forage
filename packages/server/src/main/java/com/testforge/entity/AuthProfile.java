package com.testforge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 스펙의 인증 프로필 (이름 + 401/403 시 안내할 로그인 URL).
 * 레시피가 PK를 참조하지 않으므로 재등록 시 스펙 기준 전체 교체된다.
 */
@Entity
@Table(
        name = "AUTH_PROFILE",
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
}
