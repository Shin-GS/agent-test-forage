package com.testforge.demo.blog;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * demo-blog 공용 인메모리 스토어. 게시글/댓글/태그/사용자/세션을 한곳에서 관리한다.
 * DB/JPA 없이 {@link ConcurrentHashMap} 기반으로 동작하며, 기동 시 게시글/태그/사용자를 시드한다.
 */
@Component
public class BlogStore {

    /** 게시글 저장소 */
    private final Map<Long, Post> posts = new ConcurrentHashMap<>();
    /** 댓글 저장소 (commentId -> comment) */
    private final Map<Long, Comment> comments = new ConcurrentHashMap<>();
    /** 태그 저장소 (name -> tag) */
    private final Map<String, Tag> tags = new ConcurrentHashMap<>();
    /** 사용자 저장소 (username -> password) */
    private final Map<String, String> users = new ConcurrentHashMap<>();
    /** 유효한 세션 토큰 집합 */
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    private final AtomicLong postIdSeq = new AtomicLong(101);
    private final AtomicLong commentIdSeq = new AtomicLong(9001);

    public BlogStore() {
        seedTags();
        seedPosts();
        seedUsers();
    }

    // ---- 시드 ----

    private void seedTags() {
        putTag("공지");
        putTag("개발");
        putTag("일상");
    }

    private void seedPosts() {
        createPost("환영합니다", "블로그 데모에 오신 것을 환영합니다.", "공지", "PUBLISHED");
        createPost("첫 개발 글", "Spring Boot 로 데모 서버를 만들었습니다.", "개발", "PUBLISHED");
        createPost("작성 중인 초안", "아직 다듬는 중입니다.", "일상", "DRAFT");
    }

    private void seedUsers() {
        users.put("demo", "demo1234");
    }

    // ---- 태그 ----

    public Tag putTag(String name) {
        Tag tag = new Tag(name, 0);
        tags.put(name, tag);
        return tag;
    }

    public List<Tag> listTags() {
        return tags.values().stream()
                .sorted(Comparator.comparing(Tag::name))
                .toList();
    }

    public boolean hasTag(String name) {
        return name != null && tags.containsKey(name);
    }

    public Optional<Tag> findTag(String name) {
        return Optional.ofNullable(tags.get(name));
    }

    // ---- 게시글 ----

    /** 게시글 생성. status 는 DRAFT/PUBLISHED. tag 는 미리 등록된 태그명(없으면 새로 등록). */
    public Post createPost(String title, String content, String tag, String status) {
        long id = postIdSeq.getAndIncrement();
        if (tag != null && !tag.isBlank() && !tags.containsKey(tag)) {
            putTag(tag);
        }
        Post post = new Post(id, title, content, tag, status, LocalDate.now().toString());
        posts.put(id, post);
        return post;
    }

    public List<Post> listPosts(String tag, String status) {
        return posts.values().stream()
                .filter(p -> tag == null || tag.equals(p.tag()))
                .filter(p -> status == null || status.equalsIgnoreCase(p.status()))
                .sorted(Comparator.comparingLong(Post::id))
                .toList();
    }

    public Optional<Post> findPost(long id) {
        return Optional.ofNullable(posts.get(id));
    }

    /** 게시글 상태를 변경한 새 스냅샷으로 교체 저장한다. */
    public Post updatePostStatus(Post post, String status) {
        Post updated = new Post(post.id(), post.title(), post.content(), post.tag(), status, post.createdAt());
        posts.put(post.id(), updated);
        return updated;
    }

    public boolean deletePost(long id) {
        return posts.remove(id) != null;
    }

    // ---- 댓글 ----

    public Comment createComment(long postId, String author, String content) {
        long id = commentIdSeq.getAndIncrement();
        Comment comment = new Comment(id, postId, author, content, LocalDate.now().toString());
        comments.put(id, comment);
        return comment;
    }

    public List<Comment> listComments(long postId) {
        List<Comment> result = new ArrayList<>();
        for (Comment c : comments.values()) {
            if (c.postId() == postId) {
                result.add(c);
            }
        }
        result.sort(Comparator.comparingLong(Comment::commentId));
        return result;
    }

    // ---- 사용자 / 세션 ----

    public boolean authenticate(String username, String password) {
        return username != null && password != null && password.equals(users.get(username));
    }

    public String createSession() {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.add(token);
        return token;
    }

    public boolean isValidSession(String token) {
        return token != null && sessions.contains(token);
    }

    // ---- 모델 ----

    /** 게시글 응답 모델 */
    public record Post(Long id, String title, String content, String tag, String status, String createdAt) {
    }

    /** 댓글 응답 모델 */
    public record Comment(Long commentId, Long postId, String author, String content, String createdAt) {
    }

    /** 태그 응답 모델 */
    public record Tag(String name, int postCount) {
    }
}
