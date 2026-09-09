package com.testforge.demo.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 좌석 API. 모두 공개 조회이며 예약 가능한 좌석 정보를 제공한다.
 * 좌석 데이터는 {@link BookingStore}에서 인메모리로 관리한다.
 * OpenAPI summary/description은 AI 매칭 품질에 직결되므로 명확히 작성한다.
 */
@RestController
@RequestMapping("/seats")
@Tag(name = "좌석", description = "예약 가능한 좌석 조회 API")
public class SeatController {

    private final BookingStore store;

    public SeatController(BookingStore store) {
        this.store = store;
    }

    @Operation(summary = "가용 좌석 목록 조회",
            description = "예약 가능한(available) 좌석 목록을 조회한다. 각 좌석은 이름과 최대 수용 인원(capacity)을 가진다.")
    @GetMapping
    public List<BookingStore.Seat> list() {
        return store.listAvailableSeats();
    }

    @Operation(summary = "좌석 단건 조회", description = "좌석 ID로 단건 좌석을 조회한다. 없으면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<BookingStore.Seat> detail(@PathVariable Long id) {
        return store.findSeat(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
