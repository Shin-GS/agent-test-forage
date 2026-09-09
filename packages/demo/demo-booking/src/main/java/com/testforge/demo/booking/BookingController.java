package com.testforge.demo.booking;

import com.testforge.client.annotation.TestForgeConfirm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 예약 API. 조회는 공개, 생성/취소는 세션 쿠키 인증이 필요하다({@link SessionInterceptor}).
 * 예약 데이터는 {@link BookingStore}에서 인메모리로 관리한다.
 * OpenAPI summary/description은 AI 매칭 품질에 직결되므로 명확히 작성한다.
 */
@RestController
@RequestMapping("/bookings")
@Tag(name = "예약", description = "예약 생성/조회/취소 API")
public class BookingController {

    private final BookingStore store;

    public BookingController(BookingStore store) {
        this.store = store;
    }

    @Operation(summary = "예약 생성",
            description = "seatId(좌석 ID)/date(예약 날짜 YYYY-MM-DD)/partySize(인원 수)로 예약을 생성한다. "
                    + "좌석이 없으면 404, 인원 수가 좌석 수용 인원을 초과하면 409. 인증 필요.")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateBookingRequest request) {
        if (request.seatId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "seatId가 필요합니다."));
        }
        if (request.date() == null || request.date().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "date가 필요합니다. (YYYY-MM-DD)"));
        }
        if (request.partySize() == null || request.partySize() < 1) {
            return ResponseEntity.badRequest().body(Map.of("message", "partySize는 1 이상이어야 합니다."));
        }
        var found = store.findSeat(request.seatId());
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        BookingStore.Seat seat = found.get();
        if (request.partySize() > seat.capacity()) {
            return ResponseEntity.status(409).body(Map.of(
                    "message", "인원 수가 좌석 수용 인원을 초과합니다: capacity=" + seat.capacity()));
        }
        BookingStore.Booking booking = store.createBooking(request.seatId(), request.date(), request.partySize());
        return ResponseEntity.status(201).body(booking);
    }

    @Operation(summary = "예약 단건 조회", description = "예약 ID로 단건 예약을 조회한다. 없으면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<BookingStore.Booking> detail(@PathVariable Long id) {
        return store.findBooking(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "예약 취소",
            description = "예약을 CANCELLED 상태로 취소한다. 되돌릴 수 없는 작업이라 실행 전 사용자 확인이 필요하다. "
                    + "예약이 없으면 404, 이미 취소됐으면 409. 인증 필요.")
    @TestForgeConfirm(message = "예약이 취소됩니다")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        var found = store.findBooking(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        BookingStore.Booking booking = found.get();
        if ("CANCELLED".equalsIgnoreCase(booking.status())) {
            return ResponseEntity.status(409).body(Map.of("message", "이미 취소된 예약입니다: bookingId=" + id));
        }
        BookingStore.Booking cancelled = store.updateBookingStatus(booking, "CANCELLED");
        return ResponseEntity.ok(cancelled);
    }

    /** 예약 생성 요청 모델 */
    public record CreateBookingRequest(Long seatId, String date, Integer partySize) {
    }
}
