package com.testforge.entity.investigation;

import com.testforge.entity.common.BaseEntity;
import com.testforge.entity.investigation.enums.InvestigationStepStatus;
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

import java.time.LocalDateTime;

/**
 * 조회 루프 내 소스별 조회 단계 (INVESTIGATION_STEP, db/investigation.md). 정렬은 {@code ID} 오름차순
 * (조회 순서). 이 스텝이 찾은 출처(REFERENCES_JSON)는 REFERENCES 파트 payload의 원천이다
 * (사실 vs 렌더 스냅샷 분리).
 */
@Entity
@Table(
        name = "INVESTIGATION_STEP",
        indexes = {
                @Index(name = "IDX_INVESTIGATION_STEP_INV", columnList = "INVESTIGATION_ID, ID")
        }
)
public class InvestigationStep extends BaseEntity {

    /** 스텝 ID (PK). 조회 순서 기준 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 소속 조회 루프 */
    @Column(name = "INVESTIGATION_ID", nullable = false)
    private Long investigationId;

    /** 조회 소스 (api_spec / confluence) */
    @Column(name = "SOURCE", length = 30, nullable = false)
    private String source;

    /** 조회 질의 */
    @Column(name = "QUERY", length = 500)
    private String query;

    /** 스텝 상태: RUNNING / SUCCESS / FAILED / SKIPPED */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private InvestigationStepStatus status;

    /** 이 스텝이 찾은 출처(제목/링크 등). REFERENCES 파트 payload의 원천. 없으면 NULL */
    @Lob
    @Column(name = "REFERENCES_JSON", columnDefinition = "LONGTEXT")
    private String referencesJson;

    /** 시작 시각 */
    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    /** 종료 시각 */
    @Column(name = "FINISHED_AT")
    private LocalDateTime finishedAt;

    protected InvestigationStep() {
    }

    public InvestigationStep(Long investigationId, String source, String query,
                             InvestigationStepStatus status) {
        this.investigationId = investigationId;
        this.source = source;
        this.query = query;
        this.status = status;
        this.startedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getInvestigationId() {
        return investigationId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public InvestigationStepStatus getStatus() {
        return status;
    }

    public void setStatus(InvestigationStepStatus status) {
        this.status = status;
    }

    public String getReferencesJson() {
        return referencesJson;
    }

    public void setReferencesJson(String referencesJson) {
        this.referencesJson = referencesJson;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
