---
status: draft
last-updated: 2026-09-16
ref: docs/specs/recipe/execution.md, docs/specs/recipe/plan.md, docs/specs/panel/history.md, docs/specs/pages/history-full.md, docs/specs/common/messaging.md
---

<!-- 2026-09-16: 스텝별 apiSpecId(멀티 서비스) 반영 — RECIPE_SNAPSHOT_JSON에 services/resolvedSteps 실행 확정 뷰 신설 -->
<!-- 2026-09-16: 정식 구현 확정 — RECIPE_SNAPSHOT_JSON의 services/resolvedSteps 계약 상세 기술(원문 stepsJson 보존 + 실행용 파생 뷰), 실행 히스토리는 마이그레이션 제외 명시 -->
<!-- 2026-09-16: 리뷰 반영(M1) — resolvedSteps에 stepIndex 추가·대응을 endpointId→stepIndex로 변경(스크립트/서브레시피 혼재 시 인덱스 어긋남 방지) -->
<!-- 2026-09-16: 문서 재검증 반영 — services/resolvedSteps 생성 주체=BE(실행 시작 시) + services 키 문자열 apiSpecId 명시 -->

# 실행/히스토리 도메인 DB 설계

레시피/플랜 실행 이력. 스텝별 결과를 서버에 저장하여 "이어서 실행", 히스토리 조회 지원.

## 설계 방침

- **실행은 정규화** — 스텝별 상태/결과를 조회·재개해야 하므로 (execution.md: "스텝별 상태 저장 → 이어서 실행")
- **모든 실행은 내부적으로 플랜** (plan.md: "단일 레시피 = 레시피 1개짜리 플랜"). 표시만 구분
- **히스토리는 대화와 독립** (history.md: "대화방 삭제해도 히스토리 유지") → 히스토리 조회는 `USER_ID` 기준이라 대화 삭제와 무관하게 유지된다. 대화방은 **소프트 삭제**(row 유지)이므로 `CONVERSATION_ID`는 **연결을 그대로 유지**한다(FK를 끊지 않음). 연결을 남겨 "이 실행이 나온 대화" 추적/복구가 가능하다.
- context(extract 변수)는 재개에 필요 → 저장
- **정보 조회(investigate)는 실행이 아님** → EXECUTION 계층에 저장하지 않는다. 대신 형제 계층인 [INVESTIGATION](investigation.md)에 별도 정규 저장한다(질의·소스·스텝·답변 요약 — 분석/감사용).

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
| `CONVERSATION_ID` | BIGINT FK NULL | 실행된 대화방. 대화방은 소프트 삭제라 **연결 유지**(끊지 않음). 히스토리 독립성은 USER_ID 기준 조회로 확보. NULL은 대화 없이 시작된 실행(추후) 대비 |
| `TRIGGER_PART_ID` | BIGINT FK NULL | **실행을 촉발한 MESSAGE_PART** ID (예: execution_mode·plan 카드 파트). 파트-실행 정규 연결(어느 파트에서 나온 실행인지). **한 턴에 실행이 여러 개여도** 각 실행이 자기 촉발 파트를 특정한다(턴 단위 링크로는 구분 불가). 턴은 part→message_id로 유도 — **진행/결과 파트는 이 촉발 파트가 속한 턴에 append되어 `[CARD, PROGRESS, RESULT]`가 한 턴이 된다.** 이 값은 촉발 파트 id로 고정하며 **진행(PROGRESS) 파트 id로 덮어쓰지 않는다**(진행 파트 조회는 `MESSAGE_PART.EXECUTION_ID` 역참조 사용). NULL은 대화 없이 시작된 실행(직접 실행/재개 등) 대비 |
| `API_SPEC_ID` | BIGINT FK NULL | 대상 서비스 (참조용, 스펙 삭제 대비 NULL 허용) |
| `TYPE` | VARCHAR(20) | SINGLE(단일 레시피) / PLAN(복합) |
| `TITLE` | VARCHAR(200) | 표시명 (예: "회원가입 × 5", "플랜: 입사지원") |
| `MODE` | VARCHAR(20) | AUTO(자동) / MANUAL(직접 입력). 플랜(TYPE=PLAN)은 항상 AUTO (plan.md: 승인 후 자동 진행) |
| `STATUS` | VARCHAR(20) | RUNNING / SUCCESS / PARTIAL / FAILED / STOPPED / CANCELLED |
| `CONTEXT_JSON` | LONGTEXT | 실행 전역 context (extract 변수 누적). 이어서 실행에 사용 |
| `RESULT_SUMMARY` | TEXT | 결과 요약 (히스토리 표시용) |
| `STARTED_AT` | DATETIME | 시작 |
| `FINISHED_AT` | DATETIME NULL | 종료 (RUNNING이면 NULL) |
| `DURATION_MS` | BIGINT NULL | 소요 시간 |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**
- `IDX_EXECUTION_USER_ID` : (`USER_ID`, `ID`) — 사용자 히스토리 커서 페이징 (필터 + id 정렬/범위를 인덱스로 커버)
- `IDX_EXECUTION_CONVERSATION_ID` : (`CONVERSATION_ID`, `ID`) — 대화방별 실행 커서 페이징. 대화 진입/새로고침 복원 조회에도 사용
- `IDX_EXECUTION_TRIGGER_PART_ID` : (`TRIGGER_PART_ID`) — 촉발 파트별 실행 조회 (한 턴 다중 실행 구분)
- `IDX_EXECUTION_STARTED` : (`STARTED_AT`) — 기간 필터/표시용 (추후 기간 검색 대비)

> **커서 페이징 정렬 = `ID DESC` 단독.** ID는 auto-increment PK라 생성순(=최신순)이자 유일하므로,
> `STARTED_AT`(마이크로초 정밀도) 기반 커서의 정밀도 손실로 인한 경계 누락/중복 위험을 피한다.
> 커서는 마지막 항목의 `ID`이며, 조건은 `id < :cursorId`. 쿼리(`WHERE user_id=? AND id<? ORDER BY id DESC`)를
> 복합 인덱스 `(USER_ID, ID)`가 온전히 커버해 부하가 없다. 기간 검색이 생기면 `STARTED_AT` 인덱스를 활용한다.

### 상태 (STATUS)

| 상태 | 의미 |
|------|------|
| RUNNING | 실행 중 |
| SUCCESS | 전체 성공 |
| PARTIAL | 부분 성공/실패 (일부 스텝 실패, 중지) |
| FAILED | 실패로 중단 |
| STOPPED | 사용자 중지 (현재까지 진행분 보존). 히스토리에 남음 |
| CANCELLED | 사용자 취소 (사용자가 실행을 물림). 히스토리에 남음 |

> **중지(STOPPED)와 취소(CANCELLED)는 상태로 구분해 기록한다.** 히스토리는 "무슨 일이 있었나"의
> 기록이므로, [중지]와 [취소]를 다른 사건으로 남겨 사용자가 히스토리에서 구분해 볼 수 있게 한다
> (필터/집계도 가능). 중단 시 `RESULT_SUMMARY`에 사유 + 완료 스텝 수를 자동 기록한다
> (예: "취소됨 · 1/3 스텝 완료"). **STOPPED/FAILED(PARTIAL)는 [이어서 실행]으로 재개 가능**하다
> (첫 미완료 레시피부터 레시피 단위 재시도, 기존 EXECUTION 재사용 — plan.md "이어서 실행").
> CANCELLED는 재개하지 않는다(사용자가 물렀음).

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
| `RECIPE_SNAPSHOT_JSON` | LONGTEXT | **실행 시점 레시피 전체 스냅샷** (메타+스텝+변수+결과정의). 원본이 바뀌거나 삭제돼도 히스토리 재현 가능. 멀티 서비스 실행을 위해 실행용 확정 뷰 `services`/`resolvedSteps`를 함께 굳힌다(아래 [services/resolvedSteps](#servicesresolvedsteps-멀티-서비스-실행용-확정-뷰)) |
| `SEQUENCE` | INT | 플랜 내 순서 |
| `STATUS` | VARCHAR(20) | PENDING / RUNNING / SUCCESS / SKIPPED / FAILED / STOPPED |
| `RESULT_VALUES_JSON` | JSON | 이 레시피의 결과 정의 값 (다음 레시피 입력/템플릿용) |
| `STARTED_AT` / `FINISHED_AT` | DATETIME NULL | |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**: `IDX_EXECUTION_RECIPE_EXEC` : (`EXECUTION_ID`)

### 레시피 스냅샷 (히스토리 재현)

- 실행 시작 시 **현재 레시피를 통째로 `RECIPE_SNAPSHOT_JSON`에 복사** 저장
- 히스토리 상세는 이 스냅샷 기준으로 렌더링 → 원본 레시피가 수정/삭제돼도 "그때 그 실행"을 정확히 재현
- `RECIPE_ID`는 원본 링크용 (NULL 허용, 삭제 대비). `RECIPE_VERSION_NO`는 실행 시점 버전(참고용)

#### 상세 응답의 원본 레시피 상태 (response 계층 파생, 엔티티 불변)

히스토리 상세 페이지의 "원본 레시피" 링크·버전 안내를 위해, 상세 응답(`ExecutionRecipeView`)은 **엔티티 변경 없이** 원본 레시피의 현재 상태를 조회해 파생 필드로 내린다. `EXECUTION_RECIPE`는 스냅샷만 갖고 원본의 현재 상태를 모르므로, 응답 매핑 단계에서 `RECIPE_ID`로 원본 `RECIPE`를 조회(일괄, N+1 방지)해 채운다.

| 파생 필드 | 산출 | 용도 |
|-----------|------|------|
| `recipeDeleted` (boolean) | `RECIPE_ID`가 NULL이거나 원본 `RECIPE`가 없음/`DELETED_AT` 있음 → true | 링크 비활성 + "원본 삭제됨" |
| `recipeCurrentVersion` (int, nullable) | 원본이 살아있으면 `RECIPE.CURRENT_VERSION`, 삭제/부재면 null | 실행 시점 버전(`RECIPE_VERSION_NO`)과 비교해 "버전 바뀜" 안내 |

- 이 두 필드는 **응답 전용 파생값**이다. `EXECUTION`/`EXECUTION_RECIPE`/`RECIPE` 엔티티·컬럼은 바뀌지 않는다(읽기만).
- FE 표시 규칙은 [pages/history-full.md 원본 레시피 링크 판정](../specs/pages/history-full.md#원본-레시피-링크-판정) 참조.
- 목적 구분:
  - `RECIPE_VERSION` (recipe.md) = 레시피 편집 이력/복원용
  - `EXECUTION_RECIPE.RECIPE_SNAPSHOT_JSON` = 실행 감사/히스토리 재현용 (실행과 완전 독립)

#### services / resolvedSteps (멀티 서비스 실행용 확정 뷰)

멀티 서비스 실행([execution.md 스텝별 baseUrl/endpoint 해석](../specs/recipe/execution.md#스텝별-baseurlendpoint-해석-멀티-서비스))을 위해, 실행 시작 시 BE가 그 시점 스펙 메타를 읽어 `RECIPE_SNAPSHOT_JSON` 안에 실행용 뷰 두 필드를 함께 굳힌다. 목적은 **스냅샷만으로 실행 재현**(스펙이 나중에 바뀌거나 삭제돼도 그 실행/히스토리는 안 깨짐)이다.

| 필드 | 구조 | 용도 |
|------|------|------|
| `services` | `{ "<apiSpecId>": { name, baseUrl } }` | 그 레시피가 참조하는 **모든 서비스 메타**(표시/디버깅용). 여러 서비스를 가리키면 여러 엔트리 |
| `resolvedSteps` | `[ { stepIndex, apiSpecId, endpointId, method, path, baseUrl } ]` | **스텝별 실행 확정 뷰**(실행 직행용). 각 항목은 원문 `stepsJson` 배열 인덱스 `stepIndex`로 대응 |

- **생성 주체 = BE(실행 시작 시)**: 이 두 필드는 **BE가 실행 시작(EXECUTION_RECIPE 스냅샷 저장) 시점에** `stepsJson`을 파싱해 각 API 스텝의 `apiSpecId`/`endpointId`로 스펙·endpoint를 조회해 굳힌다(통짜 저장 원칙의 정당한 예외 — 실행 오케스트레이션). FE가 아니라 서버가 확정한다.
- **원문 보존 + 실행용 분리**: `stepsJson` 원문은 **편집/재현용**으로 스냅샷에 그대로 보존하고, `resolvedSteps`는 그로부터 파생한 **실행용** 뷰다. FE는 `resolvedSteps`의 `baseUrl`/`method`/`path`로 외부 API를 직접 호출하고(서버 프록시 없음), `path`의 경로 변수는 실행 시 `pathParams`로 치환한다(원본 path 보존). `services`는 JSON object라 키가 **문자열화된 apiSpecId**(`resolvedSteps[].apiSpecId`는 숫자 — 조회 시 문자열 변환 유의).
- **대응/제외 (stepIndex 기준)**: `resolvedSteps`의 각 항목은 원문 `stepsJson` 배열의 **인덱스 `stepIndex`로 대응**한다(endpointId 기준 아님). 같은 endpointId 반복 호출 + 스크립트/서브레시피 스텝 혼재 시 순서·endpointId 매칭이 어긋나므로 명시적 `stepIndex`로만 원본 스텝을 특정한다. API가 아닌 스텝(스크립트/서브레시피)은 `resolvedSteps`에 포함하지 않으므로 `stepIndex`가 비연속(건너뜀)일 수 있다.
- **플랜 단위**: 플랜은 레시피별 `EXECUTION_RECIPE` 행으로 분리되므로 `services`/`resolvedSteps`는 **각 레시피 스냅샷 행 단위**로 들어간다. 별도 상위 배열을 두지 않는다([plan.md](../specs/recipe/plan.md)).
- **서비스 못 찾음**: 스냅샷 생성 시 스텝 `apiSpecId`의 서비스를 해석하지 못하면 조용히 대체하지 않고 그 스텝을 실패 처리한다([execution.md 스텝 서비스 못 찾음](../specs/recipe/execution.md#스텝-서비스스펙-못-찾음-실패-처리)).
- **마이그레이션 제외**: `services`/`resolvedSteps`는 과거엔 없던 개념이므로, 기존 실행 히스토리의 `RECIPE_SNAPSHOT_JSON`에 **소급 주입하지 않는다**("그때 그대로" 불변 원칙). apiSpecId 역산 마이그레이션의 보정 대상은 레시피 계열(`RECIPE`/`RECIPE_VERSION`)뿐이다([db/recipe.md 마이그레이션](recipe.md#apispecid-역산-마이그레이션-일회성)).

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

### 이어서 실행 (PARTIAL 재개)

- **STOPPED/FAILED(PARTIAL) 실행에서 첫 미완료 레시피부터 재개**한다(레시피 단위 재시도). 실패한 레시피는 스텝 중간에 죽었어도 그 레시피 전체를 다시 실행한다(스텝 단위 부분 재개는 백로그).
- 재개 시작 지점 판별을 위해 **미실행 레시피는 EXECUTION_RECIPE.STATUS=PENDING으로 보존**한다(실행 종료 시 FAILED로 덮지 않음). 완료=SUCCESS / 실패=FAILED / 미실행=PENDING으로 구분된다.
- 완료된 앞 레시피의 결과는 `CONTEXT_JSON` 누적값으로 보존되어 재개 시 이어받는다. 새 EXECUTION을 만들지 않고 **기존 EXECUTION을 RUNNING으로 되돌린다**.
- 트리거: `POST /executions/{id}/resume` (실패/중단 카드의 [이어서 실행] 버튼). 상세: [plan.md 이어서 실행](../specs/recipe/plan.md).

---

## 새로고침 복원 (파트-실행 연결)

실행 진행 블록이 FE 메모리(zustand)에만 있으면 새로고침 시 사라진다. 서버 저장분(턴+파트)으로 복원한다.

- **렌더 정참조(복원의 축)**: `MESSAGE_PART.EXECUTION_ID`. 대화 진입/새로고침 시 턴+파트를 로드하면 PROGRESS/RESULT 파트가 자기 `executionId`로 실행 상세(`GET /executions/{id}`)를 가리키므로, 진행/결과 블록이 제 위치(파트 순서)에 손실 없이 복원된다.
- **촉발 역참조(분석/감사)**: `EXECUTION.TRIGGER_PART_ID`. "이 실행이 어느 파트에서 시작됐나"를 실행 기록에서 특정한다. 한 턴에 실행이 여러 개일 때 각 실행을 파트 단위로 구분한다. (복원 렌더는 파트→실행 정참조로 충분하고, 이 역참조는 사실 추적용)
- **이후 갱신은 SSE**: 복원 이후 진행은 `message_update`(그 턴의 PROGRESS 파트 갱신 = 턴 전체 스냅샷), 완료는 `message_update`(PROGRESS 파트 확정) + RESULT 파트 append로 갱신한다. (기존 `execution_progress`/`execution_complete` 커스텀 이벤트는 폐지 — [messaging.md 실행 SSE 흐름](../specs/common/messaging.md#실행-sse-흐름-message_new--message_update))
- RUNNING 상태로 남은 실행의 처리(브라우저 종료로 중단된 실행)는 [recipe/execution.md 브라우저 새로고침](../specs/recipe/execution.md#브라우저-새로고침--탭-닫기) 정책을 따른다.

---

## 히스토리 독립성 (history.md)

- 히스토리 조회는 `USER_ID` 기준 (대화 무관) → 대화방을 삭제해도 실행 기록은 그대로 조회된다
- 대화방은 **소프트 삭제**(row 유지)이므로 `EXECUTION.CONVERSATION_ID` 연결을 **끊지 않는다**. 연결을 남겨 "이 실행이 나온 대화" 추적/복구가 가능하다 (프로젝트 원칙: 소프트 삭제 기준, FK 유지)
- 플랜 히스토리: `EXECUTION(TYPE=PLAN)` + `EXECUTION_RECIPE` 펼쳐서 표시

---

## 확장 고려

- 실행 재시도 이력: EXECUTION에 `PARENT_EXECUTION_ID` 추가로 "재실행 체인" 추적 가능 (추후)
- 민감 데이터 마스킹 정책: EXECUTION_STEP 저장 시 필터 (추후)
- 대량 실행(× N건) 시 EXECUTION_STEP 폭증 → 파티셔닝/아카이빙 (추후)
