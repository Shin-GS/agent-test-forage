package com.testforge.entity.spec;

import com.testforge.entity.common.BaseEntity;
import com.testforge.entity.spec.enums.SpecStatus;
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

import java.time.LocalDateTime;

/**
 * 등록된 외부 서버 1개 = 1행. 식별 키는 BASE_URL (UNIQUE).
 * 서비스 메타/진단 정보/생명주기 상태를 보유하며, 원본 JSON과 엔드포인트는 별도 테이블로 분리된다.
 */
@Entity
@Table(
        name = "API_SPEC",
        uniqueConstraints = @UniqueConstraint(name = "UQ_API_SPEC_BASE_URL", columnNames = "BASE_URL"),
        indexes = @Index(name = "IDX_API_SPEC_STATUS", columnList = "STATUS")
)
public class ApiSpec extends BaseEntity {

    /** 스펙 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 표시용 서비스 이름 (중복 허용, 사용자/AI에 노출) */
    @Column(name = "NAME", length = 100)
    private String name;

    /** 서버 도메인. 식별 키 (UNIQUE) */
    @Column(name = "BASE_URL", length = 500, nullable = false)
    private String baseUrl;

    /** 생명주기 상태: ACTIVE / INACTIVE */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private SpecStatus status = SpecStatus.ACTIVE;

    /** 정규화 후 OpenAPI 스펙의 SHA-256 (변경 감지용, 64자 고정) */
    @Column(name = "SPEC_HASH", columnDefinition = "CHAR(64)")
    private String specHash;

    /** 서비스 설명 (관리자 수정 우선) */
    @Column(name = "SERVICE_DESCRIPTION", length = 500)
    private String serviceDescription;

    /** 서비스 도메인 영역 */
    @Column(name = "SERVICE_DOMAIN", length = 100)
    private String serviceDomain;

    /** 기능 키워드 배열 (JSON 배열 문자열로 저장) */
    @Column(name = "SERVICE_CAPABILITIES", columnDefinition = "TEXT")
    private String serviceCapabilities;

    /** 서비스 주의사항 */
    @Column(name = "SERVICE_NOTES", length = 500)
    private String serviceNotes;

    /** 정보 조회용 Jira 프로젝트 키 */
    @Column(name = "JIRA_PROJECT_KEY", length = 50)
    private String jiraProjectKey;

    /** 진단용: 등록한 라이브러리 언어 (예: java) */
    @Column(name = "CLIENT_LANG", length = 20)
    private String clientLang;

    /** 진단용: 등록한 라이브러리 버전 (예: 0.0.1) */
    @Column(name = "CLIENT_VERSION", length = 20)
    private String clientVersion;

    /** 진단용: 마지막 등록에 사용된 계약(body) 버전 */
    @Column(name = "SCHEMA_VERSION", length = 10)
    private String schemaVersion;

    /** 관리자가 메타를 수정했는지 여부 (yml 덮어쓰기 방지) */
    @Column(name = "IS_ADMIN_EDITED", nullable = false)
    private boolean adminEdited = false;

    /** yml에서 온 메타의 해시 (yml 변경 감지용, 64자 고정) */
    @Column(name = "YML_META_HASH", columnDefinition = "CHAR(64)")
    private String ymlMetaHash;

    /** 소프트 삭제 시각 (NULL이면 유효한 스펙) */
    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    protected ApiSpec() {
    }

    public ApiSpec(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public SpecStatus getStatus() {
        return status;
    }

    public void setStatus(SpecStatus status) {
        this.status = status;
    }

    public String getSpecHash() {
        return specHash;
    }

    public void setSpecHash(String specHash) {
        this.specHash = specHash;
    }

    public String getServiceDescription() {
        return serviceDescription;
    }

    public void setServiceDescription(String serviceDescription) {
        this.serviceDescription = serviceDescription;
    }

    public String getServiceDomain() {
        return serviceDomain;
    }

    public void setServiceDomain(String serviceDomain) {
        this.serviceDomain = serviceDomain;
    }

    public String getServiceCapabilities() {
        return serviceCapabilities;
    }

    public void setServiceCapabilities(String serviceCapabilities) {
        this.serviceCapabilities = serviceCapabilities;
    }

    public String getServiceNotes() {
        return serviceNotes;
    }

    public void setServiceNotes(String serviceNotes) {
        this.serviceNotes = serviceNotes;
    }

    public String getJiraProjectKey() {
        return jiraProjectKey;
    }

    public void setJiraProjectKey(String jiraProjectKey) {
        this.jiraProjectKey = jiraProjectKey;
    }

    public String getClientLang() {
        return clientLang;
    }

    public void setClientLang(String clientLang) {
        this.clientLang = clientLang;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public boolean isAdminEdited() {
        return adminEdited;
    }

    public void setAdminEdited(boolean adminEdited) {
        this.adminEdited = adminEdited;
    }

    public String getYmlMetaHash() {
        return ymlMetaHash;
    }

    public void setYmlMetaHash(String ymlMetaHash) {
        this.ymlMetaHash = ymlMetaHash;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
