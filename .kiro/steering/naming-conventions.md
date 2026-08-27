---
sourceId: naming-conventions
sourceVersion: "1.0"
sourceUpdatedAt: 2026-07-01
inclusion: always
---

# 네이밍 컨벤션

## 목적

프로젝트 전반에서 일관된 네이밍을 보장한다.
코드 내 변수명(언어별 rule에서 다룸)이 아니라, **구조적 네이밍**(API, DB, 파일 등)에 집중.

---

## API 경로

### 규칙

| 항목 | 컨벤션 | 예시 |
|------|--------|------|
| 리소스명 | **복수형** | `/api/v1/users`, `/api/v1/orders` |
| 케이스 | **kebab-case** | `/api/v1/user-profiles` (camelCase 금지) |
| 버전 | URL 프리픽스 | `/api/v1/...`, `/api/v2/...` |
| 계층 관계 | 중첩 경로 | `/api/v1/users/{id}/orders` |
| 액션 (CRUD 외) | 동사 허용 | `/api/v1/orders/{id}/cancel` |
| ID 파라미터 | `{리소스명 단수}Id` 또는 `{id}` | `/users/{userId}` 또는 `/users/{id}` |

### HTTP 메서드 매핑

| 동작 | 메서드 | 경로 패턴 |
|------|--------|-----------|
| 목록 조회 | GET | `/resources` |
| 단건 조회 | GET | `/resources/{id}` |
| 생성 | POST | `/resources` |
| 전체 수정 | PUT | `/resources/{id}` |
| 부분 수정 | PATCH | `/resources/{id}` |
| 삭제 | DELETE | `/resources/{id}` |

### 피해야 할 패턴

- `/api/v1/getUser` — 동사를 경로에 넣지 않음 (HTTP 메서드가 동사 역할)
- `/api/v1/user` — 단수형 금지 (컬렉션은 복수형)
- `/api/v1/user_profiles` — 언더스코어 금지 (kebab-case 사용)
- 쿼리 파라미터에 복잡한 로직: `/api/v1/users?action=delete` — 메서드로 표현

---

## DB 스키마

### 테이블명

| 항목 | 컨벤션 | 예시 |
|------|--------|------|
| 케이스 | **UPPER_SNAKE_CASE** | `USER_STATUS`, `ORDER_ITEM` |
| 단수/복수 | **단수형** | `USER` (users ❌) |
| 조인 테이블 | 양쪽 테이블명 결합 | `USER_ROLE`, `ORDER_PRODUCT` |

### 컬럼명

| 항목 | 컨벤션 | 예시 |
|------|--------|------|
| 케이스 | **UPPER_SNAKE_CASE** | `USER_ID`, `CREATED_AT` |
| PK | `ID` | 모든 테이블 PK는 `ID` |
| FK | `{참조테이블}_ID` | `USER_ID`, `ORDER_ID` |
| 시간 | `*_AT` | `CREATED_AT`, `UPDATED_AT`, `DELETED_AT` |
| 불리언 | `IS_*` 또는 `*_YN` | `IS_ACTIVE`, `IS_DELETED` |
| 상태 | `STATUS` | `STATUS` (ENUM 코드값 저장) |
| 금액 | 단위 포함 | `AMOUNT_TON`, `PRICE_USD` (또는 통화 컬럼 분리) |

### 인덱스 네이밍

| 항목 | 컨벤션 | 예시 |
|------|--------|------|
| 일반 인덱스 | `IDX_{테이블}_{컬럼}` | `IDX_USER_EMAIL` |
| 유니크 인덱스 | `UQ_{테이블}_{컬럼}` | `UQ_USER_EMAIL` |
| 복합 인덱스 | `IDX_{테이블}_{컬럼1}_{컬럼2}` | `IDX_ORDER_USER_ID_STATUS` |

---

## 파일/디렉토리명

| 대상 | 컨벤션 | 예시 |
|------|--------|------|
| 디렉토리 | **kebab-case** | `user-management/`, `order-service/` |
| 설정 파일 | **kebab-case** | `docker-compose.yml`, `eslint.config.js` |
| 문서 | **kebab-case** | `business-logic.md`, `api-spec.md` |
| Java 클래스 파일 | **PascalCase** | `UserService.java`, `OrderController.java` |
| TS/JS 컴포넌트 | **PascalCase** | `UserCard.tsx`, `OrderList.tsx` |
| TS/JS 유틸/훅 | **camelCase** | `useAuth.ts`, `formatDate.ts` |
| CSS/스타일 | **kebab-case** | `tokens.css`, `base.css` |
| 테스트 파일 | 원본 + `.test`/`.spec` | `UserService.test.java`, `useAuth.test.ts` |

---

## 범용 원칙 (모든 곳에 적용)

| 원칙 | 설명 |
|------|------|
| 약어는 3글자 이하만 | `id`, `url`, `dto`, `api` OK. `usr`, `mgr`, `cfg` 지양 |
| 풀네임 선호 | `button` > `btn`, `message` > `msg` (코드 내 지역변수는 예외 허용) |
| 일관성 > 최적 | 프로젝트 내에서 한 가지 방식으로 통일. 혼용이 최악 |
| 의미 전달 우선 | 짧은 것보다 명확한 것. `userRegistrationService` > `regSvc` |

---

## 프로젝트 시작 시

이 규칙을 기본으로 적용한다. 프로젝트별로 다르게 해야 할 경우에만 해당 프로젝트의 tech.md에서 override.
