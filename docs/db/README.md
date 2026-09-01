# DB 설계

ai-test-forge 데이터베이스 스키마 설계 문서.

## 문서 목록

| 문서 | 도메인 | 주요 테이블 |
|------|--------|------------|
| [spec.md](spec.md) | 스펙(외부 서버) | API_SPEC, API_SPEC_DOCUMENT, API_ENDPOINT, AUTH_PROFILE |
| [user.md](user.md) | 사용자 | APP_USER, USER_SETTING |
| [recipe.md](recipe.md) | 레시피 | RECIPE, RECIPE_VERSION |
| [execution.md](execution.md) | 실행/히스토리 | EXECUTION, EXECUTION_RECIPE, EXECUTION_STEP |
| [conversation.md](conversation.md) | 대화 | CONVERSATION, MESSAGE |

## 도메인 관계 (개략)

```
APP_USER ──┬── USER_SETTING (1:1)
           ├── CONVERSATION ──── MESSAGE (1:N)
           ├── RECIPE ──── RECIPE_VERSION (1:N)
           └── EXECUTION ──── EXECUTION_RECIPE ──── EXECUTION_STEP (1:N:N)

API_SPEC ──┬── API_SPEC_DOCUMENT (1:1)
           ├── API_ENDPOINT (1:N)   ← 레시피 스텝이 논리 참조
           └── AUTH_PROFILE (1:N)

CONVERSATION ──── EXECUTION   (연결 유지, 히스토리는 USER_ID 기준 독립)
RECIPE       ──(스냅샷)──── EXECUTION_RECIPE  (실행 시점 통째 복사)
API_SPEC     ──── RECIPE / CONVERSATION       (대상 서비스)
```

## 설계 원칙

| 원칙 | 적용 |
|------|------|
| JSON 컬럼 | 구조가 자유롭거나 자주 바뀌는 것 (스텝, operation, 스냅샷, 메타데이터) |
| 정규화 | 조회·필터·재개가 필요한 것 (API 엔드포인트, 실행 스텝, 메시지) |
| Soft delete | 모든 삭제는 소프트 삭제 (`DELETED_AT`). FK 연결은 끊지 않음 — row가 남아 무결성 유지 |
| 공통 audit | 모든 테이블 `CREATED_AT`/`UPDATED_AT` (`@MappedSuperclass BaseEntity`) |
| 스냅샷 | 실행 히스토리는 실행 시점 레시피를 통째 저장 (원본 독립) |
| 히스토리 독립 | 대화 삭제해도 실행 기록 유지 — 조회가 `USER_ID` 기준이라 독립 (연결은 유지) |

## 규칙

- 네이밍: `.kiro/steering/naming-conventions.md` 따름
  - 테이블: `UPPER_SNAKE_CASE`, 단수형 (예: `API_SPEC`)
  - 컬럼: `UPPER_SNAKE_CASE`, PK는 `ID`, FK는 `{테이블}_ID`
  - 인덱스: `IDX_*`, 유니크: `UQ_*`
  - 시간: `*_AT`, 불리언: `IS_*`
- DB: MySQL
- Enum: `@Enumerated(STRING)`, 문자열 저장
- 예약어 회피: `USER` → `APP_USER`

## 미결정 / 추후

- 레시피↔API 참조: 논리 참조(JSON 내 endpointId) + 유효성 검증. 성능 필요 시 보조 테이블
- 민감 데이터 마스킹 (실행 결과 저장 시)
- 안 읽음 정확 추적, 대화 요약 캐시, 즐겨찾기 등은 각 문서의 "확장 고려" 참조
- 인덱스는 실제 쿼리 패턴 확인 후 튜닝
