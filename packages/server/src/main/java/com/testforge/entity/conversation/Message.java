package com.testforge.entity.conversation;

import com.testforge.entity.conversation.enums.MessageRole;
import com.testforge.entity.conversation.enums.MessageStatus;
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
 * 대화 턴 = 1행 (MESSAGE). 한 턴은 사용자 발화 1개 또는 AI 응답 1턴이다. 화면에 그려지는 실제 블록
 * (텍스트·카드·진행·결과·조회·액션피커·참고자료)은 이 턴에 딸린 {@link MessagePart}로 저장한다
 * (db/conversation.md · messaging.md).
 *
 * <p>정렬·커서는 {@code ID}(auto-increment) 단독이다(별도 SEQ 없음). FE는 SSE 도착 순서가 아니라 턴
 * {@code ID}로 정렬하므로 시각 동률/SSE 순서 뒤바뀜 문제가 원천 없다. BaseEntity를 쓰지 않고 CREATED_AT만
 * 직접 둔다(턴은 생성 후 갱신 audit이 불필요).
 */
@Entity
@Table(
        name = "MESSAGE",
        indexes = {
                // 대화방 턴 커서 페이징 (WHERE conversation_id=? AND id<:cursor ORDER BY id DESC)
                @Index(name = "IDX_MESSAGE_CONVERSATION", columnList = "CONVERSATION_ID, ID")
        }
)
public class Message {

    /** 턴 ID (PK). 대화방 내 정렬·커서 기준(오름차순 = 시간순). 별도 SEQ 없음 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /** 소속 대화방 ID */
    @Column(name = "CONVERSATION_ID", nullable = false)
    private Long conversationId;

    /** 작성 주체: USER / ASSISTANT / SYSTEM */
    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", length = 20, nullable = false)
    private MessageRole role;

    /** 턴 전체 상태: STREAMING / COMPLETE / FAILED */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private MessageStatus status;

    /** 목록 미리보기·검색용 요약(파트에서 파생한 캐시, 진실 아님). 없으면 NULL */
    @Column(name = "CONTENT_PREVIEW", length = 500)
    private String contentPreview;

    /** 낙관적 UI 매칭용(사용자 메시지). 없으면 NULL */
    @Column(name = "CLIENT_MESSAGE_ID", length = 50)
    private String clientMessageId;

    /** 사용자 발화의 참조 태그(레시피 ID 등). 없으면 NULL */
    @Column(name = "REFERENCE_ID", length = 50)
    private String referenceId;

    /** 생성 시각 */
    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    protected Message() {
    }

    public Message(Long conversationId, MessageRole role, MessageStatus status) {
        this.conversationId = conversationId;
        this.role = role;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public MessageRole getRole() {
        return role;
    }

    public void setRole(MessageRole role) {
        this.role = role;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public String getContentPreview() {
        return contentPreview;
    }

    public void setContentPreview(String contentPreview) {
        this.contentPreview = contentPreview;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
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
