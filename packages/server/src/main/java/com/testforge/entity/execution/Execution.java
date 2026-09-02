package com.testforge.entity.execution;

import com.testforge.entity.common.BaseEntity;
import com.testforge.entity.execution.enums.ExecutionMode;
import com.testforge.entity.execution.enums.ExecutionStatus;
import com.testforge.entity.execution.enums.ExecutionType;
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
 * 1회 실행 = 1행 (EXECUTION, db/execution.md). 플랜(레시피 여러 개) 또는 단일(레시피 1개).
 * 계층: EXECUTION 1:N EXECUTION_RECIPE 1:N EXECUTION_STEP.
 *
 * <p>히스토리는 대화와 독립이다(history.md): 조회가 {@code userId} 기준이라 대화방을 소프트 삭제해도
 * 실행 기록은 그대로 유지된다. 대화방은 소프트 삭제라 row가 남으므로 {@code conversationId} 연결도
 * 끊지 않는다(프로젝트 원칙: 소프트 삭제 기준, FK 유지). 실제 스텝 실행은 FE 브라우저에서 수행되며,
 * 이 엔티티는 상태/스냅샷/context/결과의 서버측 기록을 담당한다.
 */
@Entity
@Table(
        name = "EXECUTION",
        indexes = {
                // 커서 페이징(WHERE user_id=? AND id<? ORDER BY id DESC)을 인덱스로 커버하기 위해 복합
                @Index(name = "IDX_EXECUTION_USER_ID", columnList = "USER_ID, ID"),
                @Index(name = "IDX_EXECUTION_CONVERSATION_ID", columnList = "CONVERSATION_ID, ID"),
                // 기간 필터/표시용 (추후 기간 검색 대비). 히스토리 정렬 자체는 ID 기준
                @Index(name = "IDX_EXECUTION_STARTED", columnList = "STARTED_AT")
        }
)
public class Execution extends BaseEntity {

    /** 실행 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 실행한 사용자 */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 실행된 대화방. 대화방 소프트 삭제 시에도 연결 유지(끊지 않음). NULL은 대화 없이 시작된 실행(추후) 대비 */
    @Column(name = "CONVERSATION_ID")
    private Long conversationId;

    /** 대상 서비스(스펙) 참조. 스펙 삭제 대비 NULL 허용 */
    @Column(name = "API_SPEC_ID")
    private Long apiSpecId;

    /** 실행 유형: SINGLE / PLAN */
    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", length = 20, nullable = false)
    private ExecutionType type;

    /** 표시명 (예: "회원가입", "플랜: 입사지원") */
    @Column(name = "TITLE", length = 200)
    private String title;

    /** 실행 모드: AUTO / MANUAL */
    @Enumerated(EnumType.STRING)
    @Column(name = "MODE", length = 20, nullable = false)
    private ExecutionMode mode;

    /** 실행 상태: RUNNING / SUCCESS / PARTIAL / FAILED / STOPPED */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private ExecutionStatus status = ExecutionStatus.RUNNING;

    /** 실행 전역 context (extract 변수 누적, JSON). 이어서 실행에 사용 */
    @Lob
    @Column(name = "CONTEXT_JSON", columnDefinition = "LONGTEXT")
    private String contextJson;

    /** 결과 요약 (히스토리 표시용) */
    @Column(name = "RESULT_SUMMARY", columnDefinition = "TEXT")
    private String resultSummary;

    /** 시작 시각 */
    @Column(name = "STARTED_AT", nullable = false)
    private LocalDateTime startedAt;

    /** 종료 시각 (RUNNING이면 NULL) */
    @Column(name = "FINISHED_AT")
    private LocalDateTime finishedAt;

    /** 소요 시간(ms). 종료 시 계산 */
    @Column(name = "DURATION_MS")
    private Long durationMs;

    protected Execution() {
    }

    public Execution(Long userId, ExecutionType type, ExecutionMode mode) {
        this.userId = userId;
        this.type = type;
        this.mode = mode;
        this.status = ExecutionStatus.RUNNING;
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

    public Long getApiSpecId() {
        return apiSpecId;
    }

    public void setApiSpecId(Long apiSpecId) {
        this.apiSpecId = apiSpecId;
    }

    public ExecutionType getType() {
        return type;
    }

    public void setType(ExecutionType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public void setMode(ExecutionMode mode) {
        this.mode = mode;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public String getContextJson() {
        return contextJson;
    }

    public void setContextJson(String contextJson) {
        this.contextJson = contextJson;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
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
