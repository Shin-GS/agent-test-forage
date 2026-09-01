package com.testforge.entity.recipe;

import com.testforge.entity.common.BaseEntity;
import com.testforge.entity.recipe.enums.ValidationStatus;
import com.testforge.entity.recipe.enums.Visibility;
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
 * 레시피 정의 = 1행 (RECIPE). 조회/필터가 필요한 메타(name/visibility/service)만 컬럼으로 두고,
 * 스텝/변수/결과정의는 구조가 제각각이라 JSON 문자열 컬럼으로 통짜 저장한다.
 * 스텝 내부의 API 참조(endpointId)와 서브레시피 참조(recipeId)는 JSON 안에 논리 참조로 보관하며,
 * 물리 FK를 걸지 않고 저장 시점에 파싱하여 유효성을 검증한다.
 */
@Entity
@Table(
        name = "RECIPE",
        indexes = {
                @Index(name = "IDX_RECIPE_SPEC", columnList = "API_SPEC_ID"),
                @Index(name = "IDX_RECIPE_OWNER", columnList = "OWNER_USER_ID"),
                @Index(name = "IDX_RECIPE_VISIBILITY", columnList = "VISIBILITY")
        }
)
public class Recipe extends BaseEntity {

    /** 레시피 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 작성자 (개인/공통 모두 작성자 기록). 공통 여부는 VISIBILITY로 판단 */
    @Column(name = "OWNER_USER_ID", nullable = false)
    private Long ownerUserId;

    /** 대상 서비스(스펙) ID. 스텝이 참조하는 엔드포인트가 속한 스펙 */
    @Column(name = "API_SPEC_ID", nullable = false)
    private Long apiSpecId;

    /** 레시피명 (식별 + AI 매칭용) */
    @Column(name = "NAME", length = 100, nullable = false)
    private String name;

    /** 설명 (AI 의도 매칭용) */
    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    /** 공개 범위: COMMON(공통) / PRIVATE(개인) */
    @Enumerated(EnumType.STRING)
    @Column(name = "VISIBILITY", length = 20, nullable = false)
    private Visibility visibility = Visibility.PRIVATE;

    /** 분류/검색 태그 배열 (JSON 배열 문자열로 저장) */
    @Column(name = "TAGS", columnDefinition = "TEXT")
    private String tags;

    /** 사용자 입력 변수 정의 (JSON 문자열) */
    @Column(name = "VARIABLES_JSON", columnDefinition = "TEXT")
    private String variablesJson;

    /** 스텝 목록 (JSON 문자열). 타입/매핑/조건/extract 포함. 검증 시 파싱 대상 */
    @Lob
    @Column(name = "STEPS_JSON", columnDefinition = "LONGTEXT")
    private String stepsJson;

    /** 결과 정의 (JSON 문자열) */
    @Column(name = "RESULT_DEFINITION_JSON", columnDefinition = "TEXT")
    private String resultDefinitionJson;

    /** 결과 메시지 템플릿. 없으면 AI 요약 */
    @Column(name = "RESULT_TEMPLATE", columnDefinition = "TEXT")
    private String resultTemplate;

    /** 현재 버전 번호 (1부터 시작, 수정 저장마다 +1) */
    @Column(name = "CURRENT_VERSION", nullable = false)
    private int currentVersion = 1;

    /** 유효성 검증 상태: VALID / INVALID / UNVALIDATED */
    @Enumerated(EnumType.STRING)
    @Column(name = "VALIDATION_STATUS", length = 20, nullable = false)
    private ValidationStatus validationStatus = ValidationStatus.UNVALIDATED;

    /** 검증 실패 상세 메시지 (VALID면 null) */
    @Column(name = "VALIDATION_MESSAGE", length = 1000)
    private String validationMessage;

    /** 사용 횟수 (정렬용, 기본 0) */
    @Column(name = "USAGE_COUNT", nullable = false)
    private int usageCount = 0;

    /** 마지막 사용 시각 (미사용이면 null) */
    @Column(name = "LAST_USED_AT")
    private LocalDateTime lastUsedAt;

    /** 소프트 삭제 시각 (NULL이면 유효한 레시피) */
    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    protected Recipe() {
    }

    public Recipe(Long ownerUserId, Long apiSpecId, String name) {
        this.ownerUserId = ownerUserId;
        this.apiSpecId = apiSpecId;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getVariablesJson() {
        return variablesJson;
    }

    public void setVariablesJson(String variablesJson) {
        this.variablesJson = variablesJson;
    }

    public String getStepsJson() {
        return stepsJson;
    }

    public void setStepsJson(String stepsJson) {
        this.stepsJson = stepsJson;
    }

    public String getResultDefinitionJson() {
        return resultDefinitionJson;
    }

    public void setResultDefinitionJson(String resultDefinitionJson) {
        this.resultDefinitionJson = resultDefinitionJson;
    }

    public String getResultTemplate() {
        return resultTemplate;
    }

    public void setResultTemplate(String resultTemplate) {
        this.resultTemplate = resultTemplate;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public ValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(ValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getValidationMessage() {
        return validationMessage;
    }

    public void setValidationMessage(String validationMessage) {
        this.validationMessage = validationMessage;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(int usageCount) {
        this.usageCount = usageCount;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
