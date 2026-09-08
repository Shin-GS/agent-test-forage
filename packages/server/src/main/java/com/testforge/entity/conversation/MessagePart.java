package com.testforge.entity.conversation;

import com.testforge.entity.common.BaseEntity;
import com.testforge.entity.conversation.enums.PartStatus;
import com.testforge.entity.conversation.enums.PartType;
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

/**
 * 한 턴({@link Message}) 안의 순서 있는 블록 (MESSAGE_PART). 화면 렌더 단위이자, 실행/조회 사실 계층을
 * 가리키는 참조점이다(db/conversation.md). 동종 파트 N개 허용(TEXT 여러 개, 한 턴에 실행 여러 번 등).
 * 정렬은 {@code ID} 오름차순(append-only = 생성순 = 표시순, 별도 SEQ 없음).
 *
 * <p>{@code content} = TEXT 파트의 표시 본문(Markdown, 표시 진실). {@code payloadJson} = 타입별 구조화
 * 데이터(진실). 실행/조회류 파트는 {@code executionId}/{@code investigationId}로 사실 계층을 가리키고,
 * {@code payloadJson}은 그 시점 렌더 스냅샷을 담는다. 자주 조인/필터하는 참조(executionId/investigationId/
 * cardType)만 정식 컬럼으로 두고 나머지 타입별 잔여는 {@code payloadJson}에 둔다.
 */
@Entity
@Table(
        name = "MESSAGE_PART",
        indexes = {
                // 턴별 파트 정렬 조회
                @Index(name = "IDX_MESSAGE_PART_MESSAGE", columnList = "MESSAGE_ID, ID"),
                // 실행별 파트 역조회
                @Index(name = "IDX_MESSAGE_PART_EXECUTION", columnList = "EXECUTION_ID"),
                // 조회별 파트 역조회
                @Index(name = "IDX_MESSAGE_PART_INVESTIGATION", columnList = "INVESTIGATION_ID")
        }
)
public class MessagePart extends BaseEntity {

    /** 파트 ID (PK). 턴 내 파트 순서 기준(오름차순). 별도 SEQ 없음 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 소속 턴 ID */
    @Column(name = "MESSAGE_ID", nullable = false)
    private Long messageId;

    /** 파트 타입 */
    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", length = 30, nullable = false)
    private PartType type;

    /** 파트 상태 (타입별 상태머신) */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private PartStatus status;

    /** TEXT 파트 본문 (Markdown, 표시 진실). 없으면 NULL */
    @Lob
    @Column(name = "CONTENT", columnDefinition = "LONGTEXT")
    private String content;

    /** 실행류 파트(PROGRESS/RESULT/execution_mode 카드/ACTION_PICKER)가 가리키는 실행. 없으면 NULL */
    @Column(name = "EXECUTION_ID")
    private Long executionId;

    /** INVESTIGATE 파트가 가리키는 조회. 없으면 NULL */
    @Column(name = "INVESTIGATION_ID")
    private Long investigationId;

    /** CARD 파트 세부 유형(plan/candidates/service_select/auth_required/retry 등). 없으면 NULL */
    @Column(name = "CARD_TYPE", length = 30)
    private String cardType;

    /** 타입별 구조화 데이터(FK로 안 빠지는 잔여 — steps/buttons/variables/references 등). 없으면 NULL */
    @Lob
    @Column(name = "PAYLOAD_JSON", columnDefinition = "LONGTEXT")
    private String payloadJson;

    /** payload 스키마 버전 (messaging.md 버전 폴백). 1부터 시작 */
    @Column(name = "SCHEMA_VERSION", nullable = false)
    private Integer schemaVersion = 1;

    protected MessagePart() {
    }

    public MessagePart(Long messageId, PartType type, PartStatus status) {
        this.messageId = messageId;
        this.type = type;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public PartType getType() {
        return type;
    }

    public void setType(PartType type) {
        this.type = type;
    }

    public PartStatus getStatus() {
        return status;
    }

    public void setStatus(PartStatus status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public Long getInvestigationId() {
        return investigationId;
    }

    public void setInvestigationId(Long investigationId) {
        this.investigationId = investigationId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
}
