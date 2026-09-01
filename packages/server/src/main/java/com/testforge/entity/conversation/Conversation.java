package com.testforge.entity.conversation;

import com.testforge.entity.common.BaseEntity;
import com.testforge.entity.conversation.enums.ConversationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 대화방(세션) = 1행 (CONVERSATION). 채팅 대화의 컨테이너.
 * 대화방 삭제(soft)해도 실행 히스토리는 독립 유지된다(execution.md).
 * SSE/락/AI 상태 전이는 이번 스코프가 아니며, status는 컬럼/enum만 준비한다.
 */
@Entity
@Table(
        name = "CONVERSATION",
        indexes = {
                @Index(name = "IDX_CONVERSATION_USER", columnList = "USER_ID, LAST_MESSAGE_AT"),
                @Index(name = "IDX_CONVERSATION_SPEC", columnList = "API_SPEC_ID")
        }
)
public class Conversation extends BaseEntity {

    /** 대화방 ID (PK) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 소유자 사용자 ID */
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    /** 대화 제목 (자동 생성 또는 사용자 변경) */
    @Column(name = "TITLE", length = 200)
    private String title;

    /** 대화방 대상 서비스(스펙) ID. 미지정 시 NULL */
    @Column(name = "API_SPEC_ID")
    private Long apiSpecId;

    /** 대화방 처리 상태 (기본 IDLE) */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private ConversationStatus status = ConversationStatus.IDLE;

    /** 마지막 메시지 시각 (목록 최신순 정렬). 첫 메시지 전송 시 갱신 */
    @Column(name = "LAST_MESSAGE_AT")
    private LocalDateTime lastMessageAt;

    /** 사용자가 마지막으로 읽은 시각 (안 읽음 판정). 미열람이면 NULL */
    @Column(name = "LAST_READ_AT")
    private LocalDateTime lastReadAt;

    /** 소프트 삭제 시각 (NULL이면 유효한 대화방) */
    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    protected Conversation() {
    }

    public Conversation(Long userId) {
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getApiSpecId() {
        return apiSpecId;
    }

    public void setApiSpecId(Long apiSpecId) {
        this.apiSpecId = apiSpecId;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public void setStatus(ConversationStatus status) {
        this.status = status;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public LocalDateTime getLastReadAt() {
        return lastReadAt;
    }

    public void setLastReadAt(LocalDateTime lastReadAt) {
        this.lastReadAt = lastReadAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
