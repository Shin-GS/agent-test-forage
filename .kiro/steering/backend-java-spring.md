---
sourceId: backend-java-spring
sourceVersion: "1.2"
sourceUpdatedAt: 2026-08-27
inclusion: fileMatch
fileMatchPattern: "**/*.java,**/*.kts"
---

# Backend Java Spring Boot 규칙

## 1. Lombok 규칙

**왜**: 불필요한 보일러플레이트를 줄이되, 명시적으로 어떤 기능을 사용하는지 드러내기 위함.

- `@Data` 사용 지양 — equals/hashCode 자동 생성이 JPA Entity에서 문제를 일으킬 수 있음
- `@Setter` 대신 `@Builder` 사용 — 불변성 유지, 필요시 `@Setter` 허용
- 권장: `@Getter`, `@Builder`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, `@Slf4j`

## 2. Entity 패턴

**왜**: JPA 프록시 생성을 위해 protected no-arg 생성자 필요, Builder로 명시적 객체 생성.

```java
@Getter
@Entity
@Table(name = "TABLE_NAME")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EntityName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "COLUMN_NAME", nullable = false)
    private String fieldName;
}
```

## 3. Service 패턴

**왜**: 클래스 레벨 readOnly로 기본 읽기 최적화, 쓰기 메서드만 @Transactional 추가.

```java
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SomeService {

    private final SomeRepository someRepository;

    @Transactional
    public void create(CreateRequest request) {
        // 비즈니스 로직
    }
}
```

- Service 클래스는 `{Name}Service` 접미사 사용
- Controller와 Service는 1:1 매핑을 강제하지 않음

## 4. Controller 패턴

```java
@Tag(name = "Resource", description = "Resource management")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private final ResourceService resourceService;

    @Operation(summary = "리소스 목록 조회", description = "페이지네이션된 리소스 목록을 반환합니다.")
    @GetMapping
    public ResponseEntity<PageResponse<ResourceResponse>> getAll(
            @Parameter(description = "페이지 번호 (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(resourceService.getAll(page, size));
    }
}
```

### Swagger 어노테이션 규칙
- 클래스 레벨: `@Tag(name, description)` 필수
- 메서드 레벨: `@Operation(summary, description)` 필수
- 파라미터: `@Parameter(description)`

## 5. DTO 패턴

```java
public record ResourceResponse(
    Long id,
    String title,
    String description,
    LocalDateTime createdAt
) {}
```

- 응답: `{Name}Response` 접미사
- 요청: `{Name}Request` 접미사
- Entity를 API request/response에 직접 사용 금지

### DTO 패키지 구조

```
dto/{도메인}/
├── request/     # 요청 DTO
└── response/    # 응답 DTO
```

## 6. Enum 패턴

### EnumColumn 인터페이스 (공통)

```java
public interface EnumColumn {
    String getCode();
    String getDescription();
}
```

### Enum 클래스 작성 패턴

```java
/**
 * 상태 설명
 */
@Getter
@RequiredArgsConstructor
public enum Status implements EnumColumn {
    ACTIVE("ACTIVE", "활성"),
    INACTIVE("INACTIVE", "비활성");

    private final String code;
    private final String description;
}
```

## 7. 예외 처리

- 비즈니스 예외는 커스텀 Exception 클래스 사용
- `@RestControllerAdvice`로 글로벌 예외 핸들링
- HTTP 상태 코드를 의미에 맞게 사용

## 8. 하드 제약 (Hard Constraints)

1. **`@Enumerated(EnumType.ORDINAL)` 절대 금지** — `@Enumerated(EnumType.STRING)` 또는 `AttributeConverter` 사용
2. **Inline FQCN 금지** — 반드시 import문으로 선언 후 단순 클래스명 사용
3. **모든 DB 저장 Enum은 `EnumColumn` 인터페이스 구현 필수**
4. **Entity를 API request/response에 직접 사용 금지** — 반드시 DTO 분리
5. **Service에서 HttpServletRequest/Response 직접 사용 금지**

## 9. 피해야 할 패턴

- Entity에서 DTO 직접 참조 — 레이어 의존성 역전
- 비즈니스 로직을 Controller에 작성 — 재사용/테스트 불가
- Controller에서 Service로 Entity 객체 전달 — detached 상태 위험
- `@Data` 사용 — equals/hashCode 자동 생성이 JPA Entity에서 문제 유발
- 트랜잭션 없이 여러 Repository 호출 후 조합 — 정합성 미보장
