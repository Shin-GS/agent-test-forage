package com.testforge.service.conversation;

import com.testforge.common.error.ApiException;
import com.testforge.dto.common.CursorPage;
import com.testforge.dto.common.StatusView;
import com.testforge.dto.conversation.AssistantMessageDraft;
import com.testforge.dto.conversation.ConversationDetailResponse;
import com.testforge.dto.conversation.ConversationListSnapshot;
import com.testforge.dto.conversation.ConversationStartRequest;
import com.testforge.dto.conversation.ConversationStartResponse;
import com.testforge.dto.conversation.ConversationSummaryResponse;
import com.testforge.dto.conversation.MessageResponse;
import com.testforge.dto.conversation.MessageSendRequest;
import com.testforge.dto.conversation.MessageUpdatePayload;
import com.testforge.dto.conversation.MessageSendResponse;
import com.testforge.dto.conversation.SessionListUpdatePayload;
import com.testforge.dto.conversation.SessionStatusPayload;
import com.testforge.entity.conversation.Conversation;
import com.testforge.entity.conversation.Message;
import com.testforge.entity.conversation.enums.ConversationStatus;
import com.testforge.entity.conversation.enums.MessageRole;
import com.testforge.entity.conversation.enums.MessageStatus;
import com.testforge.entity.conversation.enums.MessageType;
import com.testforge.entity.execution.enums.ExecutionStatus;
import com.testforge.lock.ConversationLock;
import com.testforge.repository.conversation.ConversationRepository;
import com.testforge.repository.conversation.MessageRepository;
import com.testforge.service.execution.ExecutionService;
import com.testforge.sse.SseEventPublisher;
import com.testforge.sse.enums.SseEventType;
import com.testforge.utils.ConversationTitleUtil;
import com.testforge.utils.RecipeJsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 대화방/메시지 CRUD + 메시지 접수(저장) 로직.
 *
 * <p>대화방은 빈 상태로 생성하지 않는다. 첫 메시지가 대화방을 생성하며(start),
 * 이후 메시지는 기존 대화방에 이어서 전송한다(sendMessage). 이렇게 하여 메시지가
 * 하나도 없는 orphan 대화방을 원천 차단한다.
 *
 * <p>CRUD + 저장에 더해, 대화방 단위 락(동시성 제어), 상태 전이(session_status)와 SSE 발행,
 * 취소/중지, 서버 기동 복구를 담당한다. 메시지 접수 시 락을 장기 점유하고 {@code ai_responding}으로
 * 전이한 뒤 {@link ChatRequestedEvent}를 발행하며, AI 처리 종결({@link #completeAssistantTurn})
 * 시점에 {@code idle} 전이 + 락 해제를 수행한다. 실제 tool 분기/컨텍스트 조립은 ChatProcessor가 담당한다.
 * metadata JSON은 문자열로 저장하고 응답에서 다시 객체로 파싱해 내린다
 * (RecipeService/SpecQueryService와 동일한 로컬 Jackson 헬퍼 패턴).
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SseEventPublisher ssePublisher;
    private final ConversationLock conversationLock;
    private final ApplicationEventPublisher eventPublisher;
    private final ExecutionService executionService;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository,
                               SseEventPublisher ssePublisher,
                               ConversationLock conversationLock,
                               ApplicationEventPublisher eventPublisher,
                               ExecutionService executionService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.ssePublisher = ssePublisher;
        this.conversationLock = conversationLock;
        this.eventPublisher = eventPublisher;
        this.executionService = executionService;
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

        // 3) 대화방 선점 + 처리 중 상태 전이(ai_responding). 방금 생성한 대화방이라 락 경합은 없어
        //    tryLock 반환값을 검사하지 않는다. AI 처리 종결(completeAssistantTurn) 시점까지 점유를 유지한다.
        //    sendMessage와 달리 예외 시 unlock을 두지 않는 이유: 이 지점 이후 남은 코드는 상태 변경/저장/
        //    이벤트 등록뿐이라 예외 지점이 사실상 없고, save 실패 시엔 트랜잭션이 롤백되어 대화방 자체가
        //    사라지므로 그 ID로 다시 요청이 올 수 없다(인메모리 락이 남아도 충돌 불가).
        Long conversationId = savedConversation.getId();
        conversationLock.tryLock(conversationId);
        savedConversation.setStatus(ConversationStatus.AI_RESPONDING);

        // 목록 최신순 정렬 + 안 읽음 판정 기준 갱신
        savedConversation.setLastMessageAt(savedMessage.getCreatedAt());
        conversationRepository.save(savedConversation);

        // SSE: 첫 메시지 도착(message_new) + 처리중 상태(session_status) + 목록에 추가(session_list_update upsert).
        // 커밋 후 발행하여 확정 데이터로 내보내고, 발행 실패가 트랜잭션을 깨지 않게 한다.
        Long ownerId = savedConversation.getUserId();
        MessageResponse messageView = toMessage(savedMessage);
        ConversationListSnapshot snapshot = toListSnapshot(savedConversation);
        publishAfterCommit(ownerId, SseEventType.MESSAGE_NEW, conversationId, messageView);
        publishAfterCommit(ownerId, SseEventType.SESSION_STATUS, conversationId,
                SessionStatusPayload.of(conversationId, ConversationStatus.AI_RESPONDING));
        publishAfterCommit(ownerId, SseEventType.SESSION_LIST_UPDATE, conversationId,
                SessionListUpdatePayload.upsert(snapshot));

        // AI 처리 트리거: 커밋 후 비동기로 ChatProcessor 구동(ChatRequestedListener). 락 유지 → 종결 시 해제.
        eventPublisher.publishEvent(new ChatRequestedEvent(conversationId, ownerId));

        log.info("Conversation started with first message: conversationId={}, messageId={}",
                conversationId, savedMessage.getId());

        return new ConversationStartResponse(true, toDetail(savedConversation), messageView);
    }

    /**
     * 사용자별 미삭제 대화방 목록 (lastMessageAt DESC, 최근 200건). unread는 서버 계산.
     *
     * <p>대화방 목록은 사용자가 직접 만드는 개수(보통 10~20개)라 무한 스크롤/커서가 필요하지 않다.
     * 사이드바 전체 목록으로 한 번에 반환하되, 비정상 폭증에 대비해 최근 200건 상한만 둔다
     * (메시지/실행 히스토리와 달리 무한정 쌓이는 데이터가 아님). 상한을 넘기는 상황이 실제로 생기면
     * 그때 오래된 대화 정리 UX를 검토한다.
     */
    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> list(Long userId) {
        if (userId == null) {
            throw ApiException.invalidRequest("userId is required");
        }
        return conversationRepository
                .findTop200ByUserIdAndDeletedAtIsNullOrderByLastMessageAtDesc(userId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    /** 미삭제 대화방 상세. 없거나 삭제/타인 소유면 404. */
    @Transactional(readOnly = true)
    public ConversationDetailResponse detail(Long id, Long requesterId) {
        Conversation conversation = getOwnedOrThrow(id, requesterId);
        return toDetail(conversation);
    }

    /** 대화방 이름 변경. 없거나 삭제/타인 소유면 404, 빈 제목이면 400. */
    @Transactional
    public ConversationDetailResponse updateTitle(Long id, Long requesterId, String title) {
        if (title == null || title.isBlank()) {
            throw ApiException.invalidRequest("title is required");
        }
        Conversation conversation = getOwnedOrThrow(id, requesterId);
        conversation.setTitle(title.trim());
        Conversation saved = conversationRepository.save(conversation);

        // SSE: 목록 한 줄 갱신 (이름 변경 흡수)
        publishAfterCommit(saved.getUserId(), SseEventType.SESSION_LIST_UPDATE, saved.getId(),
                SessionListUpdatePayload.upsert(toListSnapshot(saved)));

        log.info("Conversation title updated: conversationId={}", id);
        return toDetail(saved);
    }

    /** 읽음 처리 (lastReadAt = now). 없거나 삭제/타인 소유면 404. */
    @Transactional
    public ConversationDetailResponse markRead(Long id, Long requesterId) {
        Conversation conversation = getOwnedOrThrow(id, requesterId);
        conversation.setLastReadAt(LocalDateTime.now());
        Conversation saved = conversationRepository.save(conversation);

        // SSE: 목록 한 줄 갱신 (읽음 → unread=false 를 모든 탭 뱃지에 동기화)
        publishAfterCommit(saved.getUserId(), SseEventType.SESSION_LIST_UPDATE, saved.getId(),
                SessionListUpdatePayload.upsert(toListSnapshot(saved)));

        log.info("Conversation marked read: conversationId={}", id);
        return toDetail(saved);
    }

    /**
     * 소프트 삭제 (DELETED_AT = now). 없거나 이미 삭제 시 404.
     *
     * <p>실행 중({@code EXECUTING})이면 삭제를 차단한다(409 {@code CONVERSATION_EXECUTING},
     * execution.md "실행 중 대화방 삭제 = 삭제 차단"). FE는 "중지하고 삭제" 선택 시 중지 API를 먼저
     * 호출해 idle로 만든 뒤 삭제한다. (AI 응답 중 등 다른 처리 상태는 짧게 끝나므로 차단하지 않는다.)
     */
    @Transactional
    public void softDelete(Long id, Long requesterId) {
        Conversation conversation = getOwnedOrThrow(id, requesterId);
        if (conversation.getStatus() == ConversationStatus.EXECUTING) {
            throw ApiException.conversationExecuting(id);
        }
        conversation.setDeletedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        // 대화방은 소프트 삭제라 row가 그대로 남는다. 연결된 실행(EXECUTION.CONVERSATION_ID)은
        // 그대로 유지한다 — 히스토리는 USER_ID 기준 조회라 대화 삭제와 무관하게 독립적으로 유지되고
        // (history.md), 연결을 남겨 두면 "이 실행이 나온 대화" 추적/복구가 가능하다. FK를 끊지 않는다.

        // SSE: 목록에서 제거 (모든 탭). 보고 있던 탭은 "삭제됨" 안내 후 목록 이동
        publishAfterCommit(conversation.getUserId(), SseEventType.SESSION_LIST_UPDATE, id,
                SessionListUpdatePayload.removed(id));

        log.info("Conversation soft-deleted: conversationId={}", id);
    }

    /**
     * 대화방 메시지의 커서 페이지 (채팅 무한 스크롤). 없거나 삭제된 대화방이면 404.
     *
     * <p><b>정렬은 SEQ DESC(최신순).</b> 채팅은 최신 메시지가 아래이고 위로 스크롤하면 과거를 불러오므로,
     * "다음 페이지 = 과거"다. 첫 페이지는 최신 size건, 이후는 {@code cursor}(가장 과거 seq)보다 더 과거를
     * 이어 조회한다. items는 최신순으로 내려가며, FE가 채팅 표시 시 역순(오래된 순)으로 렌더한다.
     * size 기본 20, 최대 50(과도 로딩 방지).
     *
     * @param conversationId 대상 대화방
     * @param cursor 이전 응답의 nextCursor(가장 과거 seq). null이면 첫 페이지(최신)
     * @param size 페이지 크기 (기본 20, 최대 50)
     */
    @Transactional(readOnly = true)
    public CursorPage<MessageResponse> listMessages(Long conversationId, Long requesterId,
                                                    String cursor, Integer size) {
        getOwnedOrThrow(conversationId, requesterId);
        int limit = normalizeMessageSize(size);
        Long cursorSeq = decodeSeqCursor(cursor);

        // hasNext(더 과거 존재) 판정을 위해 limit+1건 조회
        List<Message> rows = messageRepository.findByConversationIdBySeqCursor(
                conversationId, cursorSeq, org.springframework.data.domain.PageRequest.of(0, limit + 1));

        boolean hasNext = rows.size() > limit;
        List<Message> pageRows = hasNext ? rows.subList(0, limit) : rows;
        List<MessageResponse> items = pageRows.stream().map(this::toMessage).toList();
        if (!hasNext) {
            return CursorPage.last(items);
        }
        // 다음 커서 = 이번 페이지에서 가장 과거(가장 작은 seq) = 최신순 목록의 마지막 항목
        Message oldest = pageRows.get(pageRows.size() - 1);
        return CursorPage.of(items, String.valueOf(oldest.getSeq()));
    }

    /** 메시지 페이지 크기 정규화 (기본 20, 최대 50) */
    private int normalizeMessageSize(Integer size) {
        if (size == null || size <= 0) {
            return 20;
        }
        return Math.min(size, 50);
    }

    /** seq 커서 디코딩. null/빈/형식 불량이면 첫 페이지(null) */
    private Long decodeSeqCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid message cursor, treating as first page: {}", cursor);
            return null;
        }
    }

    /**
     * 메시지 전송(동기 접수). 사용자 메시지를 저장하고 lastMessageAt을 갱신한다.
     * SEQ는 대화방 내 max+1로 발번한다.
     *
     * <p><b>대화방 단위 락:</b> 진입 시 {@link ConversationLock#tryLock}으로 대화방을 선점한다.
     * 이미 처리 중이면(락 경합) 409 {@code CONVERSATION_BUSY}로 이중 전송을 막는다. 지금은 AI 처리가
     * 없어 저장 즉시 처리가 끝나므로 락은 저장 트랜잭션 동안만 짧게 잡고 커밋 후 해제한다. 실제 장기
     * 점유(AI 응답 중 락 유지 + {@code AI_RESPONDING} 전이)는 다음 조각(chat 실행 엔진)에서 다룬다.
     */
    @Transactional
    public MessageSendResponse sendMessage(Long conversationId, Long requesterId,
                                           MessageSendRequest request) {
        // 소유자 검증을 락 획득보다 먼저 수행한다. 타인이 남의 conversationId로 호출해도
        // 락을 건드리지 않고 404로 거절되어, 정당한 소유자가 락 경합(409)을 겪지 않는다.
        Conversation conversation = getOwnedOrThrow(conversationId, requesterId);

        // 대화방 선점. 이미 처리 중이면(락 경합) 이중 전송이므로 409로 거절한다.
        // 인메모리 락은 트랜잭션 자원이 아니므로 트랜잭션 안에서 잡아도 안전하다.
        if (!conversationLock.tryLock(conversationId)) {
            throw ApiException.conversationBusy(conversationId);
        }
        // 접수 처리 중 예외가 나면(저장 실패/검증 실패 등) 락을 해제해야 영구 잠금을 막는다.
        // 정상 접수 시에는 락을 유지하고, AI 처리가 종결(completeAssistantTurn)될 때 해제한다.
        boolean accepted = false;
        try {
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

            // 처리 중 상태로 전이(ai_responding): 모든 탭 입력 잠금. 목록 최신순/안 읽음 기준도 갱신.
            conversation.setStatus(ConversationStatus.AI_RESPONDING);
            conversation.setLastMessageAt(saved.getCreatedAt());
            conversationRepository.save(conversation);

            // SSE: 새 메시지(message_new) + 처리중 상태(session_status ai_responding) + 목록 한 줄 갱신
            Long ownerId = conversation.getUserId();
            MessageResponse messageView = toMessage(saved);
            publishAfterCommit(ownerId, SseEventType.MESSAGE_NEW, conversationId, messageView);
            publishAfterCommit(ownerId, SseEventType.SESSION_STATUS, conversationId,
                    SessionStatusPayload.of(conversationId, ConversationStatus.AI_RESPONDING));
            publishAfterCommit(ownerId, SseEventType.SESSION_LIST_UPDATE, conversationId,
                    SessionListUpdatePayload.upsert(toListSnapshot(conversation)));

            // AI 처리 트리거: 커밋 후 비동기로 ChatProcessor가 구동된다(ChatRequestedListener).
            // 락은 유지한 채로 넘기고, 처리 종결 시 completeAssistantTurn이 idle 전이 + 락 해제를 수행한다.
            eventPublisher.publishEvent(new ChatRequestedEvent(conversationId, ownerId));
            accepted = true;

            log.info("Message accepted: conversationId={}, messageId={}, seq={}",
                    conversationId, saved.getId(), saved.getSeq());

            return new MessageSendResponse(true, conversationId, messageView);
        } finally {
            // 정상 접수면 락을 유지(AI 종결 시 해제), 예외로 미접수면 즉시 해제해 영구 잠금을 막는다.
            if (!accepted) {
                conversationLock.unlock(conversationId);
            }
        }
    }

    /**
     * AI 처리 결과(assistant 턴)를 확정 메시지로 남기고 대화방을 종결한다.
     * ChatProcessor가 tool 결과를 {@link AssistantMessageDraft}로 만들어 넘기면, 여기서
     * seq 발번 + 메시지 저장 + {@code message_new} + {@code session_status: idle} +
     * {@code session_list_update} 발행 + 대화방 락 해제를 한 트랜잭션으로 처리한다
     * (messaging.md 종결 보장: AI 응답 완료 message_new + idle 전파).
     *
     * <p><b>취소/중지 경쟁 방어(messaging.md "취소=전체 폐기"):</b> AI 처리는 비동기라, 처리 도중
     * 사용자가 [취소]/[중지]를 눌러 대화방이 이미 {@code IDLE}로 풀렸을 수 있다. 이때 지각 도착한 AI
     * 결과를 그대로 저장/발행하면 취소했는데도 응답이 뒤늦게 나타난다. 그래서 상태가 여전히
     * {@code AI_RESPONDING}일 때만 확정하고, 아니면 <b>결과를 버린다</b>(no-op, null 반환).
     * 삭제된 대화방({@code getActiveOrThrow} 404)도 마찬가지로 버린다.
     *
     * <p><b>락 소유권:</b> 락 해제는 "이 처리가 유효할 때"(AI_RESPONDING)만 수행한다. 취소가 이미
     * 락을 풀었거나(그 사이 다른 요청이 락을 잡았을 수 있음) 상태가 바뀐 경우엔 락을 건드리지 않아,
     * 지각 처리가 남의 락을 해제하는 것을 막는다.
     *
     * <p>발행은 커밋 후로 미뤄 확정 데이터로 내보낸다.
     *
     * <p><b>전파 = REQUIRES_NEW:</b> 이 메서드는 메시지 접수 트랜잭션의 {@code AFTER_COMMIT} 리스너
     * (ChatRequestedListener)에서 호출된다. 그 시점엔 접수 트랜잭션이 이미 커밋돼 종료 중이라, 기본 전파로는
     * 새 쓰기가 커밋되지 않고 StaleState가 발생한다. 따라서 독립 트랜잭션을 새로 열어 assistant 턴을
     * 확실히 커밋한다.
     *
     * @return 확정된 assistant 메시지. 취소/중지/삭제로 결과를 버렸으면 {@code null}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MessageResponse completeAssistantTurn(Long conversationId, AssistantMessageDraft draft) {
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElse(null);

        // 대화방이 사라졌거나(삭제) 이미 처리 중이 아니면(취소/중지로 idle 등) 지각 결과를 버린다.
        // 이 경우 락은 취소 경로가 이미 해제했으므로 여기서 건드리지 않는다(남의 락 해제 방지).
        if (conversation == null || conversation.getStatus() != ConversationStatus.AI_RESPONDING) {
            log.info("Assistant turn discarded (conversation not in AI_RESPONDING): conversationId={}, status={}",
                    conversationId, conversation == null ? "DELETED" : conversation.getStatus());
            return null;
        }

        try {
            Long ownerId = conversation.getUserId();

            long nextSeq = nextSeq(conversationId);
            Message message = new Message(conversationId, nextSeq,
                    MessageRole.ASSISTANT, draft.type(), MessageStatus.COMPLETED);
            message.setContent(draft.content());
            message.setMetadataJson(draft.metadataJson());
            Message saved = messageRepository.save(message);

            // 종결: ai_responding → idle 전이 + 목록 최신순 기준 갱신
            conversation.setStatus(ConversationStatus.IDLE);
            conversation.setLastMessageAt(saved.getCreatedAt());
            Conversation savedConversation = conversationRepository.save(conversation);

            MessageResponse messageView = toMessage(saved);
            publishAfterCommit(ownerId, SseEventType.MESSAGE_NEW, conversationId, messageView);
            publishAfterCommit(ownerId, SseEventType.SESSION_STATUS, conversationId,
                    SessionStatusPayload.of(conversationId, ConversationStatus.IDLE));
            publishAfterCommit(ownerId, SseEventType.SESSION_LIST_UPDATE, conversationId,
                    SessionListUpdatePayload.upsert(toListSnapshot(savedConversation)));

            log.info("Assistant turn completed: conversationId={}, messageId={}, type={}",
                    conversationId, saved.getId(), draft.type());
            return messageView;
        } finally {
            // 이 처리가 유효했을 때만(위에서 AI_RESPONDING 확인됨) 잡았던 락을 해제한다.
            conversationLock.unlock(conversationId);
        }
    }

    /**
     * 실행 진행 블록(PROGRESS) 메시지를 생성하고 {@code message_new} + {@code session_list_update}를
     * 발행한다(messaging.md 실행 SSE 흐름 — 실행 시작). {@code payloadJson}이 진실(kind/schemaVersion/
     * executionId/recipeName/status/steps)이고, {@code content}는 표시용 진행 요약(파생물)이다.
     *
     * <p>실행 오케스트레이션({@link ExecutionService})이 호출한다. 여기서는 <b>메시지 저장/발행만</b>
     * 담당하고 대화방 상태 전이(executing)와 락은 호출측이 관리한다. 호출측 트랜잭션에 참여해
     * execution 레코드와 원자적으로 커밋되도록 기본 전파(REQUIRED)다. 대화방이 없으면(삭제) no-op으로
     * {@code null}을 반환한다.
     *
     * @param conversationId 진행 블록을 남길 대화방
     * @param payloadJson    진행 payload (JSON 문자열, kind:"progress")
     * @param content        진행 요약 본문 (Markdown, 표시용)
     * @return 생성된 PROGRESS 메시지 ID (EXECUTION.MESSAGE_ID로 저장), 대화방 없으면 null
     */
    @Transactional
    public Long createProgressMessage(Long conversationId, String payloadJson, String content) {
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElse(null);
        if (conversation == null) {
            log.info("Progress message skipped (conversation missing): conversationId={}", conversationId);
            return null;
        }

        Long ownerId = conversation.getUserId();
        long nextSeq = nextSeq(conversationId);
        Message message = new Message(conversationId, nextSeq,
                MessageRole.ASSISTANT, MessageType.PROGRESS, MessageStatus.COMPLETED);
        message.setContent(content);
        message.setMetadataJson(payloadJson);
        Message saved = messageRepository.save(message);

        conversation.setLastMessageAt(saved.getCreatedAt());
        Conversation savedConversation = conversationRepository.save(conversation);

        MessageResponse messageView = toMessage(saved);
        publishAfterCommit(ownerId, SseEventType.MESSAGE_NEW, conversationId, messageView);
        publishAfterCommit(ownerId, SseEventType.SESSION_LIST_UPDATE, conversationId,
                SessionListUpdatePayload.upsert(toListSnapshot(savedConversation)));

        log.info("Progress message created: conversationId={}, messageId={}", conversationId, saved.getId());
        return saved.getId();
    }

    /**
     * 기존 진행 블록(PROGRESS) 메시지의 payload/content를 갱신하고 {@code message_update}를 발행한다
     * (messaging.md 실행 SSE 흐름 — 스텝 보고/완료). 같은 메시지를 갱신하므로 새 메시지를 쌓지 않는다.
     * payloadJson이 진실이고 content는 표시용 요약이다.
     *
     * <p>메시지가 없거나(삭제/미존재) PROGRESS 타입이 아니면 no-op. 호출측 트랜잭션에 참여한다(REQUIRED).
     *
     * @param conversationId 대화방 ID (발행 대상/소유자 도출)
     * @param messageId      갱신 대상 PROGRESS 메시지 ID
     * @param payloadJson    갱신된 진행 payload (JSON 문자열)
     * @param content        갱신된 진행 요약 본문 (Markdown)
     */
    @Transactional
    public void updateProgressMessage(Long conversationId, Long messageId, String payloadJson, String content) {
        if (messageId == null) {
            return;
        }
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null || message.getType() != MessageType.PROGRESS) {
            log.info("Progress message update skipped (missing or not PROGRESS): messageId={}", messageId);
            return;
        }
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElse(null);
        if (conversation == null) {
            return;
        }

        message.setContent(content);
        message.setMetadataJson(payloadJson);
        Message saved = messageRepository.save(message);

        Long ownerId = conversation.getUserId();
        MessageResponse messageView = toMessage(saved);
        publishAfterCommit(ownerId, SseEventType.MESSAGE_UPDATE, conversationId,
                MessageUpdatePayload.of(conversationId, messageView));

        log.info("Progress message updated: conversationId={}, messageId={}", conversationId, messageId);
    }

    /**
     * 실행 결과 블록(RESULT) 메시지를 생성하고 {@code message_new} + {@code session_list_update}를
     * 발행한다(messaging.md 실행 SSE 흐름 — 완료). PROGRESS와 별개의 MESSAGE다. {@code payloadJson}이
     * 진실(kind/schemaVersion/executionId/recipeName/resultValues/template?)이고 {@code content}는
     * 표시용 결과 요약(템플릿 치환 결과 등, 파생물)이다.
     *
     * <p>여기서는 <b>메시지 저장/발행만</b> 담당하고, 대화방 상태 전이(idle)와 락 해제는 호출측이 이어서
     * 수행한다(중복 종결 방지). 호출측 트랜잭션에 참여한다(REQUIRED). 대화방이 없거나 content가 비면 no-op.
     *
     * @param conversationId 결과를 남길 대화방
     * @param payloadJson    결과 payload (JSON 문자열, kind:"result")
     * @param content        결과 요약 본문 (Markdown, 표시용)
     */
    @Transactional
    public void createResultMessage(Long conversationId, String payloadJson, String content) {
        if (conversationId == null || content == null || content.isBlank()) {
            return;
        }
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElse(null);
        if (conversation == null) {
            log.info("Result message skipped (conversation missing): conversationId={}", conversationId);
            return;
        }

        Long ownerId = conversation.getUserId();
        long nextSeq = nextSeq(conversationId);
        Message message = new Message(conversationId, nextSeq,
                MessageRole.ASSISTANT, MessageType.RESULT, MessageStatus.COMPLETED);
        message.setContent(content);
        message.setMetadataJson(payloadJson);
        Message saved = messageRepository.save(message);

        conversation.setLastMessageAt(saved.getCreatedAt());
        Conversation savedConversation = conversationRepository.save(conversation);

        MessageResponse messageView = toMessage(saved);
        publishAfterCommit(ownerId, SseEventType.MESSAGE_NEW, conversationId, messageView);
        publishAfterCommit(ownerId, SseEventType.SESSION_LIST_UPDATE, conversationId,
                SessionListUpdatePayload.upsert(toListSnapshot(savedConversation)));

        log.info("Result message created: conversationId={}, messageId={}", conversationId, saved.getId());
    }

    /**
     * 대화방 처리 상태를 전이하고 {@code session_status} SSE를 발행한다(모든 탭 동기화).
     * 상태 변경이 실제로 있을 때만(같은 값이면 no-op) 저장/발행한다. 커밋 후 발행하여 확정 데이터로 내보낸다.
     *
     * @return 변경 후 대화방 상세
     */
    @Transactional
    public ConversationDetailResponse transitionStatus(Long conversationId, ConversationStatus status) {
        if (status == null) {
            throw ApiException.invalidRequest("status is required");
        }
        Conversation conversation = getActiveOrThrow(conversationId);
        if (conversation.getStatus() == status) {
            // 멱등: 같은 상태로의 전이는 발행 없이 그대로 반환
            return toDetail(conversation);
        }
        conversation.setStatus(status);
        Conversation saved = conversationRepository.save(conversation);

        publishAfterCommit(saved.getUserId(), SseEventType.SESSION_STATUS, saved.getId(),
                SessionStatusPayload.of(saved.getId(), status));

        log.info("Conversation status transitioned: conversationId={}, status={}", conversationId, status);
        return toDetail(saved);
    }

    /**
     * 액션 피커 [취소]. 대화방의 RUNNING 실행을 <b>CANCELLED</b>로 종료한 뒤, 대기/락을 해제하고 대화방을
     * IDLE로 되돌리며 "취소되었습니다" 시스템 메시지를 남긴다(messaging.md 상태 해제).
     * 이미 IDLE이면 <b>멱등 no-op</b>(에러 아님). 취소/중지는 반드시 API 경유이며 상태 해제는 모든 탭에 전파된다.
     *
     * <p>취소(CANCELLED)와 중지(STOPPED)는 상태로 구분해 히스토리에 남긴다("무슨 일이 있었나"의 기록).
     * 재개 로직의 세분은 재개 기능 도입 시 다룬다. execution 종료(상태 + 요약 + PROGRESS 메시지 확정)는
     * ExecutionService가, 대화방 상태/락/안내 메시지는 releaseToIdle이 담당한다.
     */
    @Transactional
    public ConversationDetailResponse cancel(Long conversationId, Long requesterId) {
        getOwnedOrThrow(conversationId, requesterId);
        executionService.terminateRunningForConversation(conversationId, ExecutionStatus.CANCELLED);
        return releaseToIdle(conversationId, "취소되었습니다.");
    }

    /**
     * 실행 [중지]. 대화방의 RUNNING 실행을 <b>STOPPED</b>로 종료(현재까지 진행분 보존)한 뒤,
     * 대화방을 IDLE로 되돌리고 락을 해제한다. 이미 IDLE이면 <b>멱등 no-op</b>.
     * execution 종료(EXECUTION 상태 + 요약 + PROGRESS 메시지 확정)는 ExecutionService가, 대화방
     * 상태/락/안내 메시지는 releaseToIdle이 담당한다. 취소(CANCELLED)와 구분해 히스토리에 남긴다.
     */
    @Transactional
    public ConversationDetailResponse stop(Long conversationId, Long requesterId) {
        // 존재/삭제/소유자 검증 (없거나 타인 소유면 404). execution 종료를 먼저 처리한 뒤 대화방을 해제한다.
        getOwnedOrThrow(conversationId, requesterId);
        executionService.terminateRunningForConversation(conversationId, ExecutionStatus.STOPPED);
        return releaseToIdle(conversationId, "실행이 중지되었습니다.");
    }

    /**
     * 대화방을 IDLE로 해제하는 공통 경로(취소/중지). 락 해제는 상태와 무관하게 항상 수행하고(멱등),
     * 상태가 이미 IDLE이면 안내 메시지/발행 없이 no-op 반환한다. 상태 변경이 있을 때만 IDLE 전이 +
     * 시스템 안내 메시지 저장 + SSE 발행(session_status idle, message_new, session_list_update)을 수행한다.
     */
    private ConversationDetailResponse releaseToIdle(Long conversationId, String systemNotice) {
        Conversation conversation = getActiveOrThrow(conversationId);

        // 락은 상태와 무관하게 항상 해제(인메모리 락이 남아있을 수 있음). unlock은 멱등.
        conversationLock.unlock(conversationId);

        if (conversation.getStatus() == ConversationStatus.IDLE) {
            // 멱등: 이미 유휴면 상태 발행/안내 메시지 없이 종료
            log.info("Conversation release is no-op (already idle): conversationId={}", conversationId);
            return toDetail(conversation);
        }

        Long ownerId = conversation.getUserId();
        conversation.setStatus(ConversationStatus.IDLE);

        // "취소/중지되었습니다" 시스템 안내 메시지 (ASSISTANT/SYSTEM). seq는 max+1.
        long nextSeq = nextSeq(conversationId);
        Message notice = new Message(conversationId, nextSeq,
                MessageRole.ASSISTANT, MessageType.SYSTEM, MessageStatus.COMPLETED);
        notice.setContent(systemNotice);
        Message savedNotice = messageRepository.save(notice);

        conversation.setLastMessageAt(savedNotice.getCreatedAt());
        Conversation saved = conversationRepository.save(conversation);

        MessageResponse noticeView = toMessage(savedNotice);
        // session_status: idle (모든 탭 입력 잠금 해제) + 안내 메시지(message_new) + 목록 한 줄 갱신
        publishAfterCommit(ownerId, SseEventType.SESSION_STATUS, conversationId,
                SessionStatusPayload.of(conversationId, ConversationStatus.IDLE));
        publishAfterCommit(ownerId, SseEventType.MESSAGE_NEW, conversationId, noticeView);
        publishAfterCommit(ownerId, SseEventType.SESSION_LIST_UPDATE, conversationId,
                SessionListUpdatePayload.upsert(toListSnapshot(saved)));

        log.info("Conversation released to idle: conversationId={}, noticeMessageId={}",
                conversationId, savedNotice.getId());
        return toDetail(saved);
    }

    /**
     * 서버 기동 복구: {@code AI_RESPONDING}/{@code EXECUTING}로 남은 미삭제 대화방을 IDLE로 정리한다.
     * 인메모리 락은 재시작 시 이미 사라졌으므로, 상태만 남아 영구 잠금처럼 보이는 것을 방지한다
     * (messaging.md 종결 보장). 상태 신호는 SSE로 발행하지 않는다(기동 시점엔 구독자가 없음).
     *
     * @return 복구한 대화방 수
     */
    @Transactional
    public int recoverInProgressConversations() {
        List<Conversation> stuck = conversationRepository.findByStatusInAndDeletedAtIsNull(
                List.of(ConversationStatus.AI_RESPONDING, ConversationStatus.EXECUTING));
        for (Conversation conversation : stuck) {
            conversation.setStatus(ConversationStatus.IDLE);
            conversationRepository.save(conversation);
        }
        if (!stuck.isEmpty()) {
            log.info("Recovered {} in-progress conversation(s) to IDLE on startup", stuck.size());
        }
        return stuck.size();
    }

    // ── helpers ──

    /** 미삭제 대화방 조회 (없거나 삭제 시 404). 내부 오케스트레이션 경로 전용(소유자 검증 없음). */
    private Conversation getActiveOrThrow(Long id) {
        return conversationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.conversationNotFound(id));
    }

    /**
     * 사용자 진입용 대화방 조회 (IDOR 방지). 존재/미삭제 확인 후 소유자({@code userId})가
     * 요청자와 다르면 <b>존재를 노출하지 않도록</b> 404({@code conversationNotFound})로 처리한다.
     * 시스템/오케스트레이션 내부 경로에는 쓰지 않는다({@link #getActiveOrThrow} 사용).
     */
    private Conversation getOwnedOrThrow(Long id, Long requesterId) {
        Conversation conversation = getActiveOrThrow(id);
        if (requesterId == null || !requesterId.equals(conversation.getUserId())) {
            throw ApiException.conversationNotFound(id);
        }
        return conversation;
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

    /** session_list_update 스냅샷 매핑 (목록 한 줄 전체) */
    private ConversationListSnapshot toListSnapshot(Conversation conversation) {
        return new ConversationListSnapshot(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getApiSpecId(),
                StatusView.of(conversation.getStatus()),
                conversation.getLastMessageAt(),
                isUnread(conversation),
                conversation.getUpdatedAt());
    }

    /**
     * 트랜잭션 커밋 후 SSE를 발행한다. 활성 트랜잭션이 있으면 afterCommit 콜백으로 미루고,
     * 없으면(예: 테스트에서 트랜잭션 밖 호출) 즉시 발행한다. 발행 자체가 best-effort라
     * publisher 내부에서 예외를 삼키므로, 커밋 성공에는 영향을 주지 않는다.
     */
    private void publishAfterCommit(Long userId, SseEventType type, Long sessionId, Object data) {
        if (userId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ssePublisher.toUser(userId, type, sessionId, data);
                }
            });
        } else {
            ssePublisher.toUser(userId, type, sessionId, data);
        }
    }
}
