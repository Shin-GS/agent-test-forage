package com.testforge.controller.conversation;

import com.testforge.dto.common.CursorPage;
import com.testforge.dto.conversation.ConversationDetailResponse;
import com.testforge.dto.conversation.ConversationStartRequest;
import com.testforge.dto.conversation.ConversationStartResponse;
import com.testforge.dto.conversation.ConversationSummaryResponse;
import com.testforge.dto.conversation.ConversationTitleUpdateRequest;
import com.testforge.dto.conversation.MessageResponse;
import com.testforge.dto.conversation.MessageSendRequest;
import com.testforge.dto.conversation.MessageSendResponse;
import com.testforge.security.CurrentUser;
import com.testforge.service.conversation.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 대화방/메시지 CRUD + 대화방 단위 락(동시성) + 취소/중지 API.
 * 채팅 인터페이스(사이드바 대화 목록 + 대화 영역)가 소비하며, 상태 변경은 SSE로 모든 탭에 전파된다.
 * 메시지 접수 시 대화방을 ai_responding으로 전이하고 AI 처리(ChatProcessor)를 비동기로 구동한다.
 * 레시피/플랜 실행 엔진(executing 전이, 실제 스텝 실행)은 다음 조각에서 다룬다.
 *
 * <p>인증: 모든 엔드포인트는 세션 인증 필수(SecurityConfig). userId는 세션에서 도출하며
 * 클라이언트가 보낸 userId는 신뢰하지 않는다(auth.md).
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * 첫 메시지로 대화방 생성. 대화방 ID 없이 첫 메시지를 보내면 대화방을 생성하며
     * 첫 사용자 메시지를 함께 저장한다(빈 대화방 원천 차단). content/userId 필수,
     * apiSpecId/title/referenceId/metadata는 선택.
     */
    @PostMapping("/messages")
    public ResponseEntity<ConversationStartResponse> start(
            @RequestBody ConversationStartRequest request) {
        // userId는 세션에서 도출 (클라이언트 값 무시)
        ConversationStartRequest secured = new ConversationStartRequest(
                CurrentUser.id(), request.content(), request.apiSpecId(),
                request.title(), request.referenceId(), request.metadata());
        ConversationStartResponse response = conversationService.start(secured);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 대화방 목록 (본인 것만, 미삭제, lastMessageAt DESC). 각 항목에 unread 포함. */
    @GetMapping
    public List<ConversationSummaryResponse> list() {
        return conversationService.list(CurrentUser.id());
    }

    /** 대화방 상세 (없거나 삭제/타인 소유면 404) */
    @GetMapping("/{id}")
    public ConversationDetailResponse detail(@PathVariable Long id) {
        return conversationService.detail(id, CurrentUser.id());
    }

    /** 대화방 이름 변경 (본인 대화방만; 타인 소유면 404) */
    @PatchMapping("/{id}/title")
    public ConversationDetailResponse updateTitle(@PathVariable Long id,
                                                  @RequestBody ConversationTitleUpdateRequest request) {
        return conversationService.updateTitle(id, CurrentUser.id(), request.title());
    }

    /** 읽음 처리 (lastReadAt = now; 본인 대화방만) */
    @PatchMapping("/{id}/read")
    public ConversationDetailResponse markRead(@PathVariable Long id) {
        return conversationService.markRead(id, CurrentUser.id());
    }

    /** 대화방 소프트 삭제 (DELETED_AT = now; 본인 대화방만) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        conversationService.softDelete(id, CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    /**
     * 대화방 메시지 목록 (커서 기반 무한 스크롤, 최신순). 없거나 삭제된 대화방이면 404.
     * cursor는 이전 응답의 nextCursor(가장 과거 seq)를 그대로 전달(없으면 첫 페이지=최신).
     * size 기본 20, 최대 50. 응답 items는 최신순이며 FE가 채팅 표시 시 역순 렌더한다.
     */
    @GetMapping("/{id}/messages")
    public CursorPage<MessageResponse> listMessages(@PathVariable Long id,
                                                    @RequestParam(required = false) String cursor,
                                                    @RequestParam(required = false) Integer size) {
        return conversationService.listMessages(id, CurrentUser.id(), cursor, size);
    }

    /**
     * 메시지 전송(동기 접수). 사용자 메시지를 저장하고 ai_responding으로 전이한 뒤 AI 처리를 비동기로
     * 트리거한다. 접수 자체는 거의 즉시 리턴하며(낙관적 UI), AI 응답은 SSE(message_new)로 도착한다.
     * 대화방이 이미 처리 중이면(락 경합) 409 CONVERSATION_BUSY.
     */
    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageSendResponse> sendMessage(@PathVariable Long id,
                                                           @RequestBody MessageSendRequest request) {
        // userId는 세션에서 도출 (클라이언트 값 무시)
        Long requesterId = CurrentUser.id();
        MessageSendRequest secured = new MessageSendRequest(
                requesterId, request.content(), request.referenceId(), request.metadata());
        MessageSendResponse response = conversationService.sendMessage(id, requesterId, secured);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 액션 피커 [취소]. 대기/락을 해제하고 대화방을 IDLE로 되돌린다(session_status idle 전파).
     * 이미 IDLE이면 멱등 no-op(200). 없거나 삭제된 대화방이면 404.
     */
    @PostMapping("/{id}/cancel")
    public ConversationDetailResponse cancel(@PathVariable Long id) {
        return conversationService.cancel(id, CurrentUser.id());
    }

    /**
     * 실행 [중지]. 실행 중지 후 대화방을 IDLE로 되돌린다(session_status idle 전파).
     * 이미 IDLE이면 멱등 no-op(200). 없거나 삭제된 대화방이면 404.
     */
    @PostMapping("/{id}/stop")
    public ConversationDetailResponse stop(@PathVariable Long id) {
        return conversationService.stop(id, CurrentUser.id());
    }
}
