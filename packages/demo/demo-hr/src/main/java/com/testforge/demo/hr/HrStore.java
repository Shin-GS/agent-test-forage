package com.testforge.demo.hr;

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
 * demo-hr 공용 인메모리 스토어. 부서/직원/휴가/사용자/세션을 한곳에서 관리한다.
 * DB/JPA 없이 {@link ConcurrentHashMap} 기반으로 동작하며, 기동 시 부서/직원/사용자를 시드한다.
 */
@Component
public class HrStore {

    /** 부서 저장소 */
    private final Map<Long, Department> departments = new ConcurrentHashMap<>();
    /** 직원 저장소 */
    private final Map<Long, Employee> employees = new ConcurrentHashMap<>();
    /** 휴가 저장소 */
    private final Map<Long, Leave> leaves = new ConcurrentHashMap<>();
    /** 사용자 저장소 (username -> password) */
    private final Map<String, String> users = new ConcurrentHashMap<>();
    /** 유효한 세션 토큰 집합 */
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    /** 허용 휴가 타입 */
    static final Set<String> LEAVE_TYPES = Set.of("ANNUAL", "SICK");

    private final AtomicLong departmentIdSeq = new AtomicLong(1);
    private final AtomicLong employeeIdSeq = new AtomicLong(1001);
    private final AtomicLong leaveIdSeq = new AtomicLong(5001);

    public HrStore() {
        seedDepartments();
        seedEmployees();
        seedUsers();
    }

    // ---- 시드 ----

    private void seedDepartments() {
        createDepartment("개발팀");
        createDepartment("인사팀");
        createDepartment("영업팀");
    }

    private void seedEmployees() {
        // 부서 시드 순서상 개발팀=1, 인사팀=2, 영업팀=3
        createEmployee("김개발", 1L, "백엔드 엔지니어");
        createEmployee("이인사", 2L, "인사 담당자");
        createEmployee("박영업", 3L, "영업 대표");
    }

    private void seedUsers() {
        users.put("demo", "demo1234");
    }

    // ---- 부서 ----

    public Department createDepartment(String name) {
        long id = departmentIdSeq.getAndIncrement();
        Department department = new Department(id, name);
        departments.put(id, department);
        return department;
    }

    public List<Department> listDepartments() {
        return departments.values().stream()
                .sorted(Comparator.comparingLong(Department::id))
                .toList();
    }

    public Optional<Department> findDepartment(long id) {
        return Optional.ofNullable(departments.get(id));
    }

    // ---- 직원 ----

    /** 직원 생성. departmentId 는 존재하는 부서여야 한다(검증은 컨트롤러에서). */
    public Employee createEmployee(String name, Long departmentId, String position) {
        long id = employeeIdSeq.getAndIncrement();
        Employee employee = new Employee(id, name, departmentId, position, LocalDate.now().toString());
        employees.put(id, employee);
        return employee;
    }

    public List<Employee> listEmployees(Long departmentId) {
        return employees.values().stream()
                .filter(e -> departmentId == null || departmentId.equals(e.departmentId()))
                .sorted(Comparator.comparingLong(Employee::id))
                .toList();
    }

    public Optional<Employee> findEmployee(long id) {
        return Optional.ofNullable(employees.get(id));
    }

    // ---- 휴가 ----

    public Leave createLeave(long employeeId, String startDate, String endDate, String type) {
        long id = leaveIdSeq.getAndIncrement();
        Leave leave = new Leave(id, employeeId, startDate, endDate, type, "REQUESTED", LocalDate.now().toString());
        leaves.put(id, leave);
        return leave;
    }

    public Optional<Leave> findLeave(long id) {
        return Optional.ofNullable(leaves.get(id));
    }

    /** 휴가 상태를 변경한 새 스냅샷으로 교체 저장한다. */
    public Leave updateLeaveStatus(Leave leave, String status) {
        Leave updated = new Leave(leave.id(), leave.employeeId(), leave.startDate(),
                leave.endDate(), leave.type(), status, leave.createdAt());
        leaves.put(leave.id(), updated);
        return updated;
    }

    public List<Leave> listLeaves(long employeeId) {
        List<Leave> result = new ArrayList<>();
        for (Leave l : leaves.values()) {
            if (l.employeeId() == employeeId) {
                result.add(l);
            }
        }
        result.sort(Comparator.comparingLong(Leave::id));
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

    /** 부서 응답 모델 */
    public record Department(Long id, String name) {
    }

    /** 직원 응답 모델 */
    public record Employee(Long id, String name, Long departmentId, String position, String hiredAt) {
    }

    /** 휴가 응답 모델. type 은 ANNUAL/SICK, status 는 REQUESTED/APPROVED. */
    public record Leave(Long id, Long employeeId, String startDate, String endDate,
                        String type, String status, String createdAt) {
    }
}
