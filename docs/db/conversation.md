---
status: draft
last-updated: 2026-09-08
ref: docs/specs/chat/overview.md, docs/specs/common/messaging.md
---

# 대화 도메인 DB 설계

채팅 대화방 + 메시지.

## 설계 방침

- 대화는 **턴(MESSAGE)** 의 나열이고, 한 턴은 **파트(MESSAGE_PART) 배열**로 구성된다. 턴 = 사용자 발화 1개 / AI 응답 1턴.
- 파트는 화면 렌더 단위. 실행/조회의 사실은 [EXECUTION](execution.md)·[INVESTIGATION](investigation.md) 계층에 정규화하고 파트가 참조(FK).
- 정렬·커서는 **ID(auto-increment) 단독** — 별도 SEQ 없음.
- 타입별 잔여 상세는 파트의 `PAYLOAD_JSON` (자주 조인하는 참조만 정식 컬럼).
- 대화방에 **대상 서비스**(chat 서비스 설정) 저장.
- 대화방 상태(입력대기/처리중 등)는 실시간이라 DB 저장 최소 — 핵심 상태만.
- 대화방 삭제해도 실행/조회 히스토리는 독립 유지 (execution.md / investigation.md 참조).

## 테이블 개요

| 테이블 | 역할 |
|--------|------|
| `CONVERSATION` | 대화방 (세션) |
| `MESSAGE` | 대화 턴 (사용자 발화 1개 / AI 응답 1턴) |
| `MESSAGE_PART` | 턴 안의 순서 있는 블록 (텍스트·카드·진행·결과·조회·액션피커·참고자료) |

---

## CONVERSATION

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | |
| `USER_ID` | BIGINT FK | 소유자 |
| `TITLE` | VARCHAR(200) | 대화 제목 (자동 생성 or 사용자 변경) |
| `API_SPEC_ID` | BIGINT FK NULL | 대화방 대상 서비스 (미지정 시 NULL) |
| `STATUS` | VARCHAR(20) | IDLE / AI_RESPONDING / EXECUTING / WAITING_INPUT (대화방 처리 상태) |
| `LAST_MESSAGE_AT` | DATETIME | 마지막 메시지 시각 (목록 정렬) |
| `LAST_READ_AT` | DATETIME NULL | 사용자가 마지막으로 읽은 시각 (안 읽음 판정) |
| `DELETED_AT` | DATETIME NULL | 소프트 삭제 |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**
- `IDX_CONVERSATION_USER` : (`USER_ID`, `LAST_MESSAGE_AT`) — 목록 최신순
- `IDX_CONVERSATION_SPEC` : (`API_SPEC_ID`)

### 상태 (STATUS)

`session_status`(messaging.md)와 1:1 매핑. 입력 영역 구동 + 목록 뱃지에 사용.

| 상태 | 의미 | 사이드바 뱃지 |
|------|------|--------------|
| IDLE | 유휴 | (없음) |
| AI_RESPONDING | AI 응답 생성 중 | 🔄 |
| EXECUTING | 레시피/플랜 실행 중 | 🔄 |
| WAITING_INPUT | 액션 피커 입력 대기 | 🟡 |

- 목록 뱃지는 AI_RESPONDING/EXECUTING을 🔄 하나로 통합 표시 (overview.md)
- **서버 기동 시** AI_RESPONDING/EXECUTING로 남은 대화방은 IDLE로 정리 (락과 상태 불일치 방지 — messaging.md 종결 보장)

### 읽음 / 안 읽음

- 읽음은 **DB에 저장**(`LAST_READ_AT`)하여 여러 탭/기기에서 일관되게 동기화한다 (클라이언트 로컬 상태 아님)
- 대화방 진입 시 읽음 API → `LAST_READ_AT = now` → `session_list_update`(upsert, unread=false)로 **모든 탭 뱃지 동기화**
- 안 읽음 판정: `LAST_MESSAGE_AT > LAST_READ_AT`
- 다중 사용자 공유 대화방이 생기면 `CONVERSATION_READ(USER_ID, CONVERSATION_ID, LAST_READ_AT)` 테이블로 승격 (현재는 대화방 소유자 1명이라 컬럼으로 충분)
- **빈 대화방은 서버에 생성하지 않음.** 첫 메시지 전송 시 대화방 + 메시지를 함께 생성(트랜잭션) + `session_list_update`(upsert) 발행 → orphan 방지 (overview.md)
- 첫 메시지 시 제목은 임시(첫 메시지 앞 20자 이내 절단), AI 요약 후 교체 (overview.md)
- 따라서 `LAST_MESSAGE_AT`이 NULL인 대화방은 존재하지 않음 (생성 시 첫 메시지가 항상 있음)

---

## MESSAGE (턴)

한 행 = **한 턴**(사용자 발화 1개 또는 AI 응답 1턴). 화면에 그려지는 실제 블록(텍스트·카드·진행·결과 등)은 이 턴에 딸린 [MESSAGE_PART](#message_part-파트)로 저장한다. 정렬·커서는 `ID`(auto-increment) 단독.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK (auto-inc) | 대화방 내 **정렬·커서 기준**(오름차순 = 시간순). 별도 SEQ 없음 |
| `CONVERSATION_ID` | BIGINT FK | 소속 대화방 |
| `ROLE` | VARCHAR(20) | USER / ASSISTANT / SYSTEM |
| `STATUS` | VARCHAR(20) | STREAMING / COMPLETE / FAILED (턴 전체 상태) |
| `CONTENT_PREVIEW` | VARCHAR(500) NULL | 목록 미리보기·검색용 요약(파트에서 파생한 **캐시**, 진실 아님) |
| `CLIENT_MESSAGE_ID` | VARCHAR(50) NULL | 낙관적 UI 매칭용(사용자 메시지) |
| `REFERENCE_ID` | VARCHAR(50) NULL | 사용자 발화의 참조 태그(레시피 ID 등) |
| `CREATED_AT` | DATETIME | 생성 시각 |

**인덱스**: `IDX_MESSAGE_CONVERSATION` : (`CONVERSATION_ID`, `ID`) — 대화방 턴 커서 페이징(`WHERE conversation_id=? AND id<:cursor ORDER BY id DESC`)

- **정렬/커서 = `ID` 단독**(auto-increment·유일). FE는 SSE 도착 순서가 아니라 턴 `ID`로 정렬한다. 시각 동률·SSE 순서 뒤바뀜 문제가 원천 없다([execution.md 커서 원칙](execution.md)과 일관).
- `ROLE`: USER / ASSISTANT / SYSTEM. 시스템 안내(취소/중지 등)는 `ROLE=SYSTEM` 턴 + TEXT 파트 1개로 표현한다.
- `STATUS`: AI 응답 시작 시 **빈 ASSISTANT 턴을 `STREAMING`으로 INSERT** → 파트를 append하며 진행 → 완료 시 `COMPLETE`, 오류 시 `FAILED`. 사용자 턴은 저장 시 `COMPLETE`. (기존 "PENDING 자리 미리 INSERT" 패턴의 대체 — messaging.md 종결 보장/낙관적 UI)
- `CONTENT_PREVIEW`: 대화 목록 한 줄·검색 색인용. 파트 내용에서 파생해 캐시하며, 파트가 바뀌면 갱신한다. 진실이 아니므로 렌더/판정에 쓰지 않는다.

---

## MESSAGE_PART (파트)

한 턴 안의 **순서 있는 블록**. 화면 렌더 단위이자, 실행/조회 사실 계층을 가리키는 참조점이다. **동종 파트 N개 허용**(TEXT 여러 개, 한 턴에 실행 여러 번 등). 정렬은 `ID` 오름차순(append-only = 생성순 = 표시순).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK (auto-inc) | 턴 내 **파트 순서 기준**(오름차순). 별도 SEQ 없음 |
| `MESSAGE_ID` | BIGINT FK | 소속 턴 |
| `TYPE` | VARCHAR(30) | TEXT / CARD / PROGRESS / RESULT / INVESTIGATE / ACTION_PICKER / REFERENCES |
| `STATUS` | VARCHAR(20) | 타입별(아래 상태머신) |
| `CONTENT` | LONGTEXT NULL | TEXT 파트 본문 (Markdown, 표시 진실) |
| `EXECUTION_ID` | BIGINT FK NULL | 실행류 파트(PROGRESS/RESULT/execution_mode 카드/ACTION_PICKER)가 가리키는 실행 (렌더 정참조) |
| `INVESTIGATION_ID` | BIGINT FK NULL | INVESTIGATE 파트가 가리키는 조회 (렌더 정참조) |
| `CARD_TYPE` | VARCHAR(30) NULL | CARD 파트 세부 유형(plan/candidates/service_select/auth_required/retry) |
| `PAYLOAD_JSON` | JSON NULL | 타입별 구조화 데이터(FK로 안 빠지는 잔여 — steps/buttons/variables/references 등) |
| `SCHEMA_VERSION` | INT | payload 스키마 버전 (messaging.md 버전 폴백) |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**
- `IDX_MESSAGE_PART_MESSAGE` : (`MESSAGE_ID`, `ID`) — 턴별 파트 정렬 조회
- `IDX_MESSAGE_PART_EXECUTION` : (`EXECUTION_ID`) — 실행별 파트 역조회
- `IDX_MESSAGE_PART_INVESTIGATION` : (`INVESTIGATION_ID`) — 조회별 파트 역조회

### 파트 STATUS 상태머신 (타입별)

| 파트 성격 | 타입 | 상태 흐름 |
|-----------|------|-----------|
| 스트리밍 | TEXT | STREAMING → COMPLETE / FAILED |
| 실행/조회 | PROGRESS / RESULT / INVESTIGATE | 참조 대상(EXECUTION/INVESTIGATION) 상태를 따름 |
| 인터랙티브 | CARD(plan 등) / ACTION_PICKER | PENDING → CONSUMED / CANCELLED |
| 정적 | REFERENCES | COMPLETE |

- **인터랙티브 파트의 `CONSUMED`**: 사용자가 카드/피커에 응답 완료. 새로고침 후에도 이 상태로 복원되어 "이미 실행한 카드"가 다시 활성화되지 않는다.
- **THINKING(모델 추론)은 파트로 저장하지 않는다**(현재 reasoning 스트리밍 기능 없음, 저장 실익 없음, AI 컨텍스트 재주입 안 함). 추후 도입 시 ephemeral 파트로 추가.

### content / payloadJson (파트 단위 이원화)

- `CONTENT` = TEXT 파트의 표시 본문. `PAYLOAD_JSON` = 타입별 구조화 데이터(진실).
- 실행/조회류 파트는 `EXECUTION_ID`/`INVESTIGATION_ID`로 사실 계층을 가리키고, `PAYLOAD_JSON`은 그 시점 렌더 스냅샷(steps/summary 등)을 담는다.
- 자주 조인/필터하는 참조(EXECUTION_ID/INVESTIGATION_ID/CARD_TYPE)만 정식 컬럼으로 두고, 나머지 타입별 잔여는 `PAYLOAD_JSON`에 둔다(과도한 정규화 회피 — 유지보수성·확장성 균형).

---

## 대화-실행/조회 연결

- 파트 → 실행/조회 (렌더 정참조): `MESSAGE_PART.EXECUTION_ID` → EXECUTION, `MESSAGE_PART.INVESTIGATION_ID` → INVESTIGATION.
- 실행/조회 → 파트 (촉발 역참조): `EXECUTION.TRIGGER_PART_ID` → MESSAGE_PART, `INVESTIGATION.TRIGGER_PART_ID` → MESSAGE_PART. **한 턴에 실행/조회가 여러 개여도** 각자 촉발 파트를 특정한다 (상세: [execution.md](execution.md), [investigation.md](investigation.md)).
- 대화 삭제(soft): `CONVERSATION.DELETED_AT`만 설정. 연결된 `EXECUTION.CONVERSATION_ID`/`INVESTIGATION.CONVERSATION_ID`는 **그대로 유지**(FK를 끊지 않음). 히스토리는 `USER_ID` 기준 조회라 대화 삭제와 무관하게 독립 유지 (프로젝트 원칙: 소프트 삭제 기준, 연결 유지).
- 실행 중 대화 삭제: 차단 or 중지 확인 (execution.md)

---

## AI 이력 압축 (ai-config.md)

- AI 호출 시 최근 N턴을 넘기되, **파트를 그대로 직렬화하지 않고 화이트리스트+요약으로 변환**한다: TEXT는 그대로, RESULT는 결과 요약(resultValues+summary)만, investigate 답변 TEXT는 그대로. PROGRESS/INVESTIGATE 진행·CARD/ACTION_PICKER UI 구조·원시 응답은 제외. (규칙: [messaging.md AI 컨텍스트 변환 규칙](../specs/common/messaging.md#ai-컨텍스트-변환-규칙-파트--llm-메시지))
- 요약 캐시 확장(추후): `CONVERSATION`에 `HISTORY_SUMMARY` 컬럼(5턴마다 fast 모델 갱신). 프로토타입은 최근 N턴 슬라이싱으로 시작.

---

## 확장 고려

- 안 읽음 정확 추적: `CONVERSATION_READ(USER_ID, CONVERSATION_ID, LAST_READ_AT)` 테이블 (다중 기기 대비, 추후)
- 메시지 편집/삭제: MESSAGE에 `DELETED_AT` 추가 (추후)
- 대화 요약 캐시: 위 HISTORY_SUMMARY (추후)
- reasoning(THINKING) 스트리밍: 도입 시 ephemeral 파트(비저장)로 추가 (추후)
