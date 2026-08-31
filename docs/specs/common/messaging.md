---
status: draft
last-updated: 2026-08-27
---

# 메시징 및 SSE 이벤트 정의

## 개요

- 사용자 → 서버: POST API로 메시지 전송
- 서버 → 클라이언트: Global SSE 1개로 모든 이벤트 수신
- 메시지 포맷: 구조화된 JSON (type + content + metadata)
- 텍스트 포맷: Markdown 기본

---

## 메시지 JSON 구조

### 서버 → 클라이언트 (채팅 메시지)

```json
{
  "id": "msg_123",
  "sessionId": 1,
  "role": "assistant",
  "type": "text | card | progress | action_picker | system",
  "content": "Markdown 텍스트",
  "format": "markdown",
  "metadata": { },
  "createdAt": "2026-08-27T14:30:00"
}
```

### 클라이언트 → 서버 (사용자 발화)

```json
{
  "sessionId": 1,
  "type": "text | action | action_picker_response",
  "content": "사용자 입력 텍스트",
  "metadata": { },
  "referenceId": "recipe_123"
}
```

- `referenceId`: 참조 태그로 전달되는 레시피/도구 ID (nullable)

---

## 메시지 타입

### 서버 → 클라이언트

| type | 설명 | metadata 구조 |
|------|------|--------------|
| `text` | 일반 텍스트 응답 | 없음 |
| `card` | 카드 UI | `{ cardType, recipeId, executionId, buttons, ... }` |
| `progress` | 레시피 실행 진행 상태 | `{ executionId, steps: [...] }` |
| `action_picker` | 액션 피커 호출 | `{ executionId, stepIndex, variables: [...] }` |
| `system` | 시스템 메시지 (에러, 안내 등) | `{ level: "info" | "warn" | "error" }` |

### 클라이언트 → 서버

| type | 설명 | metadata 구조 |
|------|------|--------------|
| `text` | 자유 텍스트 발화 | 없음 |
| `action` | 버튼 클릭 액션 | `{ action, recipeId, executionId, mode, ... }` |
| `action_picker_response` | 액션 피커 입력 완료 | `{ executionId, stepIndex, values: {...} }` |

---

## 카드 UI metadata 상세

| cardType | 용도 | 추가 필드 |
|----------|------|-----------|
| `execution_mode` | 실행 모드 선택 | `recipeId`, `buttons: ["auto", "manual"]` |
| `result` | 실행 결과 보기 | `recipeId`, `executionId`, `timestamp` |
| `retry` | 실패 후 재시도 | `executionId`, `failedStepIndex` |
| `auth_required` | 인증 필요 | `loginPageUrl`, `executionId` |
| `candidates` | 유사 레시피 후보 | `recipes: [{ id, name, description }]` |
| `service_select` | 서비스 선택 | `services: [{ name, label }]` |
| `references` | 정보 조회 참고 자료 (investigate 출처 인용) | `references: [{ source, label, url }]` |

---

## SSE 이벤트

### 연결

- 엔드포인트: `GET /api/v1/sse/connect?token={jwt}`
- Global SSE — 사용자당 1개 연결
- 모든 대화방의 이벤트가 하나의 스트림으로 전달됨
- FE가 `sessionId`로 현재 대화방 이벤트만 렌더링, 나머지는 상태만 업데이트

### 이벤트 타입

| event | 설명 | data 구조 |
|-------|------|-----------|
| `message_new` | 새 메시지 도착 | 메시지 JSON 전체 |
| `message_update` | 기존 메시지 업데이트 (진행 상태 등) | `{ sessionId, messageId, message: {...} }` |
| `session_status` | 대화방 상태 변경 | `{ sessionId, status: "running" | "input_waiting" | "idle" }` |
| `session_list_update` | 대화방 목록 갱신 (이름 변경, 서비스 변경 등) | `{ sessionId, title, service, updatedAt }` |
| `execution_progress` | 레시피 실행 스텝 진행 | `{ sessionId, executionId, stepIndex, status, summary }` |
| `execution_complete` | 레시피 실행 완료 | `{ sessionId, executionId, status: "success" | "failed" }` |
| `heartbeat` | 연결 유지용 | `{}` |

### 재연결 정책

- 서버가 각 이벤트에 `id` 부여
- 연결 끊김 시 `EventSource` 자동 재연결
- 재연결 시 `Last-Event-ID` 헤더로 마지막 수신 ID 전달
- 서버가 해당 ID 이후 놓친 이벤트를 replay (버퍼 유지 시간: 5분)
- 5분 초과 시 replay 불가 → FE가 현재 대화방 상태를 REST API로 재조회

### Heartbeat

- 30초마다 heartbeat 이벤트 전송
- 클라이언트가 60초 이상 heartbeat 미수신 시 연결 끊김으로 판단 → 재연결

### FE 이벤트 처리 전략

| 이벤트 | 현재 보고 있는 대화방 | 다른 대화방 |
|--------|---------------------|------------|
| `message_new` | 채팅에 즉시 렌더링 | 상태 뱃지(🔵) 업데이트 |
| `message_update` | 해당 메시지 즉시 갱신 | 무시 (진입 시 로드) |
| `session_status` | 입력 영역 상태 반영 | 목록 뱃지 업데이트 |
| `session_list_update` | — | 대화 목록 반영 |
| `execution_progress` | 진행 상태 블록 갱신 | 무시 |
| `execution_complete` | 완료 메시지 + 카드 UI | 상태 뱃지(🔵) |

---

## 인증

SSE 연결 시 JWT 토큰을 URL 쿼리 파라미터로 전달:
```
GET /api/v1/sse/connect?token=eyJhbGciOiJ...
```

`EventSource`는 커스텀 헤더를 지원하지 않으므로 쿼리 파라미터 방식 사용.
토큰 만료 시 서버가 연결 종료 → 클라이언트가 토큰 갱신 후 재연결.
