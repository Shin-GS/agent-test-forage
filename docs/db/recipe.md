---
status: draft
last-updated: 2026-08-28
ref: docs/specs/recipe/structure.md, docs/specs/recipe/authoring.md, docs/specs/recipe/versioning.md
---

# 레시피 도메인 DB 설계

레시피 정의/버전 관리.

## 설계 방침

- **스텝/변수/결과정의는 JSON 컬럼**으로 저장 (정규화 X)
  - 스텝 타입 4종(API/스크립트/서브레시피/사용자입력)이 구조가 제각각 → 정규화하면 컬럼 난잡
  - 버전 스냅샷이 JSON이면 단순
  - 스텝 내부의 API 참조는 JSON 안에 `endpointId` 필드로 보관 → 유효성 검증 시 파싱하여 체크
- 조회/필터가 필요한 메타(name, visibility, service)만 컬럼으로

## 테이블 개요

| 테이블 | 역할 |
|--------|------|
| `RECIPE` | 레시피 정의 (메타 + 스텝 JSON) |
| `RECIPE_VERSION` | 수정 이력 스냅샷 (복원용) |

---

## RECIPE

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | |
| `OWNER_USER_ID` | BIGINT FK | 작성자 (개인/공통 모두 작성자 기록). 공통 여부는 VISIBILITY로 판단 |
| `API_SPEC_ID` | BIGINT FK | 대상 서비스(스펙) |
| `NAME` | VARCHAR(100) | 레시피명 (AI 매칭용) |
| `DESCRIPTION` | VARCHAR(1000) | 설명 (AI 매칭용) |
| `VISIBILITY` | VARCHAR(20) | COMMON(공통) / PRIVATE(개인) |
| `TAGS` | JSON | 분류/검색 태그 배열 |
| `VARIABLES_JSON` | JSON | 사용자 입력 변수 정의 (②) |
| `STEPS_JSON` | LONGTEXT | 스텝 목록 (③) — 타입/매핑/조건/extract 포함 |
| `RESULT_DEFINITION_JSON` | JSON | 결과 정의 (④) |
| `RESULT_TEMPLATE` | TEXT | 결과 메시지 템플릿 (⑤). 없으면 AI 요약 |
| `CURRENT_VERSION` | INT | 현재 버전 번호 |
| `VALIDATION_STATUS` | VARCHAR(20) | VALID / INVALID / UNVALIDATED |
| `VALIDATION_MESSAGE` | VARCHAR(1000) | 검증 실패 상세 |
| `USAGE_COUNT` | INT | 사용 횟수 (정렬용) |
| `LAST_USED_AT` | DATETIME NULL | 마지막 사용 |
| `DELETED_AT` | DATETIME NULL | 소프트 삭제 |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**
- `IDX_RECIPE_SPEC` : (`API_SPEC_ID`) — 서비스별 레시피 조회
- `IDX_RECIPE_OWNER` : (`OWNER_USER_ID`)
- `IDX_RECIPE_VISIBILITY` : (`VISIBILITY`)

### 스텝 JSON 안의 API 참조

- 스텝(type=api)은 `endpointId`(API_ENDPOINT.ID)를 참조
- 물리 FK는 걸지 않음 (JSON 내부라 불가). **논리 참조**
- 유효성 검증 시: STEPS_JSON 파싱 → endpointId가 존재/ACTIVE인지 체크 → DEPRECATED/삭제면 경고
- 이것이 spec.md에서 "API_ENDPOINT.ID를 PK로 참조, upsert 시 PK 유지"가 필요한 이유

### 서브레시피 참조 / 순환 방지

- 스텝(type=recipe)은 `recipeId`(RECIPE.ID) 참조
- 저장 시 순환 참조 검증 (A→B→A 차단)

---

## RECIPE_VERSION

수정 이력 스냅샷. 복원용 (versioning.md).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | |
| `RECIPE_ID` | BIGINT FK | 대상 레시피 |
| `VERSION_NO` | INT | 버전 번호 |
| `SNAPSHOT_JSON` | LONGTEXT | 해당 버전의 전체 레시피 스냅샷 (메타+스텝+변수+결과) |
| `CREATED_AT` | DATETIME | 생성 시각 (버전 생성 = 수정 시점) |

**인덱스**: `UQ_RECIPE_VERSION` : UNIQUE (`RECIPE_ID`, `VERSION_NO`)

- 레시피 수정 시 이전 상태를 스냅샷으로 저장
- 복원 = 특정 버전 SNAPSHOT_JSON을 RECIPE에 다시 반영 + 새 버전 생성
- 스냅샷이 JSON 통짜라 구현 단순

### 버전 번호 규칙

- `RECIPE.CURRENT_VERSION`은 1부터 시작, 수정 저장마다 +1
- 수정 시: 저장 직전 상태를 `RECIPE_VERSION`에 `VERSION_NO = 현재값`으로 스냅샷 → RECIPE 갱신 → `CURRENT_VERSION += 1`
- 복원 시: 대상 버전을 새 버전으로 다시 커밋 (히스토리 선형 유지, 되돌리기도 이력에 남음)

---

## 확장 고려

- 레시피 공유/승인 프로세스(추후): `RECIPE`에 `APPROVAL_STATUS` 추가로 확장
- 즐겨찾기: `RECIPE_FAVORITE(USER_ID, RECIPE_ID)` 테이블로 확장 (panel.md에 즐겨찾기 있음 → 필요 시 추가)
- 스텝을 나중에 쿼리해야 하면: STEPS_JSON 유지하되 참조 인덱스용 `RECIPE_ENDPOINT_REF(RECIPE_ID, ENDPOINT_ID)` 보조 테이블 추가 가능 (유효성 검증 성능용)
