package com.testforge.demo.ticket;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 코멘트 API. 조회는 공개, 작성은 세션 쿠키 인증이 필요하다({@link SessionInterceptor}가 티켓 하위 경로 보호).
 * 코멘트는 티켓에 종속되며 {@link TicketStore}에서 인메모리로 관리한다.
 */
@RestController
@RequestMapping("/tickets/{ticketId}/comments")
@Tag(name = "코멘트", description = "티켓 코멘트 작성/조회 API")
public class CommentController {

    private final TicketStore store;

    public CommentController(TicketStore store) {
        this.store = store;
    }

    @Operation(summary = "코멘트 목록 조회", description = "티켓 ID의 코멘트 목록을 조회한다. 티켓이 없으면 404.")
    @GetMapping
    public ResponseEntity<List<TicketStore.Comment>> list(@PathVariable Long ticketId) {
        if (store.findTicket(ticketId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(store.listComments(ticketId));
    }

    @Operation(summary = "코멘트 작성",
            description = "티켓에 코멘트를 작성한다. author와 content가 필요하다. 티켓이 없으면 404. 인증 필요.")
    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long ticketId, @RequestBody CreateCommentRequest request) {
        if (store.findTicket(ticketId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (request.author() == null || request.author().isBlank()
                || request.content() == null || request.content().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "author와 content가 필요합니다."));
        }
        TicketStore.Comment comment = store.createComment(ticketId, request.author(), request.content());
        return ResponseEntity.status(201).body(comment);
    }

    /** 코멘트 작성 요청 모델 */
    public record CreateCommentRequest(String author, String content) {
    }
}
