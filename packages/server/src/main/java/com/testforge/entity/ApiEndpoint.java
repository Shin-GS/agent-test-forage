package com.testforge.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 스펙에 속한 개별 API. 레시피 스텝이 이 행의 PK로 참조하므로,
 * 재등록 upsert 시 반드시 PK를 유지해야 한다.
 */
@Entity
@Table(
        name = "API_ENDPOINT",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_API_ENDPOINT_SPEC_METHOD_PATH",
                columnNames = {"API_SPEC_ID", "HTTP_METHOD", "PATH"}
        ),
        indexes = @Index(name = "IDX_API_ENDPOINT_SPEC", columnList = "API_SPEC_ID")
)
public class ApiEndpoint extends BaseEntity {

    /** 엔드포인트 ID (PK, 레시피 참조 대상) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 소속 스펙 ID (FK) */
    @Column(name = "API_SPEC_ID", nullable = false)
    private Long apiSpecId;

    /** HTTP 메서드 (GET/POST/PUT/PATCH/DELETE) */
    @Column(name = "HTTP_METHOD", length = 10, nullable = false)
    private String httpMethod;

    /** 경로 (예: /api/v1/users) */
    @Column(name = "PATH", length = 500, nullable = false)
    private String path;

    /** 해당 API의 요청/응답 스키마 (OpenAPI operation) JSON */
    @Lob
    @Column(name = "OPERATION_JSON", columnDefinition = "TEXT")
    private String operationJson;

    /** API 설명 (매칭 힌트) */
    @Column(name = "SUMMARY", length = 500)
    private String summary;

    /** @TestForgeExclude — 목록에서 제외 여부 */
    @Column(name = "IS_EXCLUDED", nullable = false)
    private boolean excluded = false;

    /** @TestForgeConfirm — 실행 전 확인 필요 여부 */
    @Column(name = "IS_CONFIRM_REQUIRED", nullable = false)
    private boolean confirmRequired = false;

    /** 실행 전 확인 메시지 */
    @Column(name = "CONFIRM_MESSAGE", length = 500)
    private String confirmMessage;

    /** 생명주기 상태: ACTIVE / DEPRECATED(스펙에서 사라짐) */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private EndpointStatus status = EndpointStatus.ACTIVE;

    protected ApiEndpoint() {
    }

    public ApiEndpoint(Long apiSpecId, String httpMethod, String path) {
        this.apiSpecId = apiSpecId;
        this.httpMethod = httpMethod;
        this.path = path;
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

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getOperationJson() {
        return operationJson;
    }

    public void setOperationJson(String operationJson) {
        this.operationJson = operationJson;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public boolean isExcluded() {
        return excluded;
    }

    public void setExcluded(boolean excluded) {
        this.excluded = excluded;
    }

    public boolean isConfirmRequired() {
        return confirmRequired;
    }

    public void setConfirmRequired(boolean confirmRequired) {
        this.confirmRequired = confirmRequired;
    }

    public String getConfirmMessage() {
        return confirmMessage;
    }

    public void setConfirmMessage(String confirmMessage) {
        this.confirmMessage = confirmMessage;
    }

    public EndpointStatus getStatus() {
        return status;
    }

    public void setStatus(EndpointStatus status) {
        this.status = status;
    }
}
