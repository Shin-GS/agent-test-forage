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
import com.testforge.dto.conversation.PartDraft;
import com.testforge.dto.conversation.PartResponse;
import com.testforge.dto.conversation.SessionDeletedPayload;
import com.testforge.dto.conversation.SessionListUpdatePayload;
import com.testforge.dto.conversation.SessionStatusPayload;
import com.testforge.entity.conversation.Conversation;
import com.testforge.entity.conversation.Message;
import com.testforge.entity.conversation.MessagePart;
import com.testforge.entity.conversation.enums.ConversationStatus;
import com.testforge.entity.conversation.enums.MessageRole;
import com.testforge.entity.conversation.enums.MessageStatus;
import com.testforge.entity.conversation.enums.PartStatus;
import com.testforge.entity.conversation.enums.PartType;
import com.testforge.entity.execution.enums.ExecutionStatus;
import com.testforge.entity.spec.ApiSpec;
import com.testforge.lock.ConversationLock;
import com.testforge.repository.conversation.ConversationRepository;
import com.testforge.repository.conversation.MessagePartRepository;
import com.testforge.repository.conversation.MessageRepository;
import com.testforge.repository.spec.ApiSpecRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final MessagePartRepository messagePartRepository;
    private final SseEventPublisher ssePublisher;
    private final ConversationLock conversationLock;
    private final ApplicationEventPublisher eventPublisher;
    private final ExecutionService executionService;
    private final ApiSpecRepository apiSpecRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository,
                               MessagePartRepository messagePartRepository,
                               SseEventPublisher ssePublisher,
                               ConversationLock conversationLock,
                               ApplicationEventPublisher eventPublisher,
                               ExecutionService executionService,
                               ApiSpecRepository apiSpecRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.messagePartRepository = messagePartRepository;
        this.ssePublisher = ssePublisher;
        this.conversationLock = conversationLock;
        this.eventPublisher = eventPublisher;
        this.executionService = executionService;
        this.apiSpecRepository = apiSpecRepository;
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

        // 2) 첫 메시지 저장 = USER 턴 + TEXT 파트 1개
        Message savedMessage = saveUserTurn(savedConversation.getId(),
                request.content(), request.referenceId());

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
        List<Conversation> conversations = conversationRepository
                .findTop200ByUserIdAndDeletedAtIsNullOrderByLastMessageAtDesc(userId);
        // apiSpecId → 서비스 표시명 맵을 일괄 조회로 한 번에 만든다 (행별 개별 조회 N+1 방지).
        Map<Long, String> serviceNames = resolveServiceNames(conversations);
        return conversations.stream()
                .map(conversation -> toSummary(conversation, serviceNames))
                .toList();
    }

    /** 미삭제 대화방 상세. 없거나 삭제/타인 소유면 404. */
    @Transactional(readOnly = true)
    public ConversationDetailResponse detail(Long id, Long requesterId) {
        Conversation conversation = getOwnedOrThrow(id, requesterId);
        return toDetail(conversation);
    }

    /** 대화방 제목 최대 길이 (코드포인트 기준, 확정 규칙) */
    private static final int TITLE_MAX_LENGTH = 50;

    /**
     * 대화방 이름 변경. 없거나 삭제/타인 소유면 404. 제목 검증(확정 규칙):
     * <ol>
     *   <li>제어문자(\p{Cntrl}: 개행/탭 등) 제거 후 트림</li>
     *   <li>정제 후 빈 문자열이면 400(INVALID_REQUEST)</li>
     *   <li>길이 1~50자(코드포인트 기준) 초과면 400 (자르지 않고 명확히 거절)</li>
     * </ol>
     * 정제된 제목을 저장한다.
     */
    @Transactional
    public ConversationDetailResponse updateTitle(Long id, Long requesterId, String title) {
        if (title == null) {
            throw ApiException.invalidRequest("title is required");
        }
        // 제어문자 제거 후 트림 (개행/탭 등이 제목에 섞이는 것을 방지)
        String sanitized = title.replaceAll("\\p{Cntrl}", "").trim();
        if (sanitized.isEmpty()) {
            throw ApiException.invalidRequest("title is required");
        }
        // 길이는 코드포인트 기준으로 계산 (이모지/보조 평면 문자를 1자로 셈)
        int length = sanitized.codePointCount(0, sanitized.length());
        if (length > TITLE_MAX_LENGTH) {
            throw ApiException.invalidRequest(
                    "title must be at most " + TITLE_MAX_LENGTH + " characters");
        }
        Conversation conversation = getOwnedOrThrow(id, requesterId);
        conversation.setTitle(sanitized);
        Conversation saved = conversationRepository.save(conversation);

        // SSE: 목록 한 줄 갱신 (이름 변경 흡수)
        publishAfterCommit(saved.getUserId(), SseEventType.SESSION_LIST_UPDATE, saved.getId(),
                SessionListUpdatePayload.upsert(toListSnapshot(saved)));

        log.info("Conversation title updated: conversationId={}", id);
        return toDetail(saved);
    }

    /**
     * 대화방 대상 서비스(스펙) 변경. 없거나 삭제/타인 소유면 404(getOwnedOrThrow, IDOR 방지).
     *
     * <p>{@code apiSpecId}가 null이면 "미지정으로 되돌리기"(검증 없이 해제). null이 아니면 미삭제 스펙
     * 존재 여부를 검증하고, 없으면 400(INVALID_REQUEST "유효하지 않은 서비스입니다").
     * updateTitle과 동일하게 목록 한 줄(session_list_update)을 갱신해 모든 탭에 동기화한다.
     *
     * <p><b>촉발 카드 파트 소비(선택):</b> {@code triggerPartId}가 non-null이면 service_select 카드
     * 클릭으로 서비스를 설정한 경우다. 서비스 변경 후 그 파트를 CONSUMED로 전이해 새로고침 후 카드가
     * 재활성화되지 않게 한다({@link #consumeInteractivePart} 재사용). 파트가 없거나 인터랙티브 타입이
     * 아니면 no-op이 보장되므로 방어적으로 그대로 호출한다. null이면(기존 호출) 소비 처리 없이 동작한다.
     * 소비 처리는 그 턴의 {@code message_update} SSE를 발행하므로 FE 카드 비활성화가 함께 동기화된다.
     */
    @Transactional
    public ConversationDetailResponse updateService(Long id, Long requesterId, Long apiSpecId,
                                                    Long triggerPartId) {
        Conversation conversation = getOwnedOrThrow(id, requesterId);

        // 지정 시에만 존재/유효성 검증. null이면 미지정으로 되돌리기(검증 없음).
        if (apiSpecId != null
                && apiSpecRepository.findByIdAndDeletedAtIsNull(apiSpecId).isEmpty()) {
            throw ApiException.invalidRequest("유효하지 않은 서비스입니다");
        }

        // 실제로 값이 바뀔 때만 SYSTEM 안내를 남긴다(같은 값 재설정 시 알림 중복/폭주 방지 — 멱등).
        boolean changed = !java.util.Objects.equals(conversation.getApiSpecId(), apiSpecId);
        conversation.setApiSpecId(apiSpecId);

        Long ownerId = conversation.getUserId();
        MessageResponse noticeView = null;
        if (changed) {
            // 서비스 설정/해제를 대화방에 SYSTEM 안내로 남긴다(사용자 인지 + 새로고침/다른 탭 복원).
            // 카드 선택이든 패널 드롭다운이든 이 API를 거치므로 경로 무관하게 일관되게 남는다.
            String serviceName = serviceNameOf(apiSpecId);
            String notice = apiSpecId == null
                    ? "대상 서비스 설정이 해제되었어요."
                    : "대상 서비스가 '" + serviceName + "'(으)로 설정되었어요.";
            Message savedNotice = saveTurn(id, MessageRole.SYSTEM, MessageStatus.COMPLETE,
                    List.of(PartDraft.text(notice)));
            conversation.setLastMessageAt(savedNotice.getCreatedAt());
            noticeView = toMessage(savedNotice);
        }

        Conversation saved = conversationRepository.save(conversation);

        // SSE: (변경 시) 안내 메시지(message_new) + 목록 한 줄 갱신(서비스 배지/표시명·lastMessageAt 동기화)
        if (noticeView != null) {
            publishAfterCommit(ownerId, SseEventType.MESSAGE_NEW, saved.getId(), noticeView);
        }
        publishAfterCommit(ownerId, SseEventType.SESSION_LIST_UPDATE, saved.getId(),
                SessionListUpdatePayload.upsert(toListSnapshot(saved)));

        // 촉발 카드 파트가 있으면 CONSUMED로 전이(재활성화 방지). 없거나 타입 불일치면 no-op.
        // (값이 안 바뀌었어도 카드를 눌렀으면 소진 처리는 해야 하므로 changed와 무관하게 수행.)
        if (triggerPartId != null) {
            consumeInteractivePart(id, triggerPartId);
        }

        log.info("Conversation service updated: conversationId={}, apiSpecId={}, changed={}, triggerPartId={}",
                id, apiSpecId, changed, triggerPartId);
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

        // SSE: 삭제 이벤트 (모든 탭). 보고 있던 탭은 홈으로 이탈 + 안내, 목록은 재조회로 제거된다.
        // (목록 갱신 session_list_update와 분리한 별도 SIGNAL — messaging.md)
        publishAfterCommit(conversation.getUserId(), SseEventType.SESSION_DELETED, id,
                SessionDeletedPayload.of(id));

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
        Long cursorId = decodeIdCursor(cursor);

        // hasNext(더 과거 존재) 판정을 위해 limit+1건 조회 (턴 단위 페이징 — 파트는 턴에 종속)
        List<Message> rows = messageRepository.findByConversationIdByCursor(
                conversationId, cursorId, org.springframework.data.domain.PageRequest.of(0, limit + 1));

        boolean hasNext = rows.size() > limit;
        List<Message> pageRows = hasNext ? rows.subList(0, limit) : rows;
        List<MessageResponse> items = toMessages(pageRows);
        if (!hasNext) {
            return CursorPage.last(items);
        }
        // 다음 커서 = 이번 페이지에서 가장 과거(가장 작은 id) = 최신순 목록의 마지막 항목
        Message oldest = pageRows.get(pageRows.size() - 1);
        return CursorPage.of(items, String.valueOf(oldest.getId()));
    }

    /** 메시지 페이지 크기 정규화 (기본 20, 최대 50) */
    private int normalizeMessageSize(Integer size) {
        if (size == null || size <= 0) {
            return 20;
        }
        return Math.min(size, 50);
    }

    /** id 커서 디코딩. null/빈/형식 불량이면 첫 페이지(null) */
    private Long decodeIdCursor(String cursor) {
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

            Message saved = saveUserTurn(conversationId, request.content(), request.referenceId());

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

            log.info("Message accepted: conversationId={}, messageId={}",
                    conversationId, saved.getId());

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

            // 턴(COMPLETE) + draft의 파트들을 저장
            Message saved = saveTurn(conversationId, draft.role(), MessageStatus.COMPLETE, draft.parts());

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

            log.info("Assistant turn completed: conversationId={}, messageId={}, role={}",
                    conversationId, saved.getId(), draft.role());
            return messageView;
        } finally {
            // 이 처리가 유효했을 때만(위에서 AI_RESPONDING 확인됨) 잡았던 락을 해제한다.
            conversationLock.unlock(conversationId);
        }
    }

    /**
     * 정보 조회(investigate) 최종 답변을 <b>진행(INVESTIGATE) 파트와 같은 턴</b>에 append하고 대화방을
     * 종결한다(messaging.md: 조회 답변 = [INVESTIGATE, TEXT(+REFERENCES)] 한 턴). {@link
     * #completeAssistantTurn}과 종결 규칙(취소 경쟁 방어 + 락 소유권 + REQUIRES_NEW)은 동일하되, <b>새 턴을
     * 만들지 않고</b> 조회 진행 파트가 속한 턴에 draft의 파트(TEXT/REFERENCES)를 이어 붙인다는 점만 다르다.
     *
     * <p>연결 고리는 {@code investigatePartId}(INVESTIGATION.TRIGGER_PART_ID = INVESTIGATE 파트 ID)다. 이
     * 파트가 속한 턴({@code messageId})을 조회해 그 턴에 답변 파트를 append한다. 진행 파트가 없거나(삭제)
     * INVESTIGATE 타입이 아니거나 {@code investigatePartId}가 null이면 같은 턴을 특정할 수 없으므로,
     * {@link #completeAssistantTurn}과 동일하게 <b>새 턴</b>에 답변을 남기는 폴백으로 처리한다(답변 유실 방지).
     *
     * <p><b>취소/중지 경쟁 방어:</b> {@link #completeAssistantTurn}과 동일하게 대화방이 여전히
     * {@code AI_RESPONDING}일 때만 확정하고, 아니면(취소/중지로 IDLE, 삭제) 결과를 버린다(null 반환, 락 미접촉).
     * 정상 확정 시 idle 전이 + 락 해제 + {@code session_status: idle}/{@code message_update}(같은 턴 append이므로)
     * /{@code session_list_update}를 발행한다.
     *
     * @param conversationId    답변을 남길 대화방
     * @param investigatePartId 같은 턴에 append할 기준이 되는 INVESTIGATE 파트 ID (없으면 새 턴 폴백)
     * @param draft             답변 초안(TEXT + 선택 REFERENCES)
     * @return 확정된 턴 뷰. 취소/중지/삭제로 결과를 버렸으면 {@code null}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MessageResponse completeInvestigateTurn(Long conversationId, Long investigatePartId,
                                                   AssistantMessageDraft draft) {
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElse(null);

        // completeAssistantTurn과 동일한 지각 결과 폐기 규칙(취소/중지/삭제).
        if (conversation == null || conversation.getStatus() != ConversationStatus.AI_RESPONDING) {
            log.info("Investigate turn discarded (conversation not in AI_RESPONDING): conversationId={}, status={}",
                    conversationId, conversation == null ? "DELETED" : conversation.getStatus());
            return null;
        }

        try {
            Long ownerId = conversation.getUserId();

            // 같은 턴 append: INVESTIGATE 진행 파트가 속한 턴을 찾아 draft 파트들을 이어 붙인다.
            Message turn = resolveTurnByPart(investigatePartId, PartType.INVESTIGATE);
            MessageResponse messageView;
            SseEventType chatEvent;
            if (turn != null) {
                appendParts(turn, draft.parts());
                turn.setStatus(MessageStatus.COMPLETE);
                messageRepository.save(turn);
                messageView = toMessage(turn);
                // 같은 턴에 파트를 이어 붙였으므로 message_update(그 턴의 파트 배열 전체 스냅샷).
                chatEvent = SseEventType.MESSAGE_UPDATE;
            } else {
                // 진행 파트 미존재/타입 불일치/미전달 → 같은 턴을 특정할 수 없어 새 턴 폴백(답변 유실 방지).
                Message saved = saveTurn(conversationId, draft.role(), MessageStatus.COMPLETE, draft.parts());
                messageView = toMessage(saved);
                chatEvent = SseEventType.MESSAGE_NEW;
            }

            // 종결: ai_responding → idle 전이 + 목록 최신순 기준 갱신
            conversation.setStatus(ConversationStatus.IDLE);
            conversation.setLastMessageAt(LocalDateTime.now());
            Conversation savedConversation = conversationRepository.save(conversation);

            publishAfterCommit(ownerId, chatEvent, conversationId,
                    chatEvent == SseEventType.MESSAGE_UPDATE
                            ? MessageUpdatePayload.of(conversationId, messageView)
                            : messageView);
            publishAfterCommit(ownerId, SseEventType.SESSION_STATUS, conversationId,
                    SessionStatusPayload.of(conversationId, ConversationStatus.IDLE));
            publishAfterCommit(ownerId, SseEventType.SESSION_LIST_UPDATE, conversationId,
                    SessionListUpdatePayload.upsert(toListSnapshot(savedConversation)));

            log.info("Investigate turn completed: conversationId={}, messageId={}, appended={}",
                    conversationId, messageView.id(), turn != null);
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
     * <p>새 ASSISTANT 턴(COMPLETE)에 PROGRESS 파트 1개를 append하고 그 <b>파트 ID</b>를 반환한다.
     * 반환한 파트 ID는 EXECUTION.TRIGGER_PART_ID로 저장되고 이후 갱신 대상으로 쓰인다. PROGRESS 파트는
     * {@code executionId}로 실행을 가리킨다(렌더 정참조).
     *
     * @param conversationId 진행 블록을 남길 대화방
     * @param executionId    이 진행 블록이 가리키는 실행 ID
     * @param payloadJson    진행 payload (JSON 문자열, kind:"progress")
     * @param content        진행 요약 본문 (Markdown, 표시용)
     * @return 생성된 PROGRESS 파트 ID (EXECUTION.TRIGGER_PART_ID로 저장), 대화방 없으면 null
     */
    @Transactional
    public Long createProgressMessage(Long conversationId, Long executionId,
                                      String payloadJson, String content) {
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElse(null);
        if (conversation == null) {
            log.info("Progress message skipped (conversation missing): conversationId={}", conversationId);
            return null;
        }

        Long ownerId = conversation.getUserId();
        Message turn = new Message(conversationId, MessageRole.ASSISTANT, MessageStatus.COMPLETE);
        Message savedTurn = messageRepository.save(turn);

        MessagePart part = new MessagePart(savedTurn.getId(), PartType.PROGRESS, PartStatus.COMPLETE);
        part.setExecutionId(executionId);
        part.setPayloadJson(payloadJson);
        part.setContent(content);
        part.setSchemaVersion(2);
        MessagePart savedPart = messagePartRepository.save(part);
        savedTurn.setContentPreview(preview(content));
        messageRepository.save(savedTurn);

        conversation.setLastMessageAt(savedTurn.getCreatedAt());
        Conversation savedConversation = conversationRepository.save(conversation);

        MessageResponse messageView = toMessage(savedTurn);
        publishAfterCommit(ownerId, SseEventType.MESSAGE_NEW, conversationId, messageView);
        publishAfterCommit(ownerId, SseEventType.SESSION_LIST_UPDATE, conversationId,
                SessionListUpdatePayload.upsert(toListSnapshot(savedConversation)));

        log.info("Progress part created: conversationId={}, messageId={}, partId={}",
                conversationId, savedTurn.getId(), savedPart.getId());
        return savedPart.getId();
    }

    /**
     * 기존 진행 블록(PROGRESS) 메시지의 payload/content를 갱신하고 {@code message_update}를 발행한다
     * (messaging.md 실행 SSE 흐름 — 스텝 보고/완료). 같은 메시지를 갱신하므로 새 메시지를 쌓지 않는다.
     * payloadJson이 진실이고 content는 표시용 요약이다.
     *
     * <p>파트가 없거나(삭제/미존재) PROGRESS 타입이 아니면 no-op. 갱신 payload는 그 턴의 파트 배열 전체
     * 스냅샷이다(멱등). 호출측 트랜잭션에 참여한다(REQUIRED).
     *
     * @param conversationId 대화방 ID (발행 대상/소유자 도출)
     * @param partId         갱신 대상 PROGRESS 파트 ID
     * @param payloadJson    갱신된 진행 payload (JSON 문자열)
     * @param content        갱신된 진행 요약 본문 (Markdown)
     */
    @Transactional
    public void updateProgressMessage(Long conversationId, Long partId, String payloadJson, String content) {
        updatePart(conversationId, partId, PartType.PROGRESS, payloadJson, content, null);
    }

    /**
     * 실행 결과 블록(RESULT) 파트를 <b>진행(PROGRESS) 파트와 같은 턴</b>에 append하고 {@code message_update}
     * (그 턴의 파트 배열 전체 스냅샷)를 발행한다(messaging.md: 한 턴 = [TEXT, PROGRESS, RESULT, ...]).
     * PROGRESS와 RESULT가 같은 실행({@code executionId})을 공유하며 한 턴 안에 순서대로 쌓인다. {@code
     * payloadJson}이 진실(kind/schemaVersion/executionId/recipeName/resultValues/template?)이고 {@code
     * content}는 표시용 결과 요약(파생물)이다.
     *
     * <p>연결 고리는 {@code progressPartId}(EXECUTION.TRIGGER_PART_ID = PROGRESS 파트 ID)다. 이 파트가
     * 속한 턴({@code messageId})을 조회해 그 턴에 RESULT 파트를 append한다. 진행 파트가 없거나(삭제)
     * PROGRESS 타입이 아니거나 {@code progressPartId}가 null이면, 같은 턴을 찾을 수 없으므로 <b>새 턴</b>에
     * RESULT 파트를 남기는 폴백({@link #createResultMessageInNewTurn})으로 처리한다(결과 유실 방지).
     *
     * <p>여기서는 <b>메시지 저장/발행만</b> 담당하고, 대화방 상태 전이(idle)와 락 해제는 호출측이 이어서
     * 수행한다(중복 종결 방지). 호출측 트랜잭션에 참여한다(REQUIRED). 대화방이 없거나 content가 비면 no-op.
     *
     * @param conversationId 결과를 남길 대화방
     * @param executionId    이 결과 블록이 가리키는 실행 ID
     * @param progressPartId 같은 턴에 append할 기준이 되는 PROGRESS 파트 ID (없으면 새 턴 폴백)
     * @param payloadJson    결과 payload (JSON 문자열, kind:"result")
     * @param content        결과 요약 본문 (Markdown, 표시용)
     */
    @Transactional
    public void appendResultToTurn(Long conversationId, Long executionId, Long progressPartId,
                                   String payloadJson, String content) {
        if (conversationId == null || content == null || content.isBlank()) {
            return;
        }
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElse(null);
        if (conversation == null) {
            log.info("Result part skipped (conversation missing): conversationId={}", conversationId);
            return;
        }

        // 같은 턴을 찾는 연결 고리: PROGRESS 파트 → 그 파트가 속한 턴(messageId).
        Message turn = resolveTurnByPart(progressPartId, PartType.PROGRESS);
        if (turn == null) {
            // 진행 파트가 없거나 타입 불일치/미전달 → 같은 턴을 특정할 수 없으므로 새 턴 폴백(결과 유실 방지).
            createResultMessageInNewTurn(conversation, executionId, payloadJson, content);
            return;
        }

        // 같은 턴에 RESULT 파트 append
        MessagePart part = new MessagePart(turn.getId(), PartType.RESULT, PartStatus.COMPLETE);
        part.setExecutionId(executionId);
        part.setPayloadJson(payloadJson);
        part.setContent(content);
        part.setSchemaVersion(2);
        messagePartRepository.save(part);
        // 미리보기는 결과 요약으로 갱신(가장 최신 표시 텍스트)
        turn.setContentPreview(preview(content));
        messageRepository.save(turn);

        conversation.setLastMessageAt(turn.getCreatedAt());
        Conversation savedConversation = conversationRepository.save(conversation);

        // 파트 append는 message_update(그 턴의 파트 배열 전체 스냅샷). message_new가 아니다(턴은 그대로).
        Long ownerId = conversation.getUserId();
        MessageResponse messageView = toMessage(turn);
        publishAfterCommit(ownerId, SseEventType.MESSAGE_UPDATE, conversationId,
                MessageUpdatePayload.of(conversationId, messageView));
        publishAfterCommit(ownerId, SseEventType.SESSION_LIST_UPDATE, conversationId,
                SessionListUpdatePayload.upsert(toListSnapshot(savedConversation)));

        log.info("Result part appended to turn: conversationId={}, messageId={}, progressPartId={}",
                conversationId, turn.getId(), progressPartId);
    }

    /**
     * 같은 턴을 찾지 못했을 때의 폴백: RESULT 파트를 새 ASSISTANT 턴으로 남기고 {@code message_new}를
     * 발행한다. 진행 파트가 삭제됐거나 progressPartId가 없는 예외 상황에서만 쓰인다(정상 경로는 append).
     */
    private void createResultMessageInNewTurn(Conversation conversation, Long executionId,
                                              String payloadJson, String content) {
        Long conversationId = conversation.getId();
        Long ownerId = conversation.getUserId();
        Message turn = new Message(conversationId, MessageRole.ASSISTANT, MessageStatus.COMPLETE);
        Message savedTurn = messageRepository.save(turn);

        MessagePart part = new MessagePart(savedTurn.getId(), PartType.RESULT, PartStatus.COMPLETE);
        part.setExecutionId(executionId);
        part.setPayloadJson(payloadJson);
        part.setContent(content);
        part.setSchemaVersion(2);
        messagePartRepository.save(part);
        savedTurn.setContentPreview(preview(content));
        messageRepository.save(savedTurn);

        conversation.setLastMessageAt(savedTurn.getCreatedAt());
        Conversation savedConversation = conversationRepository.save(conversation);

        MessageResponse messageView = toMessage(savedTurn);
        publishAfterCommit(ownerId, SseEventType.MESSAGE_NEW, conversationId, messageView);
        publishAfterCommit(ownerId, SseEventType.SESSION_LIST_UPDATE, conversationId,
                SessionListUpdatePayload.upsert(toListSnapshot(savedConversation)));

        log.info("Result part created in new turn (fallback): conversationId={}, messageId={}",
                conversationId, savedTurn.getId());
    }

    /**
     * 정보 조회(investigate) 진행 블록(INVESTIGATE_PROGRESS) 메시지를 생성하고 {@code message_new} +
     * {@code session_list_update}를 발행한다(investigation.md 진행 상태 표시). 레시피 실행 PROGRESS와
     * 구분되는 타입이며, investigate는 실행이 아니므로 executionId가 없다.
     *
     * <p>여기서는 <b>메시지 저장/발행만</b> 담당한다. 대화방 상태 전이(ai_responding 유지)와 락은
     * 호출측(InvestigateLoop/접수 흐름)이 관리한다. 호출측 트랜잭션에 참여한다(REQUIRED).
     * 대화방이 없으면(삭제) no-op으로 {@code null}을 반환한다.
     *
     * <p>새 ASSISTANT 턴(COMPLETE)에 INVESTIGATE 파트 1개를 append하고 그 <b>파트 ID</b>를 반환한다.
     * 파트는 {@code investigationId}로 조회를 가리킨다(렌더 정참조). investigate는 실행이 아니므로
     * {@code executionId}는 없다.
     *
     * @param conversationId  진행 블록을 남길 대화방
     * @param investigationId 이 진행 블록이 가리키는 조회 ID
     * @param payloadJson     진행 payload (JSON 문자열, kind:"investigate_progress")
     * @param content         진행 요약 본문 (Markdown, 표시용)
     * @return 생성된 INVESTIGATE 파트 ID, 대화방 없으면 null
     */
    @Transactional
    public Long createInvestigateProgressMessage(Long conversationId, Long investigationId,
                                                 String payloadJson, String content) {
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElse(null);
        if (conversation == null) {
            log.info("Investigate progress message skipped (conversation missing): conversationId={}",
                    conversationId);
            return null;
        }

        Long ownerId = conversation.getUserId();
        Message turn = new Message(conversationId, MessageRole.ASSISTANT, MessageStatus.COMPLETE);
        Message savedTurn = messageRepository.save(turn);

        MessagePart part = new MessagePart(savedTurn.getId(), PartType.INVESTIGATE, PartStatus.COMPLETE);
        part.setInvestigationId(investigationId);
        part.setPayloadJson(payloadJson);
        part.setContent(content);
        part.setSchemaVersion(1);
        MessagePart savedPart = messagePartRepository.save(part);
        savedTurn.setContentPreview(preview(content));
        messageRepository.save(savedTurn);

        conversation.setLastMessageAt(savedTurn.getCreatedAt());
        Conversation savedConversation = conversationRepository.save(conversation);

        MessageResponse messageView = toMessage(savedTurn);
        publishAfterCommit(ownerId, SseEventType.MESSAGE_NEW, conversationId, messageView);
        publishAfterCommit(ownerId, SseEventType.SESSION_LIST_UPDATE, conversationId,
                SessionListUpdatePayload.upsert(toListSnapshot(savedConversation)));

        log.info("Investigate progress part created: conversationId={}, messageId={}, partId={}",
                conversationId, savedTurn.getId(), savedPart.getId());
        return savedPart.getId();
    }

    /**
     * 기존 INVESTIGATE_PROGRESS 진행 블록의 payload/content를 갱신하고 {@code message_update}를 발행한다
     * (investigation.md 진행 상태 표시 — 조회 단계 갱신, 종결 확정). 같은 메시지를 갱신하므로 새 메시지를
     * 쌓지 않는다. 메시지가 없거나 INVESTIGATE_PROGRESS 타입이 아니면 no-op. 호출측 트랜잭션에 참여한다.
     *
     * @param conversationId 대화방 ID (발행 대상/소유자 도출)
     * @param partId         갱신 대상 INVESTIGATE 파트 ID
     * @param payloadJson    갱신된 진행 payload (JSON 문자열)
     * @param content        갱신된 진행 요약 본문 (Markdown)
     */
    @Transactional
    public void updateInvestigateProgressMessage(Long conversationId, Long partId,
                                                 String payloadJson, String content) {
        updatePart(conversationId, partId, PartType.INVESTIGATE, payloadJson, content, null);
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

        // "취소/중지되었습니다" 시스템 안내 = SYSTEM 턴 + TEXT 파트 1개 (messaging.md).
        Message savedNotice = saveTurn(conversationId, MessageRole.SYSTEM, MessageStatus.COMPLETE,
                List.of(PartDraft.text(systemNotice)));

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

    /** 사용자 발화 턴 저장 = USER 턴(COMPLETE) + TEXT 파트 1개. referenceId는 턴에 둔다. */
    private Message saveUserTurn(Long conversationId, String content, String referenceId) {
        Message turn = new Message(conversationId, MessageRole.USER, MessageStatus.COMPLETE);
        turn.setReferenceId(referenceId);
        Message savedTurn = messageRepository.save(turn);

        MessagePart part = new MessagePart(savedTurn.getId(), PartType.TEXT, PartStatus.COMPLETE);
        part.setContent(content);
        part.setSchemaVersion(1);
        messagePartRepository.save(part);

        savedTurn.setContentPreview(preview(content));
        return messageRepository.save(savedTurn);
    }

    /**
     * 턴(MESSAGE)을 저장하고 draft의 파트들을 순서대로 append한다. 턴 미리보기(contentPreview)는 첫 TEXT
     * 파트에서 파생한다. referenceId 등 턴 고유 필드가 필요하면 호출 후 별도 세팅한다.
     */
    private Message saveTurn(Long conversationId, MessageRole role, MessageStatus status,
                             List<PartDraft> parts) {
        Message turn = new Message(conversationId, role, status);
        Message savedTurn = messageRepository.save(turn);

        String previewSource = null;
        for (PartDraft draft : parts) {
            MessagePart part = new MessagePart(savedTurn.getId(), draft.type(), draft.status());
            part.setContent(draft.content());
            part.setPayloadJson(draft.payloadJson());
            part.setExecutionId(draft.executionId());
            part.setInvestigationId(draft.investigationId());
            part.setCardType(resolveCardType(draft));
            part.setSchemaVersion(draft.schemaVersion());
            messagePartRepository.save(part);
            if (previewSource == null && draft.content() != null && !draft.content().isBlank()) {
                previewSource = draft.content();
            }
        }
        savedTurn.setContentPreview(preview(previewSource));
        return messageRepository.save(savedTurn);
    }

    /**
     * 파트 ID로 그 파트가 속한 턴(MESSAGE)을 찾는다. 같은 턴에 후속 파트를 append하기 위한 연결 고리다
     * (실행 RESULT를 PROGRESS와 같은 턴에, investigate 답변을 진행 파트와 같은 턴에 붙일 때 사용).
     * partId가 null이거나, 파트가 없거나(삭제), 기대 타입이 아니거나, 소속 턴이 없으면 null을 반환한다
     * (호출측이 새 턴 폴백으로 처리).
     */
    private Message resolveTurnByPart(Long partId, PartType expectedType) {
        if (partId == null) {
            return null;
        }
        MessagePart part = messagePartRepository.findById(partId).orElse(null);
        if (part == null || (expectedType != null && part.getType() != expectedType)) {
            return null;
        }
        return messageRepository.findById(part.getMessageId()).orElse(null);
    }

    /** 이미 존재하는 턴에 draft 파트들을 순서대로 append한다(신규 파트는 턴 내 ID 오름차순 = 표시순 뒤에 붙음). */
    private void appendParts(Message turn, List<PartDraft> parts) {
        for (PartDraft draft : parts) {
            MessagePart part = new MessagePart(turn.getId(), draft.type(), draft.status());
            part.setContent(draft.content());
            part.setPayloadJson(draft.payloadJson());
            part.setExecutionId(draft.executionId());
            part.setInvestigationId(draft.investigationId());
            part.setCardType(resolveCardType(draft));
            part.setSchemaVersion(draft.schemaVersion());
            messagePartRepository.save(part);
            // 미리보기는 append된 TEXT 파트 본문으로 갱신(답변 텍스트가 가장 최신 표시 텍스트).
            if (draft.content() != null && !draft.content().isBlank()) {
                turn.setContentPreview(preview(draft.content()));
            }
        }
    }

    /**
     * 기존 파트를 갱신하고 {@code message_update}(그 턴의 파트 배열 전체 스냅샷)를 발행한다. 파트가 없거나
     * 기대 타입이 아니면 no-op. status가 지정되면 파트 상태도 전이한다(인터랙티브 파트 CONSUMED 등).
     */
    private void updatePart(Long conversationId, Long partId, PartType expectedType,
                            String payloadJson, String content, PartStatus status) {
        if (partId == null) {
            return;
        }
        MessagePart part = messagePartRepository.findById(partId).orElse(null);
        if (part == null || (expectedType != null && part.getType() != expectedType)) {
            log.info("Part update skipped (missing or wrong type): partId={}, expected={}", partId, expectedType);
            return;
        }
        Conversation conversation = conversationRepository.findByIdAndDeletedAtIsNull(conversationId)
                .orElse(null);
        if (conversation == null) {
            return;
        }

        if (payloadJson != null) {
            part.setPayloadJson(payloadJson);
        }
        if (content != null) {
            part.setContent(content);
        }
        if (status != null) {
            part.setStatus(status);
        }
        messagePartRepository.save(part);

        Message turn = messageRepository.findById(part.getMessageId()).orElse(null);
        if (turn == null) {
            return;
        }

        Long ownerId = conversation.getUserId();
        MessageResponse messageView = toMessage(turn);
        publishAfterCommit(ownerId, SseEventType.MESSAGE_UPDATE, conversationId,
                MessageUpdatePayload.of(conversationId, messageView));

        log.info("Part updated: conversationId={}, partId={}, type={}", conversationId, partId, part.getType());
    }

    /**
     * 인터랙티브 파트(CARD/ACTION_PICKER)를 소비(CONSUMED)로 전이하고 {@code message_update}를 발행한다.
     * 버튼/피커 응답 처리 시 호출한다(messaging.md 인터랙티브 파트 CONSUMED). 파트가 없으면 no-op.
     *
     * @param conversationId 대화방 ID
     * @param partId         소비 처리할 인터랙티브 파트 ID (없으면 no-op)
     */
    @Transactional
    public void consumeInteractivePart(Long conversationId, Long partId) {
        if (partId == null || conversationId == null) {
            return;
        }
        updatePart(conversationId, partId, null, null, null, PartStatus.CONSUMED);
    }

    /** contentPreview 파생(최대 500자, 앞부분 절단). null/빈이면 null. */
    private String preview(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.strip();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    /** CARD 파트의 cardType 결정: draft에 명시가 있으면 그대로, 없으면 payloadJson에서 파싱. */
    @SuppressWarnings("unchecked")
    private String resolveCardType(PartDraft draft) {
        if (draft.cardType() != null) {
            return draft.cardType();
        }
        if (draft.type() != PartType.CARD || draft.payloadJson() == null) {
            return null;
        }
        Object parsed = RecipeJsonUtil.toObject(draft.payloadJson());
        if (parsed instanceof Map<?, ?> map) {
            Object cardType = ((Map<String, Object>) map).get("cardType");
            return cardType == null ? null : cardType.toString();
        }
        return null;
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

    /** 목록 행 매핑. serviceName은 일괄 조회한 표시명 맵에서 채운다(apiSpecId null이면 null). */
    private ConversationSummaryResponse toSummary(Conversation conversation, Map<Long, String> serviceNames) {
        Long apiSpecId = conversation.getApiSpecId();
        String serviceName = apiSpecId == null ? null : serviceNames.get(apiSpecId);
        return new ConversationSummaryResponse(
                conversation.getId(),
                conversation.getTitle(),
                apiSpecId,
                serviceName,
                StatusView.of(conversation.getStatus()),
                conversation.getLastMessageAt(),
                conversation.getLastReadAt(),
                isUnread(conversation),
                conversation.getCreatedAt());
    }

    /**
     * 대화방들의 apiSpecId를 모아 ApiSpec을 일괄 조회하고, id → 표시명 맵을 만든다(N+1 방지).
     * 표시명 우선순위: serviceDescription(있으면) > name. apiSpecId가 null인 대화방은 제외.
     * (ExecutionService.resolveServiceNames와 동형 패턴.)
     */
    private Map<Long, String> resolveServiceNames(List<Conversation> conversations) {
        Set<Long> specIds = conversations.stream()
                .map(Conversation::getApiSpecId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (specIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (ApiSpec spec : apiSpecRepository.findByIdIn(specIds)) {
            result.put(spec.getId(), displayName(spec));
        }
        return result;
    }

    /**
     * 단건 apiSpecId → 서비스 표시명. SSE 실시간 스냅샷(toListSnapshot)처럼 대화 1건 이벤트에서 쓴다
     * (N+1 우려 없음). apiSpecId가 null이거나 스펙이 없으면 null.
     */
    private String serviceNameOf(Long apiSpecId) {
        if (apiSpecId == null) {
            return null;
        }
        return apiSpecRepository.findByIdAndDeletedAtIsNull(apiSpecId)
                .map(this::displayName)
                .orElse(null);
    }

    /** 사람이 읽는 서비스 표시명: serviceDescription > name (ExecutionService.displayName과 동일). */
    private String displayName(ApiSpec spec) {
        String description = spec.getServiceDescription();
        if (description != null && !description.isBlank()) {
            return description;
        }
        return spec.getName();
    }

    /** 상세 매핑 */
    private ConversationDetailResponse toDetail(Conversation conversation) {
        return new ConversationDetailResponse(
                conversation.getId(),
                conversation.getUserId(),
                conversation.getTitle(),
                conversation.getApiSpecId(),
                serviceNameOf(conversation.getApiSpecId()),
                StatusView.of(conversation.getStatus()),
                conversation.getLastMessageAt(),
                conversation.getLastReadAt(),
                isUnread(conversation),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }

    /** 턴 매핑 (파트 배열 포함). 단건 조회이므로 파트를 개별 로드한다(발행 경로). */
    private MessageResponse toMessage(Message message) {
        List<PartResponse> parts = messagePartRepository.findByMessageIdOrderByIdAsc(message.getId())
                .stream().map(this::toPart).toList();
        return new MessageResponse(
                message.getId(),
                message.getConversationId(),
                StatusView.of(message.getRole()),
                StatusView.of(message.getStatus()),
                message.getReferenceId(),
                message.getClientMessageId(),
                message.getCreatedAt(),
                parts);
    }

    /**
     * 턴 목록 매핑 (커서 페이지). 파트를 턴 id들로 일괄 로드해 N+1을 방지한다.
     * 입력 순서(최신순)를 유지한다.
     */
    private List<MessageResponse> toMessages(List<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Long> messageIds = messages.stream().map(Message::getId).toList();
        Map<Long, List<PartResponse>> partsByMessage = new HashMap<>();
        for (MessagePart part : messagePartRepository.findByMessageIdInOrderByIdAsc(messageIds)) {
            partsByMessage.computeIfAbsent(part.getMessageId(), k -> new java.util.ArrayList<>())
                    .add(toPart(part));
        }
        return messages.stream()
                .map(m -> new MessageResponse(
                        m.getId(),
                        m.getConversationId(),
                        StatusView.of(m.getRole()),
                        StatusView.of(m.getStatus()),
                        m.getReferenceId(),
                        m.getClientMessageId(),
                        m.getCreatedAt(),
                        partsByMessage.getOrDefault(m.getId(), List.of())))
                .toList();
    }

    /** 파트 매핑 (payload JSON 문자열을 객체로 파싱해서 내림) */
    private PartResponse toPart(MessagePart part) {
        return new PartResponse(
                part.getId(),
                StatusView.of(part.getType()),
                StatusView.of(part.getStatus()),
                part.getContent(),
                part.getExecutionId(),
                part.getInvestigationId(),
                part.getCardType(),
                RecipeJsonUtil.toObject(part.getPayloadJson()),
                part.getSchemaVersion());
    }

    /** session_list_update 스냅샷 매핑 (목록 한 줄 전체). serviceName은 단건 조회로 채운다. */
    private ConversationListSnapshot toListSnapshot(Conversation conversation) {
        return new ConversationListSnapshot(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getApiSpecId(),
                serviceNameOf(conversation.getApiSpecId()),
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
