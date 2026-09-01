package com.testforge.entity.conversation;

import com.testforge.entity.conversation.enums.MessageRole;
import com.testforge.entity.conversation.enums.MessageStatus;
import com.testforge.entity.conversation.enums.MessageType;
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
 * 대화 메시지 = 1행 (MESSAGE). 조회/렌더링을 위해 정규화하고,
 * 타입별 상세(cardType/executionId/buttons 등)는 METADATA_JSON에 보관한다(messaging.md).
 * BaseEntity를 쓰지 않고 CREATED_AT만 직접 둔다(스키마 정본: 메시지는 수정/갱신 audit 불필요).
 */
@Entity
@Table(
        name = "MESSAGE",
        indexes = {
                @Index(name = "IDX_MESSAGE_CONVERSATION", columnList = "CONVERSATION_ID, SEQ")
        }
)
public class Message {

    /** 메시지 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 소속 대화방 ID */
    @Column(name = "CONVERSATION_ID", nullable = false)
    private Long conversationId;

    /** 대화방 내 정렬 순서 (서버 발번, CREATED_AT 동시각 충돌 방지) */
    @Column(name = "SEQ", nullable = false)
    private Long seq;

    /** 작성 주체: USER / ASSISTANT / TOOL */
    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", length = 20, nullable = false)
    private MessageRole role;

    /** 표현 타입: TEXT / CARD / PROGRESS / ACTION_PICKER / SYSTEM */
    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", length = 20, nullable = false)
    private MessageType type;

    /** 상태: PENDING / COMPLETED / FAILED */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private MessageStatus status;

    /** 메시지 본문 (Markdown) */
    @Lob
    @Column(name = "CONTENT", columnDefinition = "LONGTEXT")
    private String content;

    /** 타입별 상세 (JSON 문자열). cardType/executionId/buttons 등 */
    @Column(name = "METADATA_JSON", columnDefinition = "TEXT")
    private String metadataJson;

    /** 참조 태그 (레시피 ID 등). 없으면 NULL */
    @Column(name = "REFERENCE_ID", length = 50)
    private String referenceId;

    /** 생성 시각 */
    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    protected Message() {
    }

    public Message(Long conversationId, Long seq, MessageRole role, MessageType type, MessageStatus status) {
        this.conversationId = conversationId;
        this.seq = seq;
        this.role = role;
        this.type = type;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public MessageRole getRole() {
        return role;
    }

    public void setRole(MessageRole role) {
        this.role = role;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
