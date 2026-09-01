package com.testforge.entity.execution;

import com.testforge.entity.common.BaseEntity;
import com.testforge.entity.execution.enums.ExecutionRecipeStatus;
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
 * 플랜 내 레시피별 실행 = 1행 (EXECUTION_RECIPE, db/execution.md). 단일 실행이면 1건.
 *
 * <p>실행 시작 시점의 레시피를 통째로 {@code recipeSnapshotJson}에 복사 저장한다. 원본 레시피가
 * 수정/삭제돼도 히스토리에서 "그때 그 실행"을 정확히 재현하기 위함이다(execution.md 스냅샷).
 * {@code recipeId}는 원본 링크(참고용, 삭제 대비 NULL 허용).
 */
@Entity
@Table(
        name = "EXECUTION_RECIPE",
        indexes = {
                @Index(name = "IDX_EXECUTION_RECIPE_EXEC", columnList = "EXECUTION_ID")
        }
)
public class ExecutionRecipe extends BaseEntity {

    /** PK */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 소속 실행 */
    @Column(name = "EXECUTION_ID", nullable = false)
    private Long executionId;

    /** 원본 레시피 링크 (삭제 대비 NULL 허용) */
    @Column(name = "RECIPE_ID")
    private Long recipeId;

    /** 실행 시점 레시피명 (스냅샷) */
    @Column(name = "RECIPE_NAME", length = 100)
    private String recipeName;

    /** 실행 시점 레시피 버전 번호 (참고용) */
    @Column(name = "RECIPE_VERSION_NO")
    private Integer recipeVersionNo;

    /** 실행 시점 레시피 전체 스냅샷 (메타+스텝+변수+결과정의, JSON) */
    @Lob
    @Column(name = "RECIPE_SNAPSHOT_JSON", columnDefinition = "LONGTEXT")
    private String recipeSnapshotJson;

    /** 플랜 내 순서 (단일은 0) */
    @Column(name = "SEQUENCE", nullable = false)
    private Integer sequence;

    /** 상태: PENDING / RUNNING / SUCCESS / SKIPPED / FAILED / STOPPED */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private ExecutionRecipeStatus status = ExecutionRecipeStatus.PENDING;

    /** 이 레시피의 결과 정의 값 (다음 레시피 입력/템플릿용, JSON) */
    @Lob
    @Column(name = "RESULT_VALUES_JSON", columnDefinition = "LONGTEXT")
    private String resultValuesJson;

    /** 시작 시각 */
    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    /** 종료 시각 */
    @Column(name = "FINISHED_AT")
    private LocalDateTime finishedAt;

    protected ExecutionRecipe() {
    }

    public ExecutionRecipe(Long executionId, Integer sequence) {
        this.executionId = executionId;
        this.sequence = sequence;
        this.status = ExecutionRecipeStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public Long getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(Long recipeId) {
        this.recipeId = recipeId;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public void setRecipeName(String recipeName) {
        this.recipeName = recipeName;
    }

    public Integer getRecipeVersionNo() {
        return recipeVersionNo;
    }

    public void setRecipeVersionNo(Integer recipeVersionNo) {
        this.recipeVersionNo = recipeVersionNo;
    }

    public String getRecipeSnapshotJson() {
        return recipeSnapshotJson;
    }

    public void setRecipeSnapshotJson(String recipeSnapshotJson) {
        this.recipeSnapshotJson = recipeSnapshotJson;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public ExecutionRecipeStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionRecipeStatus status) {
        this.status = status;
    }

    public String getResultValuesJson() {
        return resultValuesJson;
    }

    public void setResultValuesJson(String resultValuesJson) {
        this.resultValuesJson = resultValuesJson;
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
