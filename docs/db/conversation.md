---
status: draft
last-updated: 2026-09-01
ref: docs/specs/chat/overview.md, docs/specs/common/messaging.md
---

# 대화 도메인 DB 설계

채팅 대화방 + 메시지.

## 설계 방침

- 메시지는 **정규화** (조회/렌더링 필요) + 타입별 상세는 `METADATA_JSON`
- 대화방에 **대상 서비스**(chat 서비스 설정) 저장
- 대화방 상태(입력대기/처리중 등)는 실시간이라 DB 저장 최소 — 핵심 상태만
- 대화방 삭제해도 실행 히스토리는 독립 유지 (execution.md 참조)

## 테이블 개요

| 테이블 | 역할 |
|--------|------|
| `CONVERSATION` | 대화방 (세션) |
| `MESSAGE` | 대화 메시지 |

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

## MESSAGE

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | |
| `CONVERSATION_ID` | BIGINT FK | 소속 대화방 |
| `SEQ` | BIGINT | 대화방 내 정렬 순서 (서버 발번, CREATED_AT 동시각 충돌 방지) |
| `ROLE` | VARCHAR(20) | USER / ASSISTANT / TOOL (AI Tool Use 결과) |
| `TYPE` | VARCHAR(20) | TEXT / CARD / PROGRESS / ACTION_PICKER / SYSTEM |
| `STATUS` | VARCHAR(20) | PENDING / COMPLETED / FAILED (AI 응답 pending → 완료/실패 전이) |
| `CONTENT` | LONGTEXT | 메시지 본문 (Markdown) |
| `METADATA_JSON` | JSON NULL | 타입별 상세 (cardType, executionId, buttons 등 — messaging.md) |
| `REFERENCE_ID` | VARCHAR(50) NULL | 참조 태그 (레시피 ID 등) |
| `CREATED_AT` | DATETIME | 생성 시각 |

**인덱스**: `IDX_MESSAGE_CONVERSATION` : (`CONVERSATION_ID`, `SEQ`) — 대화방 메시지 정렬 조회

- `SEQ`: 대화방 내 단조 증가 순서. FE는 SSE 도착 순서가 아니라 SEQ로 정렬 (messaging.md 낙관적 UI)
- `ROLE`은 AI 대화 주체 (USER/ASSISTANT/TOOL). 시스템 안내는 `TYPE=SYSTEM`으로 표현 (role과 별개)
- `STATUS`: AI 응답 자리를 `PENDING`으로 미리 INSERT → 완료 시 `COMPLETED`(내용 채움), 오류 시 `FAILED`. 사용자 메시지는 저장 시 `COMPLETED`. (messaging.md 종결 보장 / 낙관적 UI 근거)
- 메시지 타입/메타 구조는 messaging.md의 메시지 JSON 규격과 일치
- 진행 상태(PROGRESS)/카드(CARD)는 METADATA_JSON에 executionId 등 보관 → 렌더링

---

## 대화-실행 연결

- 대화에서 레시피 실행 → `EXECUTION.CONVERSATION_ID`로 연결 (execution.md)
- 대화 삭제(soft): `CONVERSATION.DELETED_AT` 설정 + 연결된 `EXECUTION.CONVERSATION_ID = NULL` (히스토리 독립)
- 실행 중 대화 삭제: 차단 or 중지 확인 (execution.md)

---

## AI 이력 압축 (ai-config.md)

- AI 호출 시 최근 3~5건 원문 + 이전은 요약. 요약은 별도 저장 가능:
  - 확장 시 `CONVERSATION`에 `HISTORY_SUMMARY` 컬럼 추가 (5턴마다 fast 모델로 갱신)
  - 프로토타입: 단순 최근 N건 슬라이싱으로 시작 (요약 저장 추후)

---

## 확장 고려

- 안 읽음 정확 추적: `CONVERSATION_READ(USER_ID, CONVERSATION_ID, LAST_READ_AT)` 테이블 (다중 기기 대비, 추후)
- 메시지 편집/삭제: MESSAGE에 `DELETED_AT` 추가 (추후)
- 대화 요약 캐시: 위 HISTORY_SUMMARY (추후)
