---
status: draft
last-updated: 2026-09-16
ref: docs/specs/recipe/structure.md, docs/specs/recipe/authoring.md, docs/specs/recipe/execution.md, docs/specs/recipe/versioning.md
---

<!-- 2026-09-16: 스텝별 apiSpecId(멀티 서비스) 반영 — STEPS_JSON API 스텝에 apiSpecId 정식 1급 필드 + 구 데이터 폴백 명시 -->
<!-- 2026-09-16: 정식 구현 확정 — 로드 시 조용한 폴백 대신 일회성 마이그레이션(엔드포인트)으로 apiSpecId 확정. 보정 대상=RECIPE.STEPS_JSON + RECIPE_VERSION.SNAPSHOT_JSON, 실행 히스토리(EXECUTION_RECIPE.RECIPE_SNAPSHOT_JSON) 제외 -->
<!-- 2026-09-16: 리뷰 반영(H2/M2) — 저장 시 apiSpecId 확정 주체=FE 명시(서버 통짜 저장+RecipeValidator 검증), 마이그레이션 역산 실패 스텝 보유 레시피는 VALIDATION_STATUS=INVALID 마킹 -->
<!-- 2026-09-16: 문서 재검증 반영 — RECIPE_VERSION 스냅샷엔 resolvedSteps 없음(복원 후 실행 시작 시 BE가 생성) 명시 -->

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
| `STEPS_JSON` | LONGTEXT | 스텝 목록 (③) — 타입/매핑/조건/extract + 스텝 표시명(`label`, 선택) + 요청 헤더 매핑/레시피별 기본값 포함. API 스텝은 `apiSpecId`(대상 서비스) + `endpointId`를 1급 필드로 보관(멀티 서비스) |
| `RESULT_DEFINITION_JSON` | JSON | 결과 정의 (④) — 각 항목 `{ key, label(선택), source }` |
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

### 표시명(label) 저장

비개발자용 사람말 표기를 위해 스텝/결과정의 JSON에 표시명을 보관한다. 폴백 규칙 정의: [structure.md 표시명 폴백 체인](../specs/recipe/structure.md#표시명label-폴백-체인).

- **스텝 표시명**: `STEPS_JSON`의 각 스텝에 `label`(선택) 필드. 비면 실행/표시 시 폴백((1) label → (2) 엔드포인트 summary → (3) method+path). summary는 스펙 원천이라 저장하지 않고 참조 시점에 조회.
- **결과키 표시명**: `RESULT_DEFINITION_JSON`의 각 항목이 `{ key, label(선택), source }`. label 비면 원본 key로 폴백.
- **스냅샷 포함**: 스텝 `label`과 결과정의 `label`은 `RECIPE_VERSION.SNAPSHOT_JSON`에 그대로 포함되어, 히스토리 재현 시 그때 그 이름이 유지된다(스펙 summary가 이후 바뀌어도 과거 실행 표기는 스냅샷 기준으로 고정).

### 스텝 JSON 안의 API 참조

- 스텝(type=api)은 `apiSpecId`(API_SPEC.ID)와 `endpointId`(API_ENDPOINT.ID)를 참조
- 물리 FK는 걸지 않음 (JSON 내부라 불가). **논리 참조**
- 유효성 검증 시: STEPS_JSON 파싱 → **스텝의 `apiSpecId` 기준으로 `endpointId`가 그 서비스에 존재/ACTIVE인지** 체크 → 소속 아님/없음/DEPRECATED/삭제/INACTIVE면 경고
- 이것이 spec.md에서 "API_ENDPOINT.ID를 PK로 참조, upsert 시 PK 유지"가 필요한 이유

### API 스텝 JSON: apiSpecId 정식 필드 (멀티 서비스)

API 스텝(type=api)은 **호출 대상 서비스를 스텝별로 지정**한다. `apiSpecId`를 `endpointId`와 함께 **1급 필드**로 저장해, 한 레시피의 스텝들이 서로 다른 서비스를 호출할 수 있다(멀티 서비스). 정본: [structure.md 멀티 서비스 실행 규칙](../specs/recipe/structure.md#멀티-서비스-실행-규칙-확정), [execution.md 스텝별 baseUrl/endpoint 해석](../specs/recipe/execution.md#스텝별-baseurlendpoint-해석-멀티-서비스).

- **필드**: API 스텝 JSON은 `apiSpecId`(대상 서비스 = API_SPEC.ID) + `endpointId`(그 서비스의 API_ENDPOINT.ID)를 함께 담는다. `path`는 endpoint 원본 그대로(예: `/seats/{seatId}/bookings`) 보관하고 실행 시 `pathParams`로 치환한다(원본 path를 미리 치환해 저장하지 않음).
- **저장 시 명시 확정 (주체 = FE)**: 편집 중에는 `apiSpecId` 미선택(레시피 대상 서비스 상속)을 허용하되, **FE가 저장 요청을 직렬화할 때 미선택 스텝을 레시피 대상 `RECIPE.API_SPEC_ID`로 확정해 채워 전송**한다. 서버로 올라오는 STEPS_JSON에는 `apiSpecId` 없는 API 스텝이 남지 않는다. 서버는 STEPS_JSON을 **통짜로 저장**하고 파싱은 검증용으로만 쓰되(원칙 유지), FE 값을 무조건 신뢰하진 않는다 — **RecipeValidator가 스텝 `apiSpecId` 기준으로 `endpointId` 소속을 검증**해 잘못된 값을 걸러 신뢰 경계를 확보한다(상속 UX + 저장 시 명시 확정, 정본: [structure.md 스텝 서비스 지정 모델](../specs/recipe/structure.md#스텝-서비스-지정-모델-상속-ux--저장-시-명시-확정--확정)).
- **구 데이터(정식 구현)**: 배포 전이라 로드 시 조용한 폴백에 의존하지 않는다. `apiSpecId` 없는 구 스텝 데이터는 아래 [마이그레이션](#apispecid-역산-마이그레이션-일회성)으로 `apiSpecId`를 확정 기록했다(**2026-09-17 실행 완료**).
- **경계 검증**: 검증은 위 "스텝 JSON 안의 API 참조"대로 스텝의 `apiSpecId`로 경계 지어 endpoint 소속/존재/ACTIVE를 확인한다(전역 존재 확인만 하던 무경계 상태를 스텝 서비스 기준으로 좁힘).
- **스냅샷**: 실행 시 이 `apiSpecId`/`endpointId`를 근거로 `EXECUTION_RECIPE.RECIPE_SNAPSHOT_JSON`의 `services`/`resolvedSteps`(실행용 확정 뷰)를 만든다([db/execution.md RECIPE_SNAPSHOT_JSON](execution.md#레시피-스냅샷-히스토리-재현)). 서비스를 해석하지 못하면 조용히 대체하지 않고 스텝을 실패 처리한다([execution.md 스텝 서비스 못 찾음](../specs/recipe/execution.md#스텝-서비스스펙-못-찾음-실패-처리)).

#### apiSpecId 역산 마이그레이션 (일회성)

> ✅ **2026-09-17 실행 완료** — `processedRecipes=51, patchedSteps=242, failedSteps=0`. 실행·검증 후 일회성 엔드포인트/서비스 코드는 **제거**했다(아래 내용은 이력·근거 보존용). `endpointId`가 속한 서비스를 역산해 API 스텝에 `apiSpecId`를 확정 기록했다.

배포 전 기존 데이터에 `apiSpecId`를 채우기 위한 **일회성 관리자 엔드포인트**였다.

| 항목 | 내용 |
|------|------|
| 엔드포인트 | `POST /api/v1/admin/migrations/step-api-spec-id` (제거됨) |
| 권한 | **ADMIN** 전용 |
| 멱등성 | idempotent — 이미 `apiSpecId`가 있는 API 스텝은 **skip**(재실행해도 안전) |
| 역산 방식 | 스텝의 `endpointId` → `API_ENDPOINT`가 속한 `API_SPEC.ID`를 조회해 `apiSpecId`로 기록 |
| 보정 대상 | **`RECIPE.STEPS_JSON`** + **`RECIPE_VERSION.SNAPSHOT_JSON` 내부 stepsJson** (레시피 계열 둘 다) |
| 제외 대상 | **`EXECUTION_RECIPE.RECIPE_SNAPSHOT_JSON`(실행 히스토리)** — "그때 그대로" 불변 원칙 + `resolvedSteps`/`services`는 과거엔 없던 개념이라 **소급 주입하지 않음** |

- **RECIPE_VERSION 스냅샷의 범위**: `RECIPE_VERSION.SNAPSHOT_JSON`은 편집 이력이라 stepsJson(+메타)만 담고 **`services`/`resolvedSteps`는 없다**(그건 실행 스냅샷 개념). 마이그레이션은 그 안 stepsJson의 `apiSpecId`만 백필한다. 과거 버전을 복원하면 stepsJson이 현재 레시피로 반영되고, **실행 시작 시 BE가 `resolvedSteps`를 새로 생성**하므로 문제되지 않는다.
| 운영 | 실행 전 **DB 백업 권장**. 완료 후 엔드포인트는 **제거**하는 임시(one-off) 성격 |

- **역산 실패 처리**: `endpointId`로 서비스를 역산하지 못하는 스텝(엔드포인트 삭제 등)은 건너뛰고 로그에 남긴다(마이그레이션이 전체 실패하지 않도록). 이런 스텝이 **하나라도 있는 레시피는 `RECIPE.VALIDATION_STATUS=INVALID`로 마킹**한다(`VALIDATION_MESSAGE`에 사유 기록). 실행 진입 전 유효성 경고로 걸러져 엉뚱한 실행을 막는다. 멱등성(이미 `apiSpecId` 있는 스텝은 skip)은 그대로 유지된다.

### API 스텝 JSON: 요청 헤더 매핑 / 레시피별 기본값 (2단계)

API 스텝(type=api)의 JSON은 요청 바디·경로 파라미터에 더해 **요청 헤더 매핑**과 **레시피별 기본값**을 담는다. 서버는 STEPS_JSON을 통짜로 저장하므로(파싱은 유효성 검증용 endpointId/recipeId만), FE가 직렬화한 아래 키가 그대로 보관된다. 정본: [execution.md 요청 헤더 주입](../specs/recipe/execution.md), [authoring.md 요청 필드/헤더 매핑](../specs/recipe/authoring.md).

- **매핑 값 맵**(기존 구조 유지, 하위 호환): `body`(요청 필드), `pathParams`(경로 파라미터), `headers`(요청 헤더)는 각각 `{ 필드명: 값 }` 객체 맵. 값은 `source`가 인코딩된 문자열(리터럴/`userInput.x`/`stepN.x`/AI 생성 지시).
- **기본값 맵**(2단계 신설, 선택): `bodyDefaults`/`pathParamDefaults`/`headerDefaults`는 각각 `{ 필드명: 기본값 }` 객체 맵. **기본값이 있는 필드만** 포함하며, 하나도 없으면 키 자체를 생략한다(구 레시피와 동일한 출력 → 하위 호환 보장).
- **우선순위**: 실행 시 `사용자 실행 입력 > 레시피별 기본값`. (API별 기본값은 범위 밖 — 추후 최하층 확장 여지)
- **민감 헤더**: 인증 key 등은 값 소스를 `userInput`으로 두는 것을 권장. 직접 입력한 고정값은 평문으로 STEPS_JSON에 저장된다(회사 내부용 전제).

### 스냅샷 포함 (RECIPE_VERSION)

- 위 매핑 값 맵(`headers` 등)과 기본값 맵(`headerDefaults` 등)은 STEPS_JSON의 일부이므로 **`RECIPE_VERSION.SNAPSHOT_JSON`에 그대로 포함**된다. 레시피 수정/복원 시 그 시점의 헤더 매핑·기본값이 스냅샷 기준으로 고정 재현된다(별도 컬럼/테이블 없음).

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
