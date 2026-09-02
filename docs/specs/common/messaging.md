---
status: draft
last-updated: 2026-09-01
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
  "seq": 42,
  "role": "assistant",
  "type": "text | card | progress | action_picker | system",
  "content": "Markdown 텍스트",
  "format": "markdown",
  "metadata": { },
  "clientMessageId": null,
  "createdAt": "2026-08-27T14:30:00"
}
```

- `seq`: 대화방 내 **서버 발번 정렬 순서**. FE는 SSE 도착 순서가 아니라 `seq`(동률 시 createdAt)로 메시지를 정렬한다. 낙관적 표시/SSE 순서 뒤바뀜에도 화면 순서가 안 꼬이게 하는 기준.

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
- 전송 API는 **동기 접수**로 거의 즉시 리턴하고(무거운 처리는 async), 응답에 최소 `{ accepted: true, sessionId }`를 준다. FE는 이 성공 응답을 받은 뒤에만 낙관적 임시 메시지를 렌더한다(아래 낙관적 UI).
- **첫 메시지 = 대화방 생성 겸함**: 대화방 ID 없이 첫 메시지를 보내면 서버가 대화방+메시지를 함께 생성하고 새 대화방 정보를 응답에 포함(+ `session_list_update` upsert 발행). 이후 메시지는 대화방 ID로 전송.
  - `POST /api/v1/conversations/messages` — 방 없이 첫 메시지 (방 생성 겸함). 응답에 `conversation` 포함
  - `POST /api/v1/conversations/{id}/messages` — 기존 방에 이어서 전송
  - 빈 대화방을 미리 만들지 않아 orphan을 원천 차단 (overview.md)

### 메시지 목록 조회 (커서 페이징)

대화가 길어져도 부하가 없도록 메시지 조회는 **커서 기반 무한 스크롤**로 제공한다.

- `GET /api/v1/conversations/{id}/messages?cursor={nextCursor}&size={n}`
- 응답: `{ items, nextCursor, hasNext }` (커서 페이지). `items`는 **최신순(seq DESC)**
- 정렬/커서 = 대화방 로컬 `seq`(단조증가·유일). 채팅은 최신이 아래이고 위로 스크롤하면 과거를 불러오므로 "다음 페이지 = 과거"다. `cursor`는 직전 페이지의 가장 과거 seq(응답의 `nextCursor`)를 그대로 전달
- `size` 기본 20, 최대 50 (과도 로딩 방지)
- **FE 렌더**: 진입 시 첫 페이지(최신 N건)를 받아 **seq 오름차순으로 뒤집어** 표시(최신이 아래). 위로 스크롤 시 `nextCursor`로 과거 페이지를 이어 로드해 위쪽에 prepend. 화면 정렬 기준은 항상 `seq`(위 낙관적 UI와 동일)
- `cursor`는 **엔드포인트별 불투명 값**이다(메시지=seq, 실행 히스토리=id). FE는 내부 형식을 해석하지 말고 그대로 다음 요청에 전달

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

### 이벤트 봉투 (envelope)

모든 SSE 이벤트는 하나의 표준 봉투로 전달된다. `category`로 관심사를, `nature`로 성격을 구분한다.

```json
{
  "eventId": 42,
  "category": "CHAT",
  "type": "message_new",
  "nature": "DATA",
  "sessionId": 123,
  "data": { ... }
}
```

- `category`: 이벤트가 속한 관심사 (아래 표). FE 렌더링 라우팅 기준
- `nature`: `SIGNAL`(갱신 트리거, payload 최소, 유실 시 재조회로 복구) / `DATA`(콘텐츠 자체, 유실 시 손실 → replay 대상)
- `type`이 `category`와 `nature`를 모두 보유 (발행 시 type만 지정, 봉투에 함께 직렬화)
- `sessionId`: 대화방 이벤트면 지정, 전역(알림 등)이면 null

### 카테고리 (category)

| category | 의미 | 지금 사용 |
|----------|------|-----------|
| `CHAT` | 대화 메시지/카드 (액션 피커·인증 카드 포함) | ✅ |
| `SESSION` | 대화방 상태/목록 | ✅ |
| `EXECUTION` | 레시피 실행 진행/완료 | ✅ (발행은 실행 엔진 단계) |
| `SYSTEM` | 시스템/연결 수준 신호 (heartbeat + 추후 공지/토큰만료) | ✅ |
| `NOTIFICATION` | 알림센터 | ⬜ 예약 (추후) |

### 이벤트 타입

| event | category | nature | 설명 | data 구조 |
|-------|----------|--------|------|-----------|
| `message_new` | CHAT | DATA | 새 메시지 도착 | 메시지 JSON 전체 |
| `message_update` | CHAT | DATA | 기존 메시지 업데이트 (진행 상태, 추후 토큰 스트리밍) | `{ sessionId, messageId, message: {...} }` |
| `session_status` | SESSION | SIGNAL | 대화방 **처리 상태** 변경 (입력 영역 구동, 고빈도) | `{ sessionId, status }` (아래 상태값) |
| `session_list_update` | SESSION | SIGNAL | 대화방 **목록 한 줄** 갱신 (추가/삭제/이름·서비스·읽음·상태 전부 흡수) | `{ op, conversation }` (아래) |
| `execution_progress` | EXECUTION | SIGNAL | 레시피 실행 스텝 진행 | `{ sessionId, executionId, stepIndex, status, summary }` |
| `execution_complete` | EXECUTION | DATA | 레시피 실행 종료 (결과/사유 포함) | `{ sessionId, executionId, outcome, retriable, failedStepIndex }` (아래 outcome) |
| `heartbeat` | SYSTEM | SIGNAL | 연결 유지용 | `{}` |

#### execution_complete.outcome (실행 종료 사유)

`success`/`failed` 이분법으로는 "사용자 취소 vs 중지 vs 서버 오류"를 구분할 수 없어, 종료 사유를 명시한다. FE는 이 값으로 **후속 액션 버튼**을 결정한다.

| outcome | 의미 | 유발 주체 | FE 액션 |
|---------|------|----------|---------|
| `SUCCESS` | 정상 완료 | 서버 | 완료 카드 ([결과 보기]) |
| `STOPPED` | 사용자 중지 | 사용자([중지]) | 중단 카드. 현재까지 진행분은 히스토리에 보존 |
| `CANCELLED` | 사용자 취소 | 사용자([취소]) | "취소되었습니다" 안내. 히스토리에 기록으로 남음 |
| `FAILED` | 실행 오류 | 시스템(스텝 실패/타임아웃 등) | 에러 카드 + `retriable`이면 [다시 실행] |

- **중지(STOPPED)와 취소(CANCELLED)는 상태로 구분해 기록한다.** 히스토리는 "무슨 일이 있었나"의 기록이므로 [중지]와 [취소]를 다른 사건으로 남긴다(사용자가 히스토리에서 구분해 봄 + 필터/집계 가능). 중단 시 실행의 `RESULT_SUMMARY`에 사유 + 완료 스텝 수를 자동 기록한다(예: "취소됨 · 1/3 스텝 완료"). 재개(이어서 실행) 로직의 세분은 그 기능 도입 시 다룬다.
- `retriable` (FAILED에만 의미): Transient 오류(타임아웃, 5xx 등)면 `true` → [다시 실행] 노출. 구조적 오류(스크립트 버그, 잘못된 레시피 정의)면 `false` → 재실행 버튼 숨김. 분류 기준: [error-handling.md](error-handling.md)
- `failedStepIndex` (FAILED/STOPPED): 실패/중단된 스텝 위치. 재개 지점(재개 기능은 추후)

### 대화방 처리 상태 (session_status.status)

여러 탭에서 같은 대화방을 열었을 때, 한 탭의 처리 상태를 다른 탭도 즉시 반영하기 위한 값. FE는 이 값으로 **입력 영역**을 렌더링한다.

| status | 의미 | 입력 영역 UI |
|--------|------|-------------|
| `idle` | 유휴 | 정상 입력 가능 |
| `ai_responding` | AI 응답 생성 중 | "⏳ 답변이 진행 중입니다" + 입력 잠금 |
| `executing` | 레시피/플랜 실행 중 | "⏳ 레시피 실행 중... [중지]" + 입력 잠금 |
| `input_waiting` | 사용자 입력 대기 | 액션 피커만 활성, 자유 채팅 잠금 |

- 상태 전이는 요청을 시작한 탭뿐 아니라 **모든 탭(같은 사용자 Global SSE)**에 전달됨
- 입력 잠금 이유를 입력 지점에 명시(옵션 2). 실행 상세는 채팅 영역의 진행 블록(execution_progress)으로 별도 표시

### 대화방 목록 갱신 (session_list_update)

대화방 목록 한 줄을 그리는 데 필요한 변경(추가/삭제/이름변경/서비스변경/읽음/상태)을 **하나의 이벤트로 통합**한다. 별도 read/rename 이벤트를 두지 않아 파편화를 막고, FE는 "이 대화방 한 줄을 통째로 교체(없으면 추가)/제거"만 하면 된다.

```json
{
  "op": "upsert | removed",
  "conversation": {
    "id": 123,
    "title": "회원가입 테스트",
    "apiSpecId": 1,
    "status": "idle | ai_responding | executing | input_waiting",
    "lastMessageAt": "2026-09-01T10:00:00",
    "unread": true,
    "updatedAt": "2026-09-01T10:00:00"
  }
}
```

| op | 의미 | conversation |
|----|------|--------------|
| `upsert` | 추가·갱신 통합 (생성/이름변경/서비스변경/읽음/상태변경) | 목록 한 줄 전체 스냅샷 |
| `removed` | 삭제 | `{ id }`만 |

**발행 시점 / 규칙**
- **추가(upsert)**: 대화방 row 생성 순간이 아니라 **첫 메시지 전송 시점**에 발행 (빈 대화는 목록에 안 쌓임 — overview.md)
- **읽음**: 대화방 진입 시 읽음 API → `LAST_READ_AT` 갱신 → `upsert`(unread=false)로 모든 탭 뱃지 동기화
- **삭제(removed)**: 모든 탭 목록에서 제거. **다른 탭이 방금 삭제된 대화방을 보고 있으면** "이 대화는 삭제되었습니다" 안내 + 목록으로 이동
- `session_status`(고빈도 처리상태)는 목록 이벤트와 **분리 유지** — 입력 영역 구동용. 목록 뱃지는 session_list_update의 `status`로 반영. 성격(저빈도 목록 vs 고빈도 상태)이 달라 분리

### 상태 해제 (취소 / 중지 / 완료)

`ai_responding` / `executing` / `input_waiting`를 벗어나 `idle`로 돌아가는 것은 **항상 서버가 판단하고 SSE로 전파**한다. FE가 임의로 잠금을 풀지 않는다.

| 트리거 | 경로 | 결과 |
|--------|------|------|
| 액션 피커 [취소] | **FE → 취소 API 호출** (`POST /api/v1/conversations/{id}/cancel`) | 서버가 대기/락 해제 → `session_status: idle` 전파 + "취소되었습니다" 메시지(message_new) |
| 실행 중 [중지] | **FE → 중지 API 호출** (`POST /api/v1/conversations/{id}/stop`) | 현재 스텝까지 저장 후 중단 → `execution_complete`(status에 중단 반영) + `session_status: idle` 전파 |
| 정상 완료 | 서버 내부 | `execution_complete` + `session_status: idle` 전파 |

**원칙**
- **취소/중지는 반드시 API 경유.** FE가 액션 피커만 닫으면 서버는 여전히 `input_waiting`이라, 다른 탭·새로고침 시 다시 잠긴 상태로 보인다.
- 상태 해제 이벤트는 **모든 탭에 전파**되어 함께 입력 잠금이 풀린다.
- **멱등**: 여러 탭에서 동시에 취소/중지를 호출해도 이미 `idle`이면 no-op으로 처리(에러 아님).
- **서버 기동 복구**: 인메모리 락은 재시작 시 사라지므로, 기동 시 `ai_responding`/`executing`로 남은 대화방 상태를 `idle`로 정리한다(락과 상태 불일치 방지). `input_waiting`은 사용자가 이어서 입력하거나 취소로 해제.
- **SSE 미연결 중 요청**: SSE가 끊긴 순간에도 메시지 전송/취소 등 REST 응답은 정상 동작하며, 상태 갱신은 재연결 후 `Last-Event-ID` replay로 복구된다.

### 재연결 정책

- 서버가 각 이벤트에 `id` 부여
- 연결 끊김 시 `EventSource` 자동 재연결
- 재연결 시 `Last-Event-ID` 헤더로 마지막 수신 ID 전달
- 서버가 해당 ID 이후 놓친 이벤트를 replay (버퍼 유지 시간: 5분)
- 5분 초과 시 replay 불가 → FE가 현재 대화방 상태를 REST API로 재조회

### Heartbeat

- 30초마다 heartbeat 전송
- **heartbeat는 SSE comment(`: ...` 라인)로 보낸다.** id/event 필드가 없어 `Last-Event-ID`를 오염시키지 않으며(데이터 이벤트 replay 기준점 보호), replay 버퍼에도 쌓지 않는다
- 클라이언트가 60초 이상 heartbeat 미수신 시 연결 끊김으로 판단 → 재연결

### FE 이벤트 처리 전략

| 이벤트 | 현재 보고 있는 대화방 | 다른 대화방 (같은 사용자 다른 탭 포함) |
|--------|---------------------|------------|
| `message_new` | 채팅에 즉시 렌더링 | 상태 뱃지(🔵) 업데이트 |
| `message_update` | 해당 메시지 즉시 갱신 | 무시 (진입 시 로드) |
| `session_status` | **입력 영역 상태 반영** (idle/ai_responding/executing/input_waiting) | 목록 뱃지 업데이트 |
| `session_list_update` | 보고 있는 방이 removed면 "삭제됨" 안내 후 목록 이동 | 목록에 upsert(교체/추가)/removed(제거) 반영 |
| `execution_progress` | 진행 상태 블록 갱신 | 무시 |
| `execution_complete` | 완료 메시지 + 카드 UI | 상태 뱃지(🔵) |

> **여러 탭 동기화**: 같은 대화방을 여러 탭에서 열어도 모두 같은 Global SSE로 `session_status`를 받으므로, 한 탭에서 실행/응답이 진행되면 **다른 탭의 입력 영역도 즉시 잠기고 이유가 표시된다.** (탭이 "현재 보고 있는 대화방"이면 입력 영역 반영, 아니면 목록 뱃지)

### 낙관적 UI (사용자 발신 메시지)

SSE 왕복을 기다리면 내 메시지가 화면에 늦게 뜨는 체감 지연이 있다. 전송 API는 거의 즉시 리턴하므로, **접수 성공 직후 임시 메시지를 표시**한다.

1. 전송 → 전송 API가 **2xx(접수됨)** 리턴하면, FE가 **임시 사용자 메시지(id=null)를 렌더** + 즉시 입력 잠금
2. 서버는 async 처리 → 확정 메시지를 `message_new`(실제 id/seq)로 SSE 발행
3. FE는 `message_new` 도착 시 **해당 대화방의 임시(id=null) 메시지를 전부 제거하고 확정본을 렌더**
   - 임시는 "확정본이 오면 대체될 자리 채우기"일 뿐이므로, 매칭 키 없이 null 전부 제거로 충분 (대화방 락이 "임시 최대 1개" 불변식 보장)
4. 순서는 `seq`(동률 시 createdAt) 기준 정렬 — AI 응답이 먼저 도착해도 화면 순서 안 꼬임

**적용 범위 / 예외**
- 낙관적 표시는 **접수 성공한 본인 사용자 메시지에만.** AI 응답·다른 탭·다른 대화방 메시지는 SSE 도착 시 표시(낙관적 대상 아님)
- **전송 API 실패**(4xx/5xx/네트워크): 임시 메시지를 아예 그리지 않음 → "전송 실패" 안내 + 입력 유지 (실패 임시 처리 문제가 원천 소거됨)
- 이중 전송은 대화방 락(session_status)이 막고, 접수 성공 직후 입력을 선제 잠가 중복 클릭도 차단

### 종결 보장 (termination guarantee)

무한 로딩을 막기 위한 핵심 원칙.

- **서버는 처리 결과가 성공이든 실패든 반드시 종결 이벤트를 SSE로 보낸다.** (AI 응답 완료 `message_new`, 실행 종료 `execution_complete`, 또는 오류 `system` 메시지) — 어떤 경우에도 대화방이 `ai_responding`/`executing`에 갇히지 않도록 마지막에 `session_status: idle`을 전파
- 처리 중 서버 예외/크래시로 종결 이벤트를 못 보낸 경우를 대비해, **FE는 응답 지연 타임아웃**(예: 일정 시간 내 관련 SSE 없음)을 두고 "응답이 지연됩니다. 새로고침 해주세요" 안내 + 입력 잠금 해제 여부는 재조회로 결정
- 서버 기동 시 `ai_responding`/`executing`로 남은 대화방을 `idle`로 정리(위 상태 해제 참조)

> **구현/배포 주의 (추후):**
> - heartbeat 전송 실패 시 해당 emitter를 즉시 제거 (좀비 커넥션·메모리 누수 방지)
> - 브라우저는 도메인당 SSE 동시 연결 6개 제한(HTTP/1.1) — 배포 시 HTTP/2 권장
> - 프록시(Nginx 등)는 `proxy_buffering off` + 유휴 타임아웃 상향 필요 (SSE 실시간성 보장)
> - SSE 토큰이 URL 쿼리에 노출됨(EventSource 제약) — 필요 시 SSE 전용 단기 토큰 검토

---

## 인증

SSE 연결 시 JWT 토큰을 URL 쿼리 파라미터로 전달:
```
GET /api/v1/sse/connect?token=eyJhbGciOiJ...
```

`EventSource`는 커스텀 헤더를 지원하지 않으므로 쿼리 파라미터 방식 사용.
토큰 만료 시 서버가 연결 종료 → 클라이언트가 토큰 갱신 후 재연결.
