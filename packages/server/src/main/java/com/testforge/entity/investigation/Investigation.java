package com.testforge.entity.investigation;

import com.testforge.entity.common.BaseEntity;
import com.testforge.entity.investigation.enums.InvestigationStatus;
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
 * 1회 정보 조회(investigate) 루프 = 1행 (INVESTIGATION, db/investigation.md). EXECUTION의 형제 계층이며
 * 구조도 대칭이다(INVESTIGATION 1:N INVESTIGATION_STEP). "어떤 질문에 어떤 소스를 조회해 어떤 답을 냈나"를
 * 분석·감사할 수 있게 정규 저장한다.
 *
 * <p>히스토리 독립: 대화방을 소프트 삭제해도 조회 기록은 유지된다({@code userId} 기준 조회). 대화방 row가
 * 남으므로 {@code conversationId} 연결은 끊지 않는다(프로젝트 원칙: 소프트 삭제 기준, FK 유지).
 * 정렬·커서는 {@code ID}(auto-increment) 단독.
 */
@Entity
@Table(
        name = "INVESTIGATION",
        indexes = {
                @Index(name = "IDX_INVESTIGATION_USER_ID", columnList = "USER_ID, ID"),
                @Index(name = "IDX_INVESTIGATION_CONVERSATION_ID", columnList = "CONVERSATION_ID, ID"),
                @Index(name = "IDX_INVESTIGATION_TRIGGER_PART_ID", columnList = "TRIGGER_PART_ID")
        }
)
public class Investigation extends BaseEntity {

    /** 조회 ID (PK). 정렬·커서 기준 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 조회한 사용자 (히스토리/분석 조회 기준) */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 조회가 일어난 대화방. 소프트 삭제라 연결 유지. NULL은 대화 없이 시작된 조회(추후) 대비 */
    @Column(name = "CONVERSATION_ID")
    private Long conversationId;

    /**
     * 조회를 촉발한 {@code MESSAGE_PART} ID. 한 턴에 조회가 여러 번이어도 각 조회가 자기 촉발 파트를 특정한다.
     * NULL은 촉발 파트 미상/대화 없이 시작된 조회(추후) 대비.
     */
    @Column(name = "TRIGGER_PART_ID")
    private Long triggerPartId;

    /** 조회 대상 서비스 (참조용, 스펙 삭제 대비 NULL 허용) */
    @Column(name = "API_SPEC_ID")
    private Long apiSpecId;

    /** 사용자 질문(무엇을 조회했나) — 분석 핵심 */
    @Column(name = "QUERY_TEXT", length = 500)
    private String queryText;

    /** 조회 루프 상태: RUNNING / DONE / FAILED / TIMEOUT */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private InvestigationStatus status = InvestigationStatus.RUNNING;

    /** 최종 답변 요약(분석용). 종료(DONE) 시 채움 */
    @Lob
    @Column(name = "ANSWER_SUMMARY", columnDefinition = "TEXT")
    private String answerSummary;

    /** 시작 시각 */
    @Column(name = "STARTED_AT", nullable = false)
    private LocalDateTime startedAt;

    /** 종료 시각 (RUNNING이면 NULL) */
    @Column(name = "FINISHED_AT")
    private LocalDateTime finishedAt;

    /** 소요 시간(ms). 종료 시 계산 */
    @Column(name = "DURATION_MS")
    private Long durationMs;

    protected Investigation() {
    }

    public Investigation(Long userId, Long conversationId, Long triggerPartId,
                         Long apiSpecId, String queryText) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.triggerPartId = triggerPartId;
        this.apiSpecId = apiSpecId;
        this.queryText = queryText;
        this.status = InvestigationStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getTriggerPartId() {
        return triggerPartId;
    }

    public void setTriggerPartId(Long triggerPartId) {
        this.triggerPartId = triggerPartId;
    }

    public Long getApiSpecId() {
        return apiSpecId;
    }

    public void setApiSpecId(Long apiSpecId) {
        this.apiSpecId = apiSpecId;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public InvestigationStatus getStatus() {
        return status;
    }

    public void setStatus(InvestigationStatus status) {
        this.status = status;
    }

    public String getAnswerSummary() {
        return answerSummary;
    }

    public void setAnswerSummary(String answerSummary) {
        this.answerSummary = answerSummary;
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

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
}
