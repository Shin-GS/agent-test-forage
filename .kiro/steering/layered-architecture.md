---
sourceId: layered-architecture
sourceVersion: "1.0"
sourceUpdatedAt: 2026-07-01
inclusion: fileMatch
fileMatchPattern: "**/*.java,**/*.kts"
---

# 레이어드 아키텍처 규칙

## 1. 레이어 흐름

```
Controller / Scheduler → Service → Infra → Client(External)
```

## 2. 각 레이어 역할 및 의존 규칙

| 레이어 | 패키지 | 역할 | 의존 가능 대상 |
|--------|--------|------|---------------|
| Controller | `controller/` | HTTP 요청/응답 처리 | service, dto |
| Scheduler | `scheduler/` | 배치/스케줄 트리거 | service, infra, repository |
| Service | `service/` | 도메인 비즈니스 로직 | infra, repository, domain, dto |
| Infra | `infra/` | 외부 시스템 추상화 | client, domain |
| Client | `client/` | 순수 외부 통신 | 없음 (최하위) |

## 3. 의존성 규칙

- `client/`는 `infra/`에서만 참조 (service, controller에서 직접 참조 금지)
- `infra/`는 `service/`에서 참조 (controller에서 직접 참조 금지)
- `service/`는 다른 `service/`를 참조할 수 있음 (순환 참조 주의)
- 새 외부 연동 추가 시: `client/` 서브패키지 + `infra/` 서브패키지 쌍으로 생성

## 4. `service/` vs `infra/` 구분 기준

| 구분 | `service/` | `infra/` |
|------|-----------|----------|
| 역할 | 도메인 유스케이스 처리 | 외부 시스템 추상화 |
| 호출자 | Controller | Service |
| 예시 | "주문 생성", "결제 처리" | "AI한테 물어봐", "메시지 보내" |
| 도메인 지식 | 있음 (비즈니스 규칙 판단) | 없음 (시킨 대로 실행) |

## 5. 인터페이스 분리 원칙

| 레이어 | 패턴 | 이유 |
|--------|------|------|
| `service/` | 단일 클래스 (인터페이스 없음) | 구현이 하나뿐, Profile 분기 불필요 |
| `infra/` | interface + 구현체 + Mock | Profile별 구현체 교체 필요 |

## 6. `client/` 패키지 규칙

- 모든 예외를 공통 Exception으로 래핑 (try-catch 필수)
- `request/`, `response/` 서브패키지에 외부 API 스펙 그대로 매핑한 record DTO
- 로깅: 요청 시작(debug), 성공(debug), 실패(error)

## 7. `infra/` 패키지 규칙

- 인터페이스 + 구현체 + Mock 구조 유지
- Mock은 `@Profile("local")`, 실제 구현체는 `@Profile("!local")`
- `infra/`는 도메인 규칙을 모름 — 시킨 대로 외부 시스템과 통신할 뿐
- 도메인 판단은 `service/`에서 수행
