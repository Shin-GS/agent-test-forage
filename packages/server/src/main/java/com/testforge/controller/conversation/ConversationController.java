package com.testforge.controller.conversation;

import com.testforge.dto.conversation.ConversationDetailResponse;
import com.testforge.dto.conversation.ConversationStartRequest;
import com.testforge.dto.conversation.ConversationStartResponse;
import com.testforge.dto.conversation.ConversationSummaryResponse;
import com.testforge.dto.conversation.ConversationTitleUpdateRequest;
import com.testforge.dto.conversation.MessageResponse;
import com.testforge.dto.conversation.MessageSendRequest;
import com.testforge.dto.conversation.MessageSendResponse;
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
 * AI 처리/실행 엔진(ai_responding/executing 세분 전이, 락 장기 점유)은 다음 조각에서 다룬다.
 *
 * <p>TODO: 인증/권한 (auth 도메인 구현 후: 본인 대화방만 접근). 현재 userId는
 * 요청 파라미터/바디로 받되 소유권 검증은 하지 않는다.
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
        // TODO: 인증/권한 (auth 도메인 구현 후: userId를 인증 주체에서 도출)
        ConversationStartResponse response = conversationService.start(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 대화방 목록 (userId 필터, 미삭제, lastMessageAt DESC). 각 항목에 unread 포함. */
    @GetMapping
    public List<ConversationSummaryResponse> list(@RequestParam Long userId) {
        // TODO: 인증/권한 (auth 도메인 구현 후: 본인 대화방만 조회)
        return conversationService.list(userId);
    }

    /** 대화방 상세 (없거나 삭제 시 404) */
    @GetMapping("/{id}")
    public ConversationDetailResponse detail(@PathVariable Long id) {
        return conversationService.detail(id);
    }

    /** 대화방 이름 변경 */
    @PatchMapping("/{id}/title")
    public ConversationDetailResponse updateTitle(@PathVariable Long id,
                                                  @RequestBody ConversationTitleUpdateRequest request) {
        // TODO: 인증/권한 (auth 도메인 구현 후: 본인 대화방만 수정)
        return conversationService.updateTitle(id, request.title());
    }

    /** 읽음 처리 (lastReadAt = now) */
    @PatchMapping("/{id}/read")
    public ConversationDetailResponse markRead(@PathVariable Long id) {
        // TODO: 인증/권한 (auth 도메인 구현 후: 본인 대화방만 읽음 처리)
        return conversationService.markRead(id);
    }

    /** 대화방 소프트 삭제 (DELETED_AT = now) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // TODO: 인증/권한 (auth 도메인 구현 후: 본인 대화방만 삭제)
        conversationService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    /** 대화방 메시지 목록 (SEQ 오름차순). 없거나 삭제된 대화방이면 404. */
    @GetMapping("/{id}/messages")
    public List<MessageResponse> listMessages(@PathVariable Long id) {
        return conversationService.listMessages(id);
    }

    /**
     * 메시지 전송(동기 접수). 사용자 메시지를 저장하고 lastMessageAt을 갱신한다.
     * 대화방이 이미 처리 중이면(락 경합) 409 CONVERSATION_BUSY. AI 처리는 다음 조각.
     */
    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageSendResponse> sendMessage(@PathVariable Long id,
                                                           @RequestBody MessageSendRequest request) {
        // TODO: 인증/권한 (auth 도메인 구현 후: 본인 대화방만 전송)
        MessageSendResponse response = conversationService.sendMessage(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 액션 피커 [취소]. 대기/락을 해제하고 대화방을 IDLE로 되돌린다(session_status idle 전파).
     * 이미 IDLE이면 멱등 no-op(200). 없거나 삭제된 대화방이면 404.
     */
    @PostMapping("/{id}/cancel")
    public ConversationDetailResponse cancel(@PathVariable Long id) {
        // TODO: 인증/권한 (auth 도메인 구현 후: 본인 대화방만 취소)
        return conversationService.cancel(id);
    }

    /**
     * 실행 [중지]. 실행 중지 후 대화방을 IDLE로 되돌린다(session_status idle 전파).
     * 이미 IDLE이면 멱등 no-op(200). 없거나 삭제된 대화방이면 404.
     */
    @PostMapping("/{id}/stop")
    public ConversationDetailResponse stop(@PathVariable Long id) {
        // TODO: 인증/권한 (auth 도메인 구현 후: 본인 대화방만 중지)
        return conversationService.stop(id);
    }
}
