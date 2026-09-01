package com.testforge.service.conversation;

import com.testforge.common.error.ApiException;
import com.testforge.dto.common.StatusView;
import com.testforge.dto.conversation.ConversationDetailResponse;
import com.testforge.dto.conversation.ConversationStartRequest;
import com.testforge.dto.conversation.ConversationStartResponse;
import com.testforge.dto.conversation.ConversationSummaryResponse;
import com.testforge.dto.conversation.MessageResponse;
import com.testforge.dto.conversation.MessageSendRequest;
import com.testforge.dto.conversation.MessageSendResponse;
import com.testforge.entity.conversation.Conversation;
import com.testforge.entity.conversation.Message;
import com.testforge.entity.conversation.enums.MessageRole;
import com.testforge.entity.conversation.enums.MessageStatus;
import com.testforge.entity.conversation.enums.MessageType;
import com.testforge.repository.conversation.ConversationRepository;
import com.testforge.repository.conversation.MessageRepository;
import com.testforge.utils.ConversationTitleUtil;
import com.testforge.utils.RecipeJsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 대화방/메시지 CRUD + 메시지 접수(저장) 로직.
 *
 * <p>대화방은 빈 상태로 생성하지 않는다. 첫 메시지가 대화방을 생성하며(start),
 * 이후 메시지는 기존 대화방에 이어서 전송한다(sendMessage). 이렇게 하여 메시지가
 * 하나도 없는 orphan 대화방을 원천 차단한다.
 *
 * <p>이번 스코프는 순수 CRUD + 저장까지만이다. SSE 발행, 대화방 단위 락,
 * AI 처리/상태 전이(AI_RESPONDING 등)는 다음 조각에서 다루며 여기서는 다루지 않는다.
 * metadata JSON은 문자열로 저장하고 응답에서 다시 객체로 파싱해 내린다
 * (RecipeService/SpecQueryService와 동일한 로컬 Jackson 헬퍼 패턴).
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * 첫 메시지로 대화방을 생성한다(한 트랜잭션). 대화방 생성 → 제목 결정 →
     * 첫 메시지 저장(seq=1) → lastMessageAt 갱신을 원자적으로 처리한다.
     * 빈 대화방을 만들지 않아 orphan 대화방을 원천 차단한다.
     */
    @Transactional
    public ConversationStartResponse start(ConversationStartRequest request) {
        if (request.userId() == null) {
            throw ApiException.invalidRequest("userId is required");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw ApiException.invalidRequest("content is required");
        }

        // 1) 대화방 생성 + 제목 결정 (지정 title 우선, 없으면 content로 임시 파생)
        Conversation conversation = new Conversation(request.userId());
        conversation.setTitle(ConversationTitleUtil.resolveTitle(request.title(), request.content()));
        conversation.setApiSpecId(request.apiSpecId());
        Conversation savedConversation = conversationRepository.save(conversation);

        // 2) 첫 메시지 저장 (seq=1)
        Message message = new Message(savedConversation.getId(), 1L,
                MessageRole.USER, MessageType.TEXT, MessageStatus.COMPLETED);
        message.setContent(request.content());
        message.setReferenceId(request.referenceId());
        message.setMetadataJson(RecipeJsonUtil.toJsonString(request.metadata()));
        Message savedMessage = messageRepository.save(message);

        // 3) 목록 최신순 정렬 + 안 읽음 판정 기준 갱신
        savedConversation.setLastMessageAt(savedMessage.getCreatedAt());
        conversationRepository.save(savedConversation);

        // TODO: AI 처리 트리거 + SSE 발행 (session_status 전이 포함) — 다음 조각(chat 실행 엔진)에서 추가
        log.info("Conversation started with first message: conversationId={}, messageId={}",
                savedConversation.getId(), savedMessage.getId());

        return new ConversationStartResponse(true, toDetail(savedConversation), toMessage(savedMessage));
    }

    /** 사용자별 미삭제 대화방 목록 (lastMessageAt DESC). unread는 서버 계산. */
    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> list(Long userId) {
        if (userId == null) {
            throw ApiException.invalidRequest("userId is required");
        }
        return conversationRepository
                .findByUserIdAndDeletedAtIsNullOrderByLastMessageAtDesc(userId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    /** 미삭제 대화방 상세. 없거나 삭제된 경우 404. */
    @Transactional(readOnly = true)
    public ConversationDetailResponse detail(Long id) {
        Conversation conversation = getActiveOrThrow(id);
        return toDetail(conversation);
    }

    /** 대화방 이름 변경. 없거나 삭제 시 404, 빈 제목이면 400. */
    @Transactional
    public ConversationDetailResponse updateTitle(Long id, String title) {
        if (title == null || title.isBlank()) {
            throw ApiException.invalidRequest("title is required");
        }
        Conversation conversation = getActiveOrThrow(id);
        conversation.setTitle(title.trim());
        Conversation saved = conversationRepository.save(conversation);
        log.info("Conversation title updated: conversationId={}", id);
        return toDetail(saved);
    }

    /** 읽음 처리 (lastReadAt = now). 없거나 삭제 시 404. */
    @Transactional
    public ConversationDetailResponse markRead(Long id) {
        Conversation conversation = getActiveOrThrow(id);
        conversation.setLastReadAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(conversation);
        log.info("Conversation marked read: conversationId={}", id);
        return toDetail(saved);
    }

    /** 소프트 삭제 (DELETED_AT = now). 없거나 이미 삭제 시 404. */
    @Transactional
    public void softDelete(Long id) {
        Conversation conversation = getActiveOrThrow(id);
        conversation.setDeletedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        // TODO: 대화 삭제 시 연결된 EXECUTION.CONVERSATION_ID = NULL (히스토리 독립, execution.md) — execution 도메인 구현 후
        log.info("Conversation soft-deleted: conversationId={}", id);
    }

    /** 대화방 메시지 목록 (SEQ 오름차순). 없거나 삭제된 대화방이면 404. */
    @Transactional(readOnly = true)
    public List<MessageResponse> listMessages(Long conversationId) {
        getActiveOrThrow(conversationId);
        return messageRepository.findByConversationIdOrderBySeqAsc(conversationId)
                .stream()
                .map(this::toMessage)
                .toList();
    }

    /**
     * 메시지 전송(동기 접수). 사용자 메시지를 저장하고 lastMessageAt을 갱신한다.
     * SEQ는 대화방 내 max+1로 발번한다. AI 처리/SSE 발행은 이번 스코프가 아니다.
     */
    @Transactional
    public MessageSendResponse sendMessage(Long conversationId, MessageSendRequest request) {
        Conversation conversation = getActiveOrThrow(conversationId);

        if (request.content() == null || request.content().isBlank()) {
            throw ApiException.invalidRequest("content is required");
        }

        long nextSeq = nextSeq(conversationId);
        Message message = new Message(conversationId, nextSeq,
                MessageRole.USER, MessageType.TEXT, MessageStatus.COMPLETED);
        message.setContent(request.content());
        message.setReferenceId(request.referenceId());
        message.setMetadataJson(RecipeJsonUtil.toJsonString(request.metadata()));

        Message saved = messageRepository.save(message);

        // 목록 최신순 정렬 + 안 읽음 판정 기준 갱신
        conversation.setLastMessageAt(saved.getCreatedAt());
        conversationRepository.save(conversation);

        // TODO: AI 처리 트리거 + SSE 발행 (session_status 전이 포함) — 다음 조각(chat 실행 엔진)에서 추가
        log.info("Message accepted: conversationId={}, messageId={}, seq={}",
                conversationId, saved.getId(), saved.getSeq());

        return new MessageSendResponse(true, conversationId, toMessage(saved));
    }

    // ── helpers ──

    /** 미삭제 대화방 조회 (없거나 삭제 시 404) */
    private Conversation getActiveOrThrow(Long id) {
        return conversationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.conversationNotFound(id));
    }

    /** 대화방 내 다음 SEQ (max+1, 첫 메시지는 1) */
    private long nextSeq(Long conversationId) {
        Long maxSeq = messageRepository.findMaxSeq(conversationId);
        return (maxSeq == null ? 0L : maxSeq) + 1L;
    }

    /** 안 읽음 판정: lastMessageAt > lastReadAt. lastReadAt가 null이고 메시지가 있으면 안 읽음 */
    private boolean isUnread(Conversation conversation) {
        LocalDateTime lastMessageAt = conversation.getLastMessageAt();
        if (lastMessageAt == null) {
            return false;
        }
        LocalDateTime lastReadAt = conversation.getLastReadAt();
        return lastReadAt == null || lastMessageAt.isAfter(lastReadAt);
    }

    /** 목록 행 매핑 */
    private ConversationSummaryResponse toSummary(Conversation conversation) {
        return new ConversationSummaryResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getApiSpecId(),
                StatusView.of(conversation.getStatus()),
                conversation.getLastMessageAt(),
                conversation.getLastReadAt(),
                isUnread(conversation),
                conversation.getCreatedAt());
    }

    /** 상세 매핑 */
    private ConversationDetailResponse toDetail(Conversation conversation) {
        return new ConversationDetailResponse(
                conversation.getId(),
                conversation.getUserId(),
                conversation.getTitle(),
                conversation.getApiSpecId(),
                StatusView.of(conversation.getStatus()),
                conversation.getLastMessageAt(),
                conversation.getLastReadAt(),
                isUnread(conversation),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }

    /** 메시지 매핑 (metadata JSON 문자열을 객체로 파싱해서 내림) */
    private MessageResponse toMessage(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                message.getSeq(),
                StatusView.of(message.getRole()),
                StatusView.of(message.getType()),
                StatusView.of(message.getStatus()),
                message.getContent(),
                RecipeJsonUtil.toObject(message.getMetadataJson()),
                message.getReferenceId(),
                message.getCreatedAt());
    }
}
