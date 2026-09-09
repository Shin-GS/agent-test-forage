package com.testforge.demo.blog;

import com.testforge.client.annotation.TestForgeConfirm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 게시글 API. 조회는 공개, 작성/발행/삭제는 세션 쿠키 인증이 필요하다({@link SessionInterceptor}).
 * 게시글 데이터는 {@link BlogStore}에서 인메모리로 관리한다.
 * OpenAPI summary/description은 AI 매칭 품질에 직결되므로 명확히 작성한다.
 */
@RestController
@RequestMapping("/posts")
@Tag(name = "게시글", description = "게시글 작성/조회/발행/삭제 API")
public class PostController {

    /** 허용 상태값 */
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED");

    private final BlogStore store;

    public PostController(BlogStore store) {
        this.store = store;
    }

    @Operation(summary = "게시글 목록 조회",
            description = "게시글 목록을 조회한다. tag(태그명)와 status(DRAFT/PUBLISHED)로 필터링할 수 있다.")
    @GetMapping
    public List<BlogStore.Post> list(@RequestParam(required = false) String tag,
                                     @RequestParam(required = false) String status) {
        return store.listPosts(tag, status);
    }

    @Operation(summary = "게시글 단건 조회", description = "게시글 ID로 단건 게시글을 조회한다. 없으면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<BlogStore.Post> detail(@PathVariable Long id) {
        return store.findPost(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "게시글 작성",
            description = "title/content/tag/status로 게시글을 작성한다. status는 DRAFT 또는 PUBLISHED(기본 DRAFT). "
                    + "tag는 태그명이며 없는 태그면 새로 등록된다. 인증 필요.")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreatePostRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "title이 필요합니다."));
        }
        String status = request.status() == null ? "DRAFT" : request.status().toUpperCase();
        if (!STATUSES.contains(status)) {
            return ResponseEntity.badRequest().body(Map.of("message", "status는 DRAFT 또는 PUBLISHED 여야 합니다."));
        }
        BlogStore.Post post = store.createPost(request.title(), request.content(), request.tag(), status);
        return ResponseEntity.status(201).body(post);
    }

    @Operation(summary = "게시글 발행",
            description = "초안(DRAFT) 게시글을 PUBLISHED 상태로 발행한다. 게시글이 없으면 404, 이미 발행됐으면 409. 인증 필요.")
    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publish(@PathVariable Long id) {
        var found = store.findPost(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        BlogStore.Post post = found.get();
        if ("PUBLISHED".equalsIgnoreCase(post.status())) {
            return ResponseEntity.status(409).body(Map.of("message", "이미 발행된 게시글입니다: postId=" + id));
        }
        BlogStore.Post published = store.updatePostStatus(post, "PUBLISHED");
        return ResponseEntity.ok(published);
    }

    @Operation(summary = "게시글 삭제",
            description = "게시글을 삭제한다. 되돌릴 수 없는 작업이라 실행 전 사용자 확인이 필요하다. 없으면 404. 인증 필요.")
    @TestForgeConfirm(message = "게시글이 영구 삭제됩니다")
    @PostMapping("/{id}/delete")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (store.deletePost(id)) {
            return ResponseEntity.ok(Map.of("message", "삭제되었습니다", "postId", id));
        }
        return ResponseEntity.notFound().build();
    }

    /** 게시글 작성 요청 모델 */
    public record CreatePostRequest(String title, String content, String tag, String status) {
    }
}
