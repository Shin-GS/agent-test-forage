---
status: draft
last-updated: 2026-08-28
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
| `STATUS` | VARCHAR(20) | IDLE / RUNNING / WAITING_INPUT (대화방 상태) |
| `LAST_MESSAGE_AT` | DATETIME | 마지막 메시지 시각 (목록 정렬) |
| `DELETED_AT` | DATETIME NULL | 소프트 삭제 |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**
- `IDX_CONVERSATION_USER` : (`USER_ID`, `LAST_MESSAGE_AT`) — 목록 최신순
- `IDX_CONVERSATION_SPEC` : (`API_SPEC_ID`)

### 상태 (STATUS)

| 상태 | 의미 | 사이드바 뱃지 |
|------|------|--------------|
| IDLE | 유휴 | (없음) |
| RUNNING | AI 응답/실행 중 | 🔄 |
| WAITING_INPUT | 액션 피커 입력 대기 | 🟡 |

- "안 읽음(🔵)"은 클라이언트 상태(마지막 읽은 시각 비교)라 별도 테이블 불필요 (프로토타입)
- 빈 대화(메시지 0건)는 목록에 안 쌓임 → 첫 메시지 전송 시 실제 생성 (overview.md)

---

## MESSAGE

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | |
| `CONVERSATION_ID` | BIGINT FK | 소속 대화방 |
| `ROLE` | VARCHAR(20) | USER / ASSISTANT / TOOL (AI Tool Use 결과) |
| `TYPE` | VARCHAR(20) | TEXT / CARD / PROGRESS / ACTION_PICKER / SYSTEM |
| `CONTENT` | LONGTEXT | 메시지 본문 (Markdown) |
| `METADATA_JSON` | JSON NULL | 타입별 상세 (cardType, executionId, buttons 등 — messaging.md) |
| `REFERENCE_ID` | VARCHAR(50) NULL | 참조 태그 (레시피 ID 등) |
| `CREATED_AT` | DATETIME | 생성 시각 (순서) |

**인덱스**: `IDX_MESSAGE_CONVERSATION` : (`CONVERSATION_ID`, `CREATED_AT`)

- `ROLE`은 AI 대화 주체 (USER/ASSISTANT/TOOL). 시스템 안내는 `TYPE=SYSTEM`으로 표현 (role과 별개)
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
