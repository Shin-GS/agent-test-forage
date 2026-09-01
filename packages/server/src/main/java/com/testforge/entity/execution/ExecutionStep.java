package com.testforge.entity.execution;

import com.testforge.entity.common.BaseEntity;
import com.testforge.entity.execution.enums.ExecutionStepStatus;
import com.testforge.entity.execution.enums.StepType;
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
 * 레시피 내 스텝별 실행 결과 = 1행 (EXECUTION_STEP, db/execution.md).
 * 이어서 실행/히스토리 상세의 핵심. 실행 시작 시 스텝 스냅샷으로 PENDING 레코드가 생성되고,
 * FE가 각 스텝을 실행한 뒤 결과(상태/요약/응답/입력/에러)를 보고하면 갱신된다(스텝 보고는 다음 조각).
 */
@Entity
@Table(
        name = "EXECUTION_STEP",
        indexes = {
                @Index(name = "IDX_EXECUTION_STEP_RECIPE", columnList = "EXECUTION_RECIPE_ID, STEP_INDEX")
        }
)
public class ExecutionStep extends BaseEntity {

    /** PK */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 소속 레시피 실행 */
    @Column(name = "EXECUTION_RECIPE_ID", nullable = false)
    private Long executionRecipeId;

    /** 스텝 순서 */
    @Column(name = "STEP_INDEX", nullable = false)
    private Integer stepIndex;

    /** 스텝명 (스냅샷) */
    @Column(name = "STEP_NAME", length = 200)
    private String stepName;

    /** 스텝 타입: API / SCRIPT / RECIPE / USER_INPUT */
    @Enumerated(EnumType.STRING)
    @Column(name = "STEP_TYPE", length = 20, nullable = false)
    private StepType stepType;

    /** 상태: PENDING / SUCCESS / FAILED / SKIPPED */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private ExecutionStepStatus status = ExecutionStepStatus.PENDING;

    /** 결과 한 줄 요약 (진행 표시용, 30자 내외) */
    @Column(name = "SUMMARY", length = 500)
    private String summary;

    /** 사용자 입력값 (JSON) */
    @Lob
    @Column(name = "USER_INPUT_JSON", columnDefinition = "LONGTEXT")
    private String userInputJson;

    /** 원시 응답 (1MB 초과 시 잘라 저장, JSON) */
    @Lob
    @Column(name = "RESPONSE_JSON", columnDefinition = "LONGTEXT")
    private String responseJson;

    /** 실패 시 에러 메시지 */
    @Column(name = "ERROR_MESSAGE", length = 1000)
    private String errorMessage;

    /** 시작 시각 */
    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    /** 종료 시각 */
    @Column(name = "FINISHED_AT")
    private LocalDateTime finishedAt;

    protected ExecutionStep() {
    }

    public ExecutionStep(Long executionRecipeId, Integer stepIndex, StepType stepType) {
        this.executionRecipeId = executionRecipeId;
        this.stepIndex = stepIndex;
        this.stepType = stepType;
        this.status = ExecutionStepStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Long getExecutionRecipeId() {
        return executionRecipeId;
    }

    public Integer getStepIndex() {
        return stepIndex;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public StepType getStepType() {
        return stepType;
    }

    public void setStepType(StepType stepType) {
        this.stepType = stepType;
    }

    public ExecutionStepStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStepStatus status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getUserInputJson() {
        return userInputJson;
    }

    public void setUserInputJson(String userInputJson) {
        this.userInputJson = userInputJson;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
