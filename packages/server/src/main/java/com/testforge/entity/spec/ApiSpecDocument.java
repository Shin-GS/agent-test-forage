package com.testforge.entity.spec;

import com.testforge.entity.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 스펙의 원본 OpenAPI JSON 보관. API_SPEC과 1:1.
 * 대용량이라 별도 테이블로 분리하여 스펙 목록 조회 시 JSON을 로딩하지 않도록 한다.
 */
@Entity
@Table(
        name = "API_SPEC_DOCUMENT",
        uniqueConstraints = @UniqueConstraint(name = "UQ_API_SPEC_DOCUMENT_SPEC", columnNames = "API_SPEC_ID")
)
public class ApiSpecDocument extends BaseEntity {

    /** 문서 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 소속 스펙 ID (FK, 1:1) */
    @Column(name = "API_SPEC_ID", nullable = false)
    private Long apiSpecId;

    /** 원본 OpenAPI JSON 전문 */
    @Lob
    @Column(name = "SPEC_JSON", columnDefinition = "LONGTEXT")
    private String specJson;

    protected ApiSpecDocument() {
    }

    public ApiSpecDocument(Long apiSpecId, String specJson) {
        this.apiSpecId = apiSpecId;
        this.specJson = specJson;
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

    public String getSpecJson() {
        return specJson;
    }

    public void setSpecJson(String specJson) {
        this.specJson = specJson;
    }
}
