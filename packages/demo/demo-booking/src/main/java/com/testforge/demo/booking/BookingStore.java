package com.testforge.demo.booking;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * demo-booking 공용 인메모리 스토어. 좌석/예약/사용자/세션을 한곳에서 관리한다.
 * DB/JPA 없이 {@link ConcurrentHashMap} 기반으로 동작하며, 기동 시 좌석/사용자를 시드한다.
 */
@Component
public class BookingStore {

    /** 좌석 저장소 (seatId -> seat) */
    private final Map<Long, Seat> seats = new ConcurrentHashMap<>();
    /** 예약 저장소 (bookingId -> booking) */
    private final Map<Long, Booking> bookings = new ConcurrentHashMap<>();
    /** 사용자 저장소 (username -> password) */
    private final Map<String, String> users = new ConcurrentHashMap<>();
    /** 유효한 세션 토큰 집합 */
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    private final AtomicLong seatIdSeq = new AtomicLong(1);
    private final AtomicLong bookingIdSeq = new AtomicLong(5001);

    public BookingStore() {
        seedSeats();
        seedUsers();
    }

    // ---- 시드 ----

    private void seedSeats() {
        putSeat("창가 2인석", 2);
        putSeat("홀 4인석", 4);
        putSeat("룸 6인석", 6);
        putSeat("바 1인석", 1);
    }

    private void seedUsers() {
        users.put("demo", "demo1234");
    }

    // ---- 좌석 ----

    /** 좌석 생성. capacity 는 최대 수용 인원. */
    public Seat putSeat(String name, int capacity) {
        long id = seatIdSeq.getAndIncrement();
        Seat seat = new Seat(id, name, capacity, true);
        seats.put(id, seat);
        return seat;
    }

    /** 가용(available) 좌석 목록을 조회한다. */
    public List<Seat> listAvailableSeats() {
        return seats.values().stream()
                .filter(Seat::available)
                .sorted(Comparator.comparingLong(Seat::id))
                .toList();
    }

    public Optional<Seat> findSeat(long id) {
        return Optional.ofNullable(seats.get(id));
    }

    // ---- 예약 ----

    /** 예약 생성. status 는 CONFIRMED 로 시작한다. */
    public Booking createBooking(long seatId, String date, int partySize) {
        long id = bookingIdSeq.getAndIncrement();
        Booking booking = new Booking(id, seatId, date, partySize, "CONFIRMED", LocalDate.now().toString());
        bookings.put(id, booking);
        return booking;
    }

    public Optional<Booking> findBooking(long id) {
        return Optional.ofNullable(bookings.get(id));
    }

    /** 예약 상태를 변경한 새 스냅샷으로 교체 저장한다. */
    public Booking updateBookingStatus(Booking booking, String status) {
        Booking updated = new Booking(booking.bookingId(), booking.seatId(), booking.date(),
                booking.partySize(), status, booking.createdAt());
        bookings.put(booking.bookingId(), updated);
        return updated;
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

    /** 좌석 응답 모델 */
    public record Seat(Long id, String name, int capacity, boolean available) {
    }

    /** 예약 응답 모델 */
    public record Booking(Long bookingId, Long seatId, String date, int partySize, String status, String createdAt) {
    }
}
