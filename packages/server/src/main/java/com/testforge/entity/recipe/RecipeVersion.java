package com.testforge.entity.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.LocalDateTime;

/**
 * 레시피 수정 이력 스냅샷 (RECIPE_VERSION). 복원용.
 * 수정 시 저장 직전 상태를 통짜 JSON으로 보관한다 (메타+스텝+변수+결과).
 * (RECIPE_ID, VERSION_NO)가 유니크하며, 스냅샷이 JSON이라 복원 구현이 단순하다.
 */
@Entity
@Table(
        name = "RECIPE_VERSION",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_RECIPE_VERSION",
                columnNames = {"RECIPE_ID", "VERSION_NO"}
        )
)
@EntityListeners(AuditingEntityListener.class)
public class RecipeVersion {

    /** 버전 스냅샷 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 대상 레시피 ID (FK, 논리 참조) */
    @Column(name = "RECIPE_ID", nullable = false)
    private Long recipeId;

    /** 버전 번호 (스냅샷 시점의 CURRENT_VERSION) */
    @Column(name = "VERSION_NO", nullable = false)
    private int versionNo;

    /** 해당 버전의 전체 레시피 스냅샷 (메타+스텝+변수+결과) JSON */
    @Lob
    @Column(name = "SNAPSHOT_JSON", columnDefinition = "LONGTEXT")
    private String snapshotJson;

    /** 생성 시각 (버전 생성 = 수정 시점) */
    @CreatedDate
    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    protected RecipeVersion() {
    }

    public RecipeVersion(Long recipeId, int versionNo, String snapshotJson) {
        this.recipeId = recipeId;
        this.versionNo = versionNo;
        this.snapshotJson = snapshotJson;
    }

    public Long getId() {
        return id;
    }

    public Long getRecipeId() {
        return recipeId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
