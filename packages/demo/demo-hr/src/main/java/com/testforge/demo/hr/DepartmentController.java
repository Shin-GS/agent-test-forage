package com.testforge.demo.hr;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 부서 API. 조회 전용(공개)이며 부서 데이터는 {@link HrStore}에서 인메모리로 관리한다.
 * OpenAPI summary/description은 AI 매칭 품질에 직결되므로 명확히 작성한다.
 */
@RestController
@RequestMapping("/departments")
@Tag(name = "부서", description = "부서 조회 API")
public class DepartmentController {

    private final HrStore store;

    public DepartmentController(HrStore store) {
        this.store = store;
    }

    @Operation(summary = "부서 목록 조회", description = "등록된 모든 부서 목록을 조회한다. 공개 API.")
    @GetMapping
    public List<HrStore.Department> list() {
        return store.listDepartments();
    }

    @Operation(summary = "부서 단건 조회", description = "부서 ID로 단건 부서를 조회한다. 없으면 404. 공개 API.")
    @GetMapping("/{id}")
    public ResponseEntity<HrStore.Department> detail(@PathVariable Long id) {
        return store.findDepartment(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
