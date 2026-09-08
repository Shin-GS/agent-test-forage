---
status: draft
last-updated: 2026-09-08
ref: docs/specs/chat/scenarios/investigation.md, docs/specs/common/messaging.md, docs/db/conversation.md, docs/db/execution.md
---

# 정보 조회(investigate) 도메인 DB 설계

정보 조회 루프(investigate)의 사실 기록. "어떤 질문에 어떤 소스를 조회해 어떤 답을 냈나"를 분석·감사할 수 있게 정규 저장한다.

## 설계 방침

- **조회도 실행처럼 사실 계층으로 정규화**한다. EXECUTION의 형제 계층이며, 구조도 대칭이다(INVESTIGATION 1:N INVESTIGATION_STEP).
- 조회는 실행(EXECUTION)이 아니다 — 레시피 스텝을 실행하지 않고, 결과를 재개(resume)하지 않는다. 그래서 EXECUTION 계층과 분리한다.
- **화면 렌더 스냅샷 vs 사실 저장 분리**: 진행 블록(INVESTIGATE 파트)·참고 자료(REFERENCES 파트)의 payload는 화면 렌더용 스냅샷이고, 여기 INVESTIGATION/INVESTIGATION_STEP은 분석·감사용 사실이다.
- **히스토리 독립**: 대화방을 소프트 삭제해도 조회 기록은 유지된다(`USER_ID` 기준 조회). 대화방 row가 남으므로 `CONVERSATION_ID` 연결은 끊지 않는다(프로젝트 원칙: 소프트 삭제 기준, FK 유지).
- 정렬·커서는 **ID(auto-increment) 단독** (execution.md 커서 원칙과 일관).

## 테이블 개요

| 테이블 | 역할 |
|--------|------|
| `INVESTIGATION` | 1회 정보 조회 루프 |
| `INVESTIGATION_STEP` | 루프 내 소스별 조회 단계 |

계층: `INVESTIGATION` 1:N `INVESTIGATION_STEP`

---

## INVESTIGATION

1회 조회 루프 = 1행.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK (auto-inc) | 정렬·커서 기준 |
| `USER_ID` | BIGINT FK | 조회한 사용자 (히스토리/분석 조회 기준) |
| `CONVERSATION_ID` | BIGINT FK NULL | 조회가 일어난 대화방. 소프트 삭제라 연결 유지. NULL은 대화 없이 시작된 조회(추후) 대비 |
| `TRIGGER_PART_ID` | BIGINT FK NULL | **조회를 촉발한 MESSAGE_PART** ID. 한 턴에 조회가 여러 번이어도 각 조회가 자기 촉발 파트를 특정. 턴은 part→message_id로 유도 |
| `API_SPEC_ID` | BIGINT FK NULL | 조회 대상 서비스 (참조용, 스펙 삭제 대비 NULL 허용) |
| `QUERY_TEXT` | VARCHAR(500) | 사용자 질문(무엇을 조회했나) — 분석 핵심 |
| `STATUS` | VARCHAR(20) | RUNNING / DONE / FAILED / TIMEOUT |
| `ANSWER_SUMMARY` | TEXT NULL | 최종 답변 요약(분석용). 종료(DONE) 시 채움 |
| `STARTED_AT` | DATETIME | 시작 |
| `FINISHED_AT` | DATETIME NULL | 종료 (RUNNING이면 NULL) |
| `DURATION_MS` | BIGINT NULL | 소요 시간 |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**
- `IDX_INVESTIGATION_USER_ID` : (`USER_ID`, `ID`) — 사용자 조회 이력 커서 페이징 (ID DESC 단독, execution.md와 동일 원칙)
- `IDX_INVESTIGATION_CONVERSATION_ID` : (`CONVERSATION_ID`, `ID`) — 대화방별 조회 조회
- `IDX_INVESTIGATION_TRIGGER_PART_ID` : (`TRIGGER_PART_ID`) — 촉발 파트별 조회 (한 턴 다중 조회 구분)

### 상태 (STATUS)

| 상태 | 의미 |
|------|------|
| RUNNING | 조회 루프 진행 중 |
| DONE | 정상 종료 (최종 답변 발행) |
| FAILED | 전 커넥터 실패 등으로 조회 실패 |
| TIMEOUT | 루프 타임아웃(120초 초과) |

> **비정상 종료(FAILED/TIMEOUT)도 `running` 잔존 없이 확정**한다(서버 finally에서 상태 확정 — 유령 진행 블록 방지, [messaging.md INVESTIGATE](../specs/common/messaging.md#investigate-정보-조회-진행-블록)).

---

## INVESTIGATION_STEP

루프 내 소스별 조회 단계. 정렬은 `ID` 오름차순(조회 순서).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK (auto-inc) | 조회 순서 기준 |
| `INVESTIGATION_ID` | BIGINT FK | 소속 조회 루프 |
| `SOURCE` | VARCHAR(30) | 조회 소스 (`api_spec` / `confluence`) |
| `QUERY` | VARCHAR(500) | 조회 질의 |
| `STATUS` | VARCHAR(20) | RUNNING / SUCCESS / FAILED / SKIPPED |
| `REFERENCES_JSON` | JSON NULL | 이 스텝이 찾은 출처(제목/링크 등). REFERENCES 파트 payload의 원천 |
| `STARTED_AT` / `FINISHED_AT` | DATETIME NULL | |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**: `IDX_INVESTIGATION_STEP_INV` : (`INVESTIGATION_ID`, `ID`)

- `SKIPPED`: 조회를 실제 수행하지 못한 경우(미등록 커넥터 source, confluence인데 `confluenceSpaceKey` 미연결, 중복 `(source, query)` 캐시 재사용). **`skipped`도 조회 카운터를 소비**한다([investigation.md 루프 카운터](../specs/chat/scenarios/investigation.md)).

---

## 파트 연결 (렌더 vs 촉발)

- **파트 → 조회 (렌더 정참조)**: `MESSAGE_PART.INVESTIGATION_ID` → INVESTIGATION. INVESTIGATE 파트가 자기가 그릴 조회를 가리킨다. 대화 진입/새로고침 시 파트 로드로 진행 블록이 복원된다.
- **조회 → 파트 (촉발 역참조)**: `INVESTIGATION.TRIGGER_PART_ID` → MESSAGE_PART. 조회를 촉발한 파트를 특정한다(한 턴 다중 조회 구분·분석용).
- 화면 칩(참고 자료)은 REFERENCES 파트 payload가 렌더하고, 그 원천 출처는 INVESTIGATION_STEP.REFERENCES_JSON에 남는다(사실 vs 렌더 스냅샷 분리).

---

## API

- `GET /api/v1/investigations/{id}` — 조회 상세(INVESTIGATION + STEP). 실행의 `GET /executions/{id}`에 대응.
- 조회 이력 목록(커서)은 필요 시 추가(현재 화면 요구가 생기면). 실행 히스토리와 달리 별도 히스토리 화면은 아직 없음.

---

## 확장 고려

- 조회 이력 화면/필터: 실행 히스토리처럼 목록 API + 화면 (추후, 요구 생기면)
- 조회 결과 캐시/임베딩: 시맨틱 레시피 검색 도입 시 재검토 (tech.md IntentResolver 확장 경계)
- 민감 데이터 마스킹: STEP 저장 시 필터 (execution.md와 동일 정책, 추후)
