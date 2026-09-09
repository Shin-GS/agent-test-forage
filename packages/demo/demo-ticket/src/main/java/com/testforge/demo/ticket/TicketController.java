package com.testforge.demo.ticket;

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
 * 티켓 API. 조회는 공개, 생성/상태변경은 세션 쿠키 인증이 필요하다({@link SessionInterceptor}).
 * 티켓 데이터는 {@link TicketStore}에서 인메모리로 관리한다.
 * OpenAPI summary/description은 AI 매칭 품질에 직결되므로 명확히 작성한다.
 */
@RestController
@RequestMapping("/tickets")
@Tag(name = "티켓", description = "이슈 티켓 생성/조회/상태변경 API")
public class TicketController {

    /** 허용 우선순위값 */
    private static final Set<String> PRIORITIES = Set.of("LOW", "HIGH");
    /** 허용 상태값 */
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "CLOSED");

    private final TicketStore store;

    public TicketController(TicketStore store) {
        this.store = store;
    }

    @Operation(summary = "티켓 목록 조회",
            description = "티켓 목록을 조회한다. status(OPEN/IN_PROGRESS/CLOSED)와 priority(LOW/HIGH)로 필터링할 수 있다.")
    @GetMapping
    public List<TicketStore.Ticket> list(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String priority) {
        return store.listTickets(status, priority);
    }

    @Operation(summary = "티켓 단건 조회", description = "티켓 ID로 단건 티켓을 조회한다. 없으면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<TicketStore.Ticket> detail(@PathVariable Long id) {
        return store.findTicket(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "티켓 생성",
            description = "title/description/priority로 티켓을 생성한다. priority는 LOW 또는 HIGH여야 한다. "
                    + "생성된 티켓의 초기 상태는 OPEN이다. 인증 필요.")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateTicketRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "title이 필요합니다."));
        }
        if (request.description() == null || request.description().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "description이 필요합니다."));
        }
        if (request.priority() == null || !PRIORITIES.contains(request.priority().toUpperCase())) {
            return ResponseEntity.badRequest().body(Map.of("message", "priority는 LOW 또는 HIGH 여야 합니다."));
        }
        TicketStore.Ticket ticket = store.createTicket(
                request.title(), request.description(), request.priority().toUpperCase(), "OPEN");
        return ResponseEntity.status(201).body(ticket);
    }

    @Operation(summary = "티켓 상태 변경",
            description = "티켓 상태를 변경한다. status는 OPEN/IN_PROGRESS/CLOSED 중 하나여야 한다. "
                    + "티켓이 없으면 404, 잘못된 status면 400. 인증 필요.")
    @PostMapping("/{id}/status")
    public ResponseEntity<?> changeStatus(@PathVariable Long id, @RequestBody ChangeStatusRequest request) {
        var found = store.findTicket(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (request.status() == null || !STATUSES.contains(request.status().toUpperCase())) {
            return ResponseEntity.badRequest().body(Map.of("message", "status는 OPEN/IN_PROGRESS/CLOSED 여야 합니다."));
        }
        TicketStore.Ticket updated = store.updateTicketStatus(found.get(), request.status().toUpperCase());
        return ResponseEntity.ok(updated);
    }

    /** 티켓 생성 요청 모델 */
    public record CreateTicketRequest(String title, String description, String priority) {
    }

    /** 티켓 상태 변경 요청 모델 */
    public record ChangeStatusRequest(String status) {
    }
}
