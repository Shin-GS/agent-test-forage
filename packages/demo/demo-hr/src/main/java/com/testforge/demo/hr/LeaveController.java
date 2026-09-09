package com.testforge.demo.hr;

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
 * 휴가 API. 조회는 공개, 신청/승인은 세션 쿠키 인증이 필요하다({@link SessionInterceptor}가 POST 를 보호).
 * 휴가 데이터는 {@link HrStore}에서 인메모리로 관리한다.
 */
@RestController
@RequestMapping("/leaves")
@Tag(name = "휴가", description = "휴가 신청/조회/승인 API")
public class LeaveController {

    private final HrStore store;

    public LeaveController(HrStore store) {
        this.store = store;
    }

    @Operation(summary = "휴가 신청",
            description = "employeeId(직원 ID), startDate/endDate(YYYY-MM-DD 형식 문자열), "
                    + "type(ANNUAL=연차 또는 SICK=병가)으로 휴가를 신청한다. 직원이 없으면 404, "
                    + "type/날짜가 유효하지 않으면 400. 신청 직후 상태는 REQUESTED. 인증 필요.")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateLeaveRequest request) {
        if (request.employeeId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "employeeId가 필요합니다."));
        }
        if (store.findEmployee(request.employeeId()).isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "존재하지 않는 직원입니다: employeeId=" + request.employeeId()));
        }
        if (isBlank(request.startDate()) || isBlank(request.endDate())) {
            return ResponseEntity.badRequest().body(Map.of("message", "startDate와 endDate가 필요합니다(YYYY-MM-DD)."));
        }
        String type = request.type() == null ? null : request.type().toUpperCase();
        if (type == null || !HrStore.LEAVE_TYPES.contains(type)) {
            return ResponseEntity.badRequest().body(Map.of("message", "type은 ANNUAL 또는 SICK 이어야 합니다."));
        }
        HrStore.Leave leave = store.createLeave(request.employeeId(), request.startDate(), request.endDate(), type);
        return ResponseEntity.status(201).body(leave);
    }

    @Operation(summary = "휴가 단건 조회", description = "휴가 ID로 단건 휴가 신청 내역을 조회한다. 없으면 404. 공개 API.")
    @GetMapping("/{id}")
    public ResponseEntity<HrStore.Leave> detail(@PathVariable Long id) {
        return store.findLeave(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "휴가 승인",
            description = "신청(REQUESTED) 상태의 휴가를 APPROVED 로 승인한다. 휴가가 없으면 404, "
                    + "이미 승인됐으면 409. 인증 필요.")
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        var found = store.findLeave(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HrStore.Leave leave = found.get();
        if ("APPROVED".equalsIgnoreCase(leave.status())) {
            return ResponseEntity.status(409).body(Map.of("message", "이미 승인된 휴가입니다: leaveId=" + id));
        }
        HrStore.Leave approved = store.updateLeaveStatus(leave, "APPROVED");
        return ResponseEntity.ok(approved);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 휴가 신청 요청 모델. startDate/endDate 는 YYYY-MM-DD 문자열, type 은 ANNUAL/SICK. */
    public record CreateLeaveRequest(Long employeeId, String startDate, String endDate, String type) {
    }
}
