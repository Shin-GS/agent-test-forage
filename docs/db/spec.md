---
status: draft
last-updated: 2026-08-28
ref: docs/specs/spec/registration.md
---

# 스펙 도메인 DB 설계

외부 서버의 API 스펙 등록/관리 스키마. 라이브러리 등록 요청을 저장하고, 레시피가 참조한다.

> **환경 분리**: ai-test-forge 인스턴스를 dev/stg/prod별로 따로 배포한다. 따라서 DB에 `environment` 구분을 두지 않는다. 식별 키는 `BASE_URL` 단독.

## 테이블 개요

| 테이블 | 역할 |
|--------|------|
| `API_SPEC` | 등록된 외부 서버(스펙) 단위. baseUrl로 식별 |
| `API_SPEC_DOCUMENT` | 원본 OpenAPI JSON 보관 (대용량 분리) |
| `API_ENDPOINT` | 스펙에 속한 개별 API (method+path). 레시피가 참조 |
| `AUTH_PROFILE` | 스펙의 인증 프로필 (name + loginPageUrl) |

- 서비스 메타(description/domain/capabilities/notes), Jira projectKey는 `API_SPEC`에 저장
- 원본 JSON은 조회 성능을 위해 `API_SPEC_DOCUMENT`로 분리, API는 `API_ENDPOINT`로 정규화 분해
- 등록 계약/클라이언트 정보(`SCHEMA_VERSION`/`CLIENT_LANG`/`CLIENT_VERSION`)는 진단용으로 보관 (여러 언어·버전 라이브러리 추적)

## 공통 audit 컬럼

모든 테이블은 아래를 가진다 (JPA `@MappedSuperclass BaseEntity`로 공통화 권장).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `CREATED_AT` | DATETIME | 생성 시각 |
| `UPDATED_AT` | DATETIME | 갱신 시각 |

---

## API_SPEC

외부 서버 1개 = 1행. **식별 키: `BASE_URL`** (UNIQUE). name은 표시용(중복 허용).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | 스펙 ID |
| `NAME` | VARCHAR(100) | 표시용 서비스 이름 (중복 허용, 사용자/AI 노출) |
| `BASE_URL` | VARCHAR(500) | 서버 도메인. 식별 키 (UNIQUE) |
| `STATUS` | VARCHAR(20) | ACTIVE / STALE / INACTIVE |
| `SPEC_HASH` | CHAR(64) | 정규화 후 SHA-256 (변경 감지) |
| `SERVICE_DESCRIPTION` | VARCHAR(500) | 서비스 설명 (관리자 우선) |
| `SERVICE_DOMAIN` | VARCHAR(100) | 도메인 영역 |
| `SERVICE_CAPABILITIES` | TEXT | 기능 키워드 배열 (JSON 문자열로 저장, H2/MySQL 호환) |
| `SERVICE_NOTES` | VARCHAR(500) | 주의사항 |
| `JIRA_PROJECT_KEY` | VARCHAR(50) | 정보 조회용 Jira 프로젝트 키 |
| `CLIENT_LANG` | VARCHAR(20) | 등록한 라이브러리 언어 (진단용, 예: java) |
| `CLIENT_VERSION` | VARCHAR(20) | 등록한 라이브러리 버전 (진단용, 예: 0.0.1) |
| `SCHEMA_VERSION` | VARCHAR(10) | 마지막 등록에 사용된 계약 버전 (진단용) |
| `IS_ADMIN_EDITED` | BOOLEAN | 관리자가 메타를 수정했는지 (yml 덮어쓰기 방지) |
| `YML_META_HASH` | CHAR(64) | yml에서 온 메타 해시 (yml 변경 감지용) |
| `LAST_HEARTBEAT_AT` | DATETIME | 마지막 heartbeat 시각 |
| `DELETED_AT` | DATETIME NULL | 소프트 삭제 (NULL이면 유효) |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**
- `UQ_API_SPEC_BASE_URL` : UNIQUE (`BASE_URL`) — 식별 키, 동시 최초등록 중복 방지
- `IDX_API_SPEC_STATUS` : (`STATUS`)

### 상태 (STATUS)

| 상태 | 의미 | 전이 |
|------|------|------|
| ACTIVE | 정상 (heartbeat 유지) | 기본 |
| STALE | 5분 이상 heartbeat 없음 | heartbeat 재수신 시 ACTIVE 복귀 |
| INACTIVE | 관리자가 수동 비활성 | 관리자만 ACTIVE로 복귀 |

- 24시간 이상 heartbeat 없음 → 소프트 삭제 (`DELETED_AT`). **INACTIVE는 자동 삭제 제외** (초기 버전 기준, 공격적 삭제 방지)
- INACTIVE/삭제 스펙은 AI 매칭/실행 제외. 참조 레시피는 유효성 검증에서 경고
- 프로토타입에 REGISTERING(비동기 파싱) 상태 없음 (동기 처리)

### 서비스 메타 병합 (관리자 우선)

- 최초 등록: yml 값으로 채움, `IS_ADMIN_EDITED = false`, `YML_META_HASH` 저장
- 관리자 수정: 해당 메타 갱신, `IS_ADMIN_EDITED = true`
- 재등록 시 yml 메타 해시 변경: `IS_ADMIN_EDITED = true`면 덮어쓰지 않고 "변경 감지"만, false면 yml로 갱신

---

## API_SPEC_DOCUMENT

원본 OpenAPI JSON 보관. `API_SPEC`과 1:1. 대용량이라 분리 (스펙 목록 조회 시 JSON 미로딩).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | |
| `API_SPEC_ID` | BIGINT FK UNIQUE | 소속 스펙 (1:1) |
| `SPEC_JSON` | LONGTEXT | 원본 OpenAPI JSON |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**: `UQ_API_SPEC_DOCUMENT_SPEC` : UNIQUE (`API_SPEC_ID`)

---

## API_ENDPOINT

스펙에 속한 개별 API. **레시피 스텝이 이 테이블의 `ID`(PK)로 참조** → 재등록 upsert 시 PK 유지 필수.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | 엔드포인트 ID (레시피 참조 대상) |
| `API_SPEC_ID` | BIGINT FK | 소속 스펙 |
| `HTTP_METHOD` | VARCHAR(10) | GET/POST/PUT/PATCH/DELETE |
| `PATH` | VARCHAR(500) | 경로 (예: /api/v1/users) |
| `OPERATION_JSON` | TEXT | 해당 API의 요청/응답 스키마 (OpenAPI operation, JSON 문자열, H2/MySQL 호환) |
| `SUMMARY` | VARCHAR(500) | API 설명 (매칭 힌트) |
| `IS_EXCLUDED` | BOOLEAN | @TestForgeExclude (목록 제외) |
| `IS_CONFIRM_REQUIRED` | BOOLEAN | @TestForgeConfirm (실행 전 확인) |
| `CONFIRM_MESSAGE` | VARCHAR(500) | 확인 메시지 |
| `STATUS` | VARCHAR(20) | ACTIVE / DEPRECATED (스펙에서 사라짐) |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**
- `UQ_API_ENDPOINT_SPEC_METHOD_PATH` : UNIQUE (`API_SPEC_ID`, `HTTP_METHOD`, `PATH`) — upsert 키
- `IDX_API_ENDPOINT_SPEC` : (`API_SPEC_ID`)

### 재등록 upsert

키 `(API_SPEC_ID, HTTP_METHOD, PATH)` 기준:

| 상황 | 처리 |
|------|------|
| 기존에 있음 | OPERATION_JSON/summary/어노테이션 갱신 (**ID 유지** → 레시피 참조 보존) |
| 신규 | INSERT |
| 스펙에서 사라짐 | `STATUS = DEPRECATED` (물리 삭제 X → 레시피 보호) |

- DEPRECATED API 참조 레시피 → 유효성 검증 경고
- 경로 변경(`/v1/users` → `/v2/users`)은 삭제+신규로 취급 (구 API는 DEPRECATED)

---

## AUTH_PROFILE

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | |
| `API_SPEC_ID` | BIGINT FK | 소속 스펙 |
| `NAME` | VARCHAR(100) | 프로필 이름 (예: 일반/관리자) |
| `LOGIN_PAGE_URL` | VARCHAR(500) | 401/403 시 안내할 로그인 URL |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

- 재등록 시 API_SPEC 기준 전체 재구성 (레시피가 PK 참조 안 하므로 단순 교체 가능)

---

## 등록 API 계약

라이브러리 ↔ server 인터페이스.

### 등록 (전체 스펙)

`POST /api/v1/specs/register`

```json
{
  "schemaVersion": "1",
  "client": { "lang": "java", "version": "0.0.1" },
  "name": "demo-shop",
  "baseUrl": "https://shop-api.example.com",
  "specJson": "{...OpenAPI JSON...}",
  "specHash": "sha256...",
  "serviceInfo": {
    "description": "온라인 쇼핑몰 API",
    "domain": "커머스",
    "capabilities": ["회원가입", "상품등록", "주문"],
    "notes": "스테이징"
  },
  "jira": { "projectKey": "SHOP" },
  "authProfiles": [
    { "name": "일반", "loginPageUrl": "https://.../login" }
  ]
}
```

응답: `200` + `{ "specId": 123, "status": "ACTIVE" }`

### 등록 계약 버저닝

여러 언어/버전의 라이브러리가 하나의 ai-test-forge 서버로 등록한다. 등록 요청 body 구조가 라이브러리 진화에 따라 바뀔 수 있으므로 버전을 명시한다.

| 필드 | 설명 |
|------|------|
| `schemaVersion` | **등록 계약(body 구조) 버전.** 서버가 이 값으로 파싱 로직을 분기 |
| `client.lang` / `client.version` | 어떤 라이브러리가 보냈는지 (진단/호환용) |

- **호환 흡수 주체 = 서버.** 서버가 schemaVersion별 파서를 두어 여러 버전 요청을 내부 표준 모델로 정규화
- 라이브러리는 자기 `schemaVersion`을 정직하게 명시해서 전송
- OpenAPI 스펙 버전(3.0/3.1)은 `specJson` 내부 `openapi` 필드에 이미 존재 → 서버가 그걸로 파싱
- 계약이 하위호환 불가하게 깨지면 API 경로도 승격 (`/api/v2/specs/register`)

### Heartbeat (해시만)

`POST /api/v1/specs/heartbeat`

```json
{
  "schemaVersion": "1",
  "baseUrl": "https://shop-api.example.com",
  "specHash": "sha256..."
}
```

- server가 저장된 해시와 비교
  - 같음 → `LAST_HEARTBEAT_AT` 갱신, `200 { "action": "none" }`
  - 다름 → `200 { "action": "resend" }` → 라이브러리가 `/register` 재호출
  - baseUrl 미등록 → `200 { "action": "resend" }`

### 인증 (등록 보안)

- 공유 시크릿 토큰 헤더 (`X-TestForge-Token`). server 환경변수로 관리
- 토큰 불일치 시 `401`. 아무 서버나 등록 못 하게 방어

---

## specHash 계산 규칙

- OpenAPI JSON을 **정규화(키 정렬, 공백 제거) 후** SHA-256
- 서버 재빌드 시 필드 순서/포맷만 바뀌어도 해시가 달라지는 것 방지
- **serviceInfo/jira/authProfiles는 해시 미포함** (스펙 본문만). 메타 변경은 `YML_META_HASH`로 별도 감지

---

## 다중 인스턴스 / 동시성

| 상황 | 처리 |
|------|------|
| 서버 N대가 각각 heartbeat | 모두 `baseUrl` 동일 → 같은 스펙 갱신. specHash 같으면 no-op |
| 동시 최초 등록 (경합) | `UQ_API_SPEC_BASE_URL` 제약으로 1건만 성공, 나머지 upsert 흡수 |
| baseUrl 변경 (서버 URL 교체) | 구 URL 스펙은 heartbeat 끊겨 STALE → 관리자가 수동 INACTIVE/삭제 |

---

## 미결정 / 추후

- 레시피↔API 참조 FK를 물리 FK로 걸지, 논리 참조만 할지 (레시피 스키마 설계 시 확정)
- DEPRECATED API 자동 정리 정책 (현재 수동)
- 변경 이력(diff) 기록 테이블 (발표 어필용, 추후)
