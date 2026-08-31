---
status: draft
last-updated: 2026-08-28
ref: docs/specs/recipe/execution.md, docs/specs/recipe/plan.md, docs/specs/panel/history.md, docs/specs/pages/history-full.md
---

# 실행/히스토리 도메인 DB 설계

레시피/플랜 실행 이력. 스텝별 결과를 서버에 저장하여 "이어서 실행", 히스토리 조회 지원.

## 설계 방침

- **실행은 정규화** — 스텝별 상태/결과를 조회·재개해야 하므로 (execution.md: "스텝별 상태 저장 → 이어서 실행")
- **모든 실행은 내부적으로 플랜** (plan.md: "단일 레시피 = 레시피 1개짜리 플랜"). 표시만 구분
- **히스토리는 대화와 독립** (history.md: "대화방 삭제해도 히스토리 유지") → 대화 FK를 NULL 허용 + soft 참조
- context(extract 변수)는 재개에 필요 → 저장
- **정보 조회(investigate)는 실행이 아님** → 여기 저장하지 않음. 1회성 조회이며 결과는 채팅 메시지로만 남음 (investigation.md)

## 테이블 개요

| 테이블 | 역할 |
|--------|------|
| `EXECUTION` | 1회 실행 (플랜 단위). 단일 레시피도 여기 1건 |
| `EXECUTION_RECIPE` | 플랜 내 레시피별 실행 (플랜=N, 단일=1) |
| `EXECUTION_STEP` | 레시피 내 스텝별 실행 결과 |

계층: `EXECUTION` 1:N `EXECUTION_RECIPE` 1:N `EXECUTION_STEP`

---

## EXECUTION

1회 실행 = 1행 (플랜 = 레시피 여러 개 묶음, 단일 = 레시피 1개).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | |
| `USER_ID` | BIGINT FK | 실행한 사용자 |
| `CONVERSATION_ID` | BIGINT FK NULL | 실행된 대화방. **대화 삭제 시 NULL로 (히스토리 독립 유지)** |
| `API_SPEC_ID` | BIGINT FK NULL | 대상 서비스 (참조용, 스펙 삭제 대비 NULL 허용) |
| `TYPE` | VARCHAR(20) | SINGLE(단일 레시피) / PLAN(복합) |
| `TITLE` | VARCHAR(200) | 표시명 (예: "회원가입 × 5", "플랜: 입사지원") |
| `MODE` | VARCHAR(20) | AUTO(자동) / MANUAL(직접 입력). 플랜(TYPE=PLAN)은 항상 AUTO (plan.md: 승인 후 자동 진행) |
| `STATUS` | VARCHAR(20) | RUNNING / SUCCESS / PARTIAL / FAILED / STOPPED |
| `CONTEXT_JSON` | LONGTEXT | 실행 전역 context (extract 변수 누적). 이어서 실행에 사용 |
| `RESULT_SUMMARY` | TEXT | 결과 요약 (히스토리 표시용) |
| `STARTED_AT` | DATETIME | 시작 |
| `FINISHED_AT` | DATETIME NULL | 종료 (RUNNING이면 NULL) |
| `DURATION_MS` | BIGINT NULL | 소요 시간 |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**
- `IDX_EXECUTION_USER` : (`USER_ID`)
- `IDX_EXECUTION_CONVERSATION` : (`CONVERSATION_ID`)
- `IDX_EXECUTION_STARTED` : (`STARTED_AT`) — 히스토리 최신순

### 상태 (STATUS)

| 상태 | 의미 |
|------|------|
| RUNNING | 실행 중 |
| SUCCESS | 전체 성공 |
| PARTIAL | 부분 성공/실패 (일부 스텝 실패, 중지) |
| FAILED | 실패로 중단 |
| STOPPED | 사용자 중지 (이어서 실행 가능) |

---

## EXECUTION_RECIPE

플랜 내 레시피별 실행. 단일 실행이면 1건.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | |
| `EXECUTION_ID` | BIGINT FK | 소속 실행 |
| `RECIPE_ID` | BIGINT FK NULL | 원본 레시피 링크 (삭제 대비 NULL 허용) |
| `RECIPE_NAME` | VARCHAR(100) | 실행 시점 레시피명 (스냅샷) |
| `RECIPE_VERSION_NO` | INT NULL | 실행 시점 레시피 버전 번호 (참고용) |
| `RECIPE_SNAPSHOT_JSON` | LONGTEXT | **실행 시점 레시피 전체 스냅샷** (메타+스텝+변수+결과정의). 원본이 바뀌거나 삭제돼도 히스토리 재현 가능 |
| `SEQUENCE` | INT | 플랜 내 순서 |
| `STATUS` | VARCHAR(20) | PENDING / RUNNING / SUCCESS / SKIPPED / FAILED / STOPPED |
| `RESULT_VALUES_JSON` | JSON | 이 레시피의 결과 정의 값 (다음 레시피 입력/템플릿용) |
| `STARTED_AT` / `FINISHED_AT` | DATETIME NULL | |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**: `IDX_EXECUTION_RECIPE_EXEC` : (`EXECUTION_ID`)

### 레시피 스냅샷 (히스토리 재현)

- 실행 시작 시 **현재 레시피를 통째로 `RECIPE_SNAPSHOT_JSON`에 복사** 저장
- 히스토리 상세는 이 스냅샷 기준으로 렌더링 → 원본 레시피가 수정/삭제돼도 "그때 그 실행"을 정확히 재현
- `RECIPE_ID`는 원본 링크용 (NULL 허용, 삭제 대비). `RECIPE_VERSION_NO`는 참고용
- 목적 구분:
  - `RECIPE_VERSION` (recipe.md) = 레시피 편집 이력/복원용
  - `EXECUTION_RECIPE.RECIPE_SNAPSHOT_JSON` = 실행 감사/히스토리 재현용 (실행과 완전 독립)

---

## EXECUTION_STEP

레시피 내 스텝별 실행 결과. **이어서 실행/히스토리 상세의 핵심.**

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | |
| `EXECUTION_RECIPE_ID` | BIGINT FK | 소속 레시피 실행 |
| `STEP_INDEX` | INT | 스텝 순서 |
| `STEP_NAME` | VARCHAR(200) | 스텝명 (스냅샷) |
| `STEP_TYPE` | VARCHAR(20) | API / SCRIPT / RECIPE / USER_INPUT |
| `STATUS` | VARCHAR(20) | PENDING / SUCCESS / FAILED / SKIPPED |
| `SUMMARY` | VARCHAR(500) | 결과 한 줄 요약 (진행 표시용, 30자 내외) |
| `USER_INPUT_JSON` | JSON NULL | 사용자 입력값 (history-full.md: 입력값 표시) |
| `RESPONSE_JSON` | LONGTEXT NULL | 원시 응답 (1MB 초과 시 잘라 저장) |
| `ERROR_MESSAGE` | VARCHAR(1000) NULL | 실패 시 에러 |
| `STARTED_AT` / `FINISHED_AT` | DATETIME NULL | |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**: `IDX_EXECUTION_STEP_RECIPE` : (`EXECUTION_RECIPE_ID`, `STEP_INDEX`)

### 저장 정책 (execution.md 반영)

| 저장 대상 | 위치 |
|-----------|------|
| 실행 시점 레시피 스냅샷 | EXECUTION_RECIPE.RECIPE_SNAPSHOT_JSON |
| extract 변수 (context) | EXECUTION.CONTEXT_JSON |
| 스텝 상태 (성공/실패/스킵) | EXECUTION_STEP.STATUS |
| 에러 메시지 | EXECUTION_STEP.ERROR_MESSAGE |
| 사용자 입력값 | EXECUTION_STEP.USER_INPUT_JSON |
| 원시 응답 (1MB 초과 시 절단) | EXECUTION_STEP.RESPONSE_JSON |

- 민감 데이터(토큰/비번/개인정보) 마스킹은 추후 (error-handling.md 참조)

### 이어서 실행

- STOPPED/FAILED 실행에서 마지막 SUCCESS 스텝 다음부터 재개
- CONTEXT_JSON에 누적된 변수로 이어서 실행 (서버가 내려줌)
- 프로토타입: "처음부터 다시"만 제공, 이어서 실행은 스키마는 준비하되 구현 추후 가능

---

## 히스토리 독립성 (history.md)

- 대화방 삭제 시 `EXECUTION.CONVERSATION_ID = NULL` (실행 기록은 유지)
- 히스토리 조회는 `USER_ID` 기준 (대화 무관)
- 플랜 히스토리: `EXECUTION(TYPE=PLAN)` + `EXECUTION_RECIPE` 펼쳐서 표시

---

## 확장 고려

- 실행 재시도 이력: EXECUTION에 `PARENT_EXECUTION_ID` 추가로 "재실행 체인" 추적 가능 (추후)
- 민감 데이터 마스킹 정책: EXECUTION_STEP 저장 시 필터 (추후)
- 대량 실행(× N건) 시 EXECUTION_STEP 폭증 → 파티셔닝/아카이빙 (추후)
