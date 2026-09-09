package com.testforge.demo.blog;

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
 * 댓글 API. 조회는 공개, 작성은 세션 쿠키 인증이 필요하다({@link SessionInterceptor}가 댓글 작성 경로 보호).
 * 댓글은 게시글에 종속되며 {@link BlogStore}에서 인메모리로 관리한다.
 */
@RestController
@RequestMapping("/posts/{postId}/comments")
@Tag(name = "댓글", description = "게시글 댓글 작성/조회 API")
public class CommentController {

    private final BlogStore store;

    public CommentController(BlogStore store) {
        this.store = store;
    }

    @Operation(summary = "댓글 목록 조회", description = "게시글 ID의 댓글 목록을 조회한다. 게시글이 없으면 404.")
    @GetMapping
    public ResponseEntity<List<BlogStore.Comment>> list(@PathVariable Long postId) {
        if (store.findPost(postId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(store.listComments(postId));
    }

    @Operation(summary = "댓글 작성",
            description = "게시글에 댓글을 작성한다. author와 content가 필요하다. 게시글이 없으면 404. 인증 필요.")
    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long postId, @RequestBody CreateCommentRequest request) {
        if (store.findPost(postId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (request.author() == null || request.author().isBlank()
                || request.content() == null || request.content().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "author와 content가 필요합니다."));
        }
        BlogStore.Comment comment = store.createComment(postId, request.author(), request.content());
        return ResponseEntity.status(201).body(comment);
    }

    /** 댓글 작성 요청 모델 */
    public record CreateCommentRequest(String author, String content) {
    }
}
