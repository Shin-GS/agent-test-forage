package com.testforge.demo.ticket;

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
 * demo-ticket 공용 인메모리 스토어. 티켓/코멘트/사용자/세션을 한곳에서 관리한다.
 * DB/JPA 없이 {@link ConcurrentHashMap} 기반으로 동작하며, 기동 시 티켓과 사용자를 시드한다.
 */
@Component
public class TicketStore {

    /** 티켓 저장소 (ticketId -> ticket) */
    private final Map<Long, Ticket> tickets = new ConcurrentHashMap<>();
    /** 코멘트 저장소 (commentId -> comment) */
    private final Map<Long, Comment> comments = new ConcurrentHashMap<>();
    /** 사용자 저장소 (username -> password) */
    private final Map<String, String> users = new ConcurrentHashMap<>();
    /** 유효한 세션 토큰 집합 */
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    private final AtomicLong ticketIdSeq = new AtomicLong(101);
    private final AtomicLong commentIdSeq = new AtomicLong(9001);

    public TicketStore() {
        seedTickets();
        seedUsers();
    }

    // ---- 시드 ----

    private void seedTickets() {
        createTicket("로그인 버튼이 동작하지 않음", "로그인 페이지에서 버튼 클릭 시 아무 반응이 없습니다.", "HIGH", "OPEN");
        createTicket("목록 정렬 개선", "티켓 목록을 최신순으로 정렬하면 좋겠습니다.", "LOW", "IN_PROGRESS");
        createTicket("오타 수정", "설정 화면 안내 문구에 오타가 있습니다.", "LOW", "CLOSED");
    }

    private void seedUsers() {
        users.put("demo", "demo1234");
    }

    // ---- 티켓 ----

    /** 티켓 생성. priority 는 LOW/HIGH, status 는 OPEN/IN_PROGRESS/CLOSED. */
    public Ticket createTicket(String title, String description, String priority, String status) {
        long id = ticketIdSeq.getAndIncrement();
        Ticket ticket = new Ticket(id, title, description, priority, status, LocalDate.now().toString());
        tickets.put(id, ticket);
        return ticket;
    }

    public List<Ticket> listTickets(String status, String priority) {
        return tickets.values().stream()
                .filter(t -> status == null || status.equalsIgnoreCase(t.status()))
                .filter(t -> priority == null || priority.equalsIgnoreCase(t.priority()))
                .sorted(Comparator.comparingLong(Ticket::id))
                .toList();
    }

    public Optional<Ticket> findTicket(long id) {
        return Optional.ofNullable(tickets.get(id));
    }

    /** 티켓 상태를 변경한 새 스냅샷으로 교체 저장한다. */
    public Ticket updateTicketStatus(Ticket ticket, String status) {
        Ticket updated = new Ticket(ticket.id(), ticket.title(), ticket.description(),
                ticket.priority(), status, ticket.createdAt());
        tickets.put(ticket.id(), updated);
        return updated;
    }

    // ---- 코멘트 ----

    public Comment createComment(long ticketId, String author, String content) {
        long id = commentIdSeq.getAndIncrement();
        Comment comment = new Comment(id, ticketId, author, content, LocalDate.now().toString());
        comments.put(id, comment);
        return comment;
    }

    public List<Comment> listComments(long ticketId) {
        List<Comment> result = new ArrayList<>();
        for (Comment c : comments.values()) {
            if (c.ticketId() == ticketId) {
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

    /** 티켓 응답 모델 */
    public record Ticket(Long id, String title, String description, String priority, String status, String createdAt) {
    }

    /** 코멘트 응답 모델 */
    public record Comment(Long commentId, Long ticketId, String author, String content, String createdAt) {
    }
}
