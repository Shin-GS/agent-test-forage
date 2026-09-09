package com.testforge.demo.hr;

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

/**
 * 직원 API. 조회는 공개, 등록은 세션 쿠키 인증이 필요하다({@link SessionInterceptor}가 POST 를 보호).
 * 직원 데이터는 {@link HrStore}에서 인메모리로 관리한다.
 */
@RestController
@RequestMapping("/employees")
@Tag(name = "직원", description = "직원 등록/조회 API")
public class EmployeeController {

    private final HrStore store;

    public EmployeeController(HrStore store) {
        this.store = store;
    }

    @Operation(summary = "직원 목록 조회",
            description = "직원 목록을 조회한다. departmentId(부서 ID)로 특정 부서 직원만 필터링할 수 있다. 공개 API.")
    @GetMapping
    public List<HrStore.Employee> list(@RequestParam(required = false) Long departmentId) {
        return store.listEmployees(departmentId);
    }

    @Operation(summary = "직원 단건 조회", description = "직원 ID로 단건 직원을 조회한다. 없으면 404. 공개 API.")
    @GetMapping("/{id}")
    public ResponseEntity<HrStore.Employee> detail(@PathVariable Long id) {
        return store.findEmployee(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "직원 등록",
            description = "name(이름), departmentId(부서 ID), position(직책)으로 직원을 등록한다. "
                    + "departmentId 는 존재하는 부서여야 한다. 부서가 없으면 400. 인증 필요.")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateEmployeeRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "name이 필요합니다."));
        }
        if (request.departmentId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "departmentId가 필요합니다."));
        }
        if (store.findDepartment(request.departmentId()).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "존재하지 않는 부서입니다: departmentId=" + request.departmentId()));
        }
        HrStore.Employee employee = store.createEmployee(request.name(), request.departmentId(), request.position());
        return ResponseEntity.status(201).body(employee);
    }

    /** 직원 등록 요청 모델 */
    public record CreateEmployeeRequest(String name, Long departmentId, String position) {
    }
}
