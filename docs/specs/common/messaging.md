---
status: draft
last-updated: 2026-09-08
---

# 메시징 및 SSE 이벤트 정의

## 개요

- 사용자 → 서버: POST API로 메시지 전송
- 서버 → 클라이언트: Global SSE 1개로 모든 이벤트 수신
- 메시지 포맷: 구조화된 JSON (type + content + payloadJson)
- 텍스트 포맷: Markdown 기본

### 저장/전달 내부 계약 (FE/BE 공유)

> 대화는 **턴(MESSAGE)** 의 나열이고, 한 턴은 **순서 있는 파트(MESSAGE_PART) 배열**로 구성된다.
> 한 턴 = 사용자 발화 1개 또는 AI 응답 1턴이다. 화면에 뜨는 모든 것(텍스트, 실행 진행/결과, 카드,
> 액션 피커, 참고 자료)은 그 턴 안의 **파트**로 저장된다.
> 새로고침 시 **메시지(턴+파트) 로드만으로** 화면을 복원한다(FE 메모리 의존 없음).
> 파트 갱신은 **그 턴을 `message_update`로 갱신**한다 — 갱신 payload는 항상 **그 턴의 파트 배열 전체 스냅샷**이다(파트 delta 아님, 멱등).

- **턴(MESSAGE)** 은 대화 타임라인·AI 컨텍스트의 축이다. **파트(MESSAGE_PART)** 는 화면 렌더 단위이자 순서 있는 블록이다(동종 파트 N개 허용: TEXT 여러 개, 한 턴에 실행 여러 번 등).
- 실행/조회의 **사실**은 [EXECUTION 계층](#execution-계층사실히스토리)·[INVESTIGATION 계층](#investigation-계층조회-사실)에 정규화되고, 파트는 이를 **참조(FK)** 만 한다. 파트는 "무엇을 어디에 그릴지", 실행/조회 계층은 "무슨 일이 있었나(감사·재개·분석)"를 담당한다.
- 로직은 파트의 `payloadJson`(및 EXECUTION/INVESTIGATION)을 진실로 사용하고, `content`는 표시·미리보기·검색·폴백 전용이다.
- **정렬·커서는 ID(auto-increment) 단독**이다. 턴은 대화방 내 `MESSAGE.ID` 오름차순, 파트는 턴 내 `MESSAGE_PART.ID` 오름차순(생성순=표시순). 별도 SEQ를 두지 않는다([execution.md 커서 원칙](../../db/execution.md)과 일관).

---

## 메시지 JSON 구조

### 서버 → 클라이언트 (턴 + 파트)

한 턴(MESSAGE)은 파트(MESSAGE_PART) 배열을 품는다. FE는 턴을 `id` 오름차순으로, 각 턴 안 파트를 `id` 오름차순으로 렌더한다.

```json
{
  "id": 123,
  "sessionId": 1,
  "role": "assistant",
  "status": "streaming | complete | failed",
  "createdAt": "2026-08-27T14:30:00",
  "parts": [
    { "id": 1001, "type": "text", "status": "complete",
      "content": "회원가입 5건 만들어 드릴게요." },
    { "id": 1002, "type": "progress", "status": "running",
      "executionId": 456, "payloadJson": { "kind": "progress", "schemaVersion": 2, ... } },
    { "id": 1003, "type": "result", "status": "complete",
      "executionId": 456, "payloadJson": { "kind": "result", "schemaVersion": 2, ... } },
    { "id": 1004, "type": "text", "status": "complete",
      "content": "5건 모두 성공했습니다. 추가로 로그인 테스트도 할까요?" }
  ]
}
```

- **턴 필드**: `id`(정렬·커서 기준), `role`(user/assistant/system), `status`(턴 전체 상태), `createdAt`, `parts[]`. 사용자 발화 턴은 참조 태그가 있으면 `referenceId`를 가진다.
- **파트 필드**: `id`(턴 내 순서), `type`(아래 파트 타입), `status`(파트 상태), 그리고 타입에 따라 `content`(TEXT) / `executionId`(실행류) / `investigationId`(조회류) / `cardType`(카드) / `payloadJson`(잔여 구조화 데이터).
- **정렬**: FE는 SSE 도착 순서가 아니라 **턴 `id`, 파트 `id`** 로 정렬한다. 낙관적 표시/SSE 순서 뒤바뀜에도 화면 순서가 안 꼬인다.

### 파트 타입 (PartType)

`TEXT` / `CARD` / `PROGRESS` / `RESULT` / `INVESTIGATE` / `ACTION_PICKER` / `REFERENCES`

| type | 설명 | 참조/페이로드 |
|------|------|--------------|
| `TEXT` | 텍스트 블록(발화 본문). AI 한 턴에 여러 개 가능(실행 앞뒤 설명 등) | `content` (Markdown) |
| `CARD` | 카드 UI(플랜 제안/후보/서비스 선택/인증/재시도) | `cardType` + `payloadJson` |
| `PROGRESS` | 레시피 실행 진행 블록 | `executionId` + `payloadJson`(kind:progress) |
| `RESULT` | 레시피 실행 결과 블록 | `executionId` + `payloadJson`(kind:result) |
| `INVESTIGATE` | 정보 조회 진행 블록 | `investigationId` + `payloadJson`(kind:investigate_progress) |
| `ACTION_PICKER` | 액션 피커(구조화 입력) | `executionId`(pre-run 수집 시 실행 생성 후 채워짐) + `payloadJson`(variables·stepIndex 등) |
| `REFERENCES` | 조회 참고 자료(출처 칩) | `payloadJson`(kind:references) |

> **THINKING(모델 추론)은 파트로 저장하지 않는다.** 현재 reasoning을 화면에 실시간 노출하는 기능이 없고, 저장 실익(재현·분석)이 없으며 AI 컨텍스트에도 재주입하지 않는다. 추후 reasoning 스트리밍을 도입하면 그때 **저장하지 않는(ephemeral) 파트**로 추가한다.

### 파트 STATUS 상태머신 (타입별)

| 파트 성격 | 타입 | 상태 흐름 |
|-----------|------|-----------|
| 스트리밍 | TEXT | STREAMING → COMPLETE / FAILED |
| 실행/조회 | PROGRESS / RESULT / INVESTIGATE | 참조 대상(EXECUTION/INVESTIGATION) 상태를 따름 |
| 인터랙티브 | CARD(plan/candidates 등) / ACTION_PICKER | PENDING → CONSUMED / CANCELLED |
| 정적 | REFERENCES | COMPLETE |

- 인터랙티브 파트의 `CONSUMED`는 "사용자가 이미 이 카드/피커에 응답했다"는 뜻이다. 새로고침 후에도 이 상태로 복원되어 "이미 실행한 카드"가 다시 활성화되지 않는다.

### content vs payloadJson (파트 단위 이원화)

파트의 페이로드는 두 필드로 나뉜다(기존 이원화 원칙을 파트 단위로 승계).

| 필드 | 성격 | 용도 |
|------|------|------|
| `content` | 사람이 읽는 **표시용 텍스트**(Markdown) | TEXT 파트의 본문. 표시·검색·폴백 |
| `payloadJson` | 유형별 **구조화 데이터**. 화면/로직의 **진실** | FE 렌더링·상태 판정, BE 로직 |

- 로직은 `payloadJson`(및 EXECUTION/INVESTIGATION)을 진실로 사용한다. **`content`를 파싱해 상태를 판정하지 않는다.**
- `TEXT`처럼 표시 텍스트가 본질인 파트는 `payloadJson`이 없다(`content`가 곧 데이터).
- 실행/조회류 파트는 `executionId`/`investigationId`로 사실 계층을 가리키고, `payloadJson`은 그 시점 렌더 스냅샷을 담는다.
- **턴 미리보기**: 목록/검색용 요약은 턴의 `contentPreview`(파트에서 파생한 캐시)로 제공한다. 진실이 아니라 캐시이며, 파트가 바뀌면 갱신한다.

### payloadJson 공통 필드

모든 `payloadJson`은 아래 공통 필드로 시작한다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `kind` | string | payload 판별자. 유형별 스키마를 고르는 키 (`progress` / `result` / 카드는 `cardType`) |
| `schemaVersion` | integer | payload 스키마 버전. **1부터** 시작, 스키마 변경 시 증가 |

- **버전 폴백**: FE는 자신이 아는 최대 `schemaVersion`보다 **상위 버전**을 받으면 payloadJson 렌더를 포기하고 **`content`로 폴백**한다(깨지지 않게). 하위/동일 버전은 정상 렌더.

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
- 응답: `{ items, nextCursor, hasNext }` (커서 페이지). `items`는 **최신순(턴 `id` DESC)**, 각 턴은 `parts[]`를 포함
- 정렬/커서 = 턴 `MESSAGE.ID`(auto-increment·유일). 채팅은 최신이 아래이고 위로 스크롤하면 과거를 불러오므로 "다음 페이지 = 과거"다. `cursor`는 직전 페이지의 가장 과거 턴 `id`(응답의 `nextCursor`)를 그대로 전달
- `size` 기본 20, 최대 50 (과도 로딩 방지). **파트는 턴에 종속**되므로 페이징 단위는 턴이다(파트 수와 무관하게 턴 N개).
- **FE 렌더**: 진입 시 첫 페이지(최신 N턴)를 받아 **턴 `id` 오름차순으로 뒤집어** 표시(최신이 아래). 위로 스크롤 시 `nextCursor`로 과거 페이지를 이어 로드해 위쪽에 prepend. 정렬 기준은 항상 턴 `id`(위 낙관적 UI와 동일)
- `cursor`는 **엔드포인트별 불투명 값**이다(메시지=턴 id, 실행 히스토리=실행 id). FE는 내부 형식을 해석하지 말고 그대로 다음 요청에 전달

---

## 턴과 파트의 대응 (서버 → 클라이언트)

서버가 만드는 파트는 위 [파트 타입](#파트-타입-parttype)을 따른다. 한 AI 턴은 상황에 따라 여러 파트를 순서대로 append한다. 예:

- 단순 답변: `[TEXT]`
- 조회 답변: `[INVESTIGATE, TEXT(+REFERENCES)]`
- 레시피 실행: `[TEXT, PROGRESS, RESULT, TEXT]`
- 한 턴 다중 실행: `[TEXT, PROGRESS(exec A), RESULT(A), PROGRESS(exec B), RESULT(B)]`
- 카드 실행: `[CARD(execution_mode)]` → 사용자가 [바로 실행] 누르면 **그 카드 파트가 속한 턴에** PROGRESS/RESULT를 이어 append(카드 CONSUMED) → 한 턴 = `[CARD, PROGRESS, RESULT]`. 플랜 카드([자동 실행])도 동일하게 그 카드 턴에 PROGRESS/RESULT를 붙인다.

> **한 턴 귀속 규칙 (촉발 파트의 턴):** 실행의 진행/결과(PROGRESS/RESULT)는 **그 실행을 촉발한 파트(`EXECUTION.TRIGGER_PART_ID`, 예: execution_mode·plan 카드 파트)가 속한 턴에 append**된다. 그 결과 카드와 진행/결과가 **하나의 AI 턴(아바타 1개)** 으로 묶여 `[CARD, PROGRESS, RESULT]`로 렌더된다. 촉발 파트가 없는 실행(대화 없이/직접 실행 등)은 새 ASSISTANT 턴을 만들어 append(폴백). 재개(이어서 실행/respond)는 새 진행 블록이므로 새 턴을 만든다. 진행 블록 자체(PROGRESS 파트)는 `executionId`로 실행을 정참조하며(갱신/RESULT append 시 executionId로 역조회), `TRIGGER_PART_ID`는 촉발 카드 파트를 가리키는 값으로 유지한다(PROGRESS 파트 id로 덮어쓰지 않음). 대화방 단위 락으로 한 대화방 동시 실행은 1개이며, 한 발화가 여러 실행을 한 턴에 묶는 형태(exec A/B)는 순차 다중 실행 도입 시 활성화한다.

- **SYSTEM 안내**(취소/중지, 대상 서비스 설정/해제 등)는 `role=system` 턴 + `TEXT` 파트 1개로 표현한다(별도 SYSTEM 파트 타입 없음). 서비스 설정 알림은 `PATCH /conversations/{id}/service` 성공 시 발행되며, 카드 선택·패널 드롭다운 어느 경로든 동일하게 남는다(새 대화 미생성 상태는 제외 — 대화방이 없어 남길 곳이 없고 pending으로만 보관).
- **빈 ASSISTANT 턴**: AI 응답을 시작할 때 `status=streaming`인 빈 턴을 먼저 만들고 파트를 append한다(기존 "PENDING 자리 미리 INSERT" 패턴의 대체). 완료 시 턴 `status=complete`.

## 클라이언트 → 서버 (사용자 액션)

사용자 입력은 두 종류다. **자유 텍스트만 새 USER 턴을 만들고**, 버튼/피커 응답은 새 턴을 만들지 않고 **해당 인터랙티브 파트를 소비(CONSUMED)** 시킨 뒤 후속 AI 턴으로 이어진다.

| 입력 | 처리 | 엔드포인트 |
|------|------|-----------|
| 자유 텍스트 발화 | 새 USER 턴(TEXT 파트) 생성 | `POST /conversations/{id}/messages` |
| 액션 피커 값 제출 | 대상 ACTION_PICKER 파트 CONSUMED + 실행 재개 | `POST /action-picker/respond` |
| execution_mode 카드 [바로 실행]/[값 확인 후 실행] | 카드 파트 CONSUMED + 실행 시작 | `POST /conversations/{id}/executions` (mode=AUTO/MANUAL) |
| plan 카드 [자동 실행] | 카드 파트 CONSUMED + 플랜 실행 | `POST /conversations/{id}/plan-executions` |
| candidates 카드 후보 선택 | 카드 파트 CONSUMED + 실행 시작 | `POST /conversations/{id}/executions` |
| service_select 카드 | 카드 파트 CONSUMED + 대화방 서비스 설정 + **SYSTEM 알림 턴** append("대상 서비스가 'XX'(으)로 설정되었어요") | `PATCH /conversations/{id}/service` |
| 실패/중단 카드 [이어서 실행] | 재개 | `POST /executions/{id}/resume` |
| [취소] / [중지] | 실행 취소/중지 | `POST /conversations/{id}/cancel` / `/stop` |

- 버튼/피커 응답 요청 본문에는 **대상 파트를 특정하는 `partId`** 를 포함한다(서버가 그 파트를 CONSUMED로 전이). 나머지 필드(recipeId/mode/values 등)는 기존 계약 유지.
- 별도의 통합 `/actions` 엔드포인트는 두지 않는다 — 위 기존 분화 엔드포인트를 유지하고 각 API가 촉발 파트 CONSUMED 처리를 더한다.

---

## 유형별 payloadJson 스키마

### PROGRESS (실행 진행 블록)

실행 시작 시 AI 턴에 PROGRESS **파트** 1개가 append되고, 스텝 진행마다 `message_update`로 **그 턴을 갱신**한다(갱신 payload는 턴의 파트 배열 전체 스냅샷). 파트는 `executionId`로 [EXECUTION](#execution-계층사실히스토리)을 가리킨다.

payload는 **레시피 그룹 구조**다(플랜 진행 카드 = chat.html Case 12 정본: 레시피 그룹 단위 + 완료 접힘 + 현재만 스텝 펼침 + k/N 레시피). **단일 실행(N=1)도 `recipes` 1개로 통일**한다 — FE는 단일/플랜을 동일 구조로 렌더하되 표시만 분기한다(단일이면 그룹 헤더 없이 스텝을 바로 노출).

```json
{
  "kind": "progress",
  "schemaVersion": 2,
  "executionId": 456,
  "title": "입사지원",
  "overallStatus": "running | success | partial | failed | stopped | cancelled",
  "recipeProgress": { "current": 2, "total": 3 },
  "recipes": [
    {
      "sequence": 0,
      "recipeName": "이력서 작성",
      "status": "pending | running | success | skipped | failed | stopped | cancelled",
      "summary": "이력서 ID: RES-001",
      "steps": [
        { "index": 0, "name": "로그인 확인", "status": "pending | running | success | failed | skipped", "summary": "..." }
      ]
    }
  ]
}
```

- `title`: 실행 제목(플랜명 또는 단일 레시피명). 플랜 진행 헤더("📋 {title} 플랜 실행 중 (k/N 레시피)")에 쓰인다.
- `overallStatus`: 실행 전체 상태. 완료 시 `success`/`partial`/`failed` 등으로 확정.
- `recipeProgress`: **레시피 단위 진행률**(k/N 레시피 표기용). `total`=전체 레시피 수, `current`=종료(성공/스킵/실패/중지/취소)된 레시피 + 현재 `running` 레시피(= "완료+현재"). 예) 1개 완료 + 1개 진행 중 = `current:2`.
- `recipes[]`: 레시피 그룹. `sequence`(플랜 내 순서, 0-base), `recipeName`(실행 시점 스냅샷 이름), `status`(레시피 상태 소문자 코드), `summary`(완료 접힘 표시용 결과 한 줄, 없으면 `null`), `steps[]`.
- `recipes[].summary`: 레시피 결과값(RESULT_VALUES_JSON)의 스칼라 값을 `·`로 이어 붙인 한 줄(값 위주, 표시명은 RESULT payload가 별도 제공). 완료 레시피 접힘 헤더에 노출된다.
- `steps[].status`: 스텝별 상태. `content`에는 이 구조에서 파생한 표시용 진행 요약(Markdown)을 담는다.
- `steps[].name`: 스텝 표시명. 서버가 [표시명 폴백 체인](../recipe/structure.md#표시명label-폴백-체인)((1) 스텝 표시명 → (2) 엔드포인트 summary → (3) method+path)으로 미리 결정해 채운 사람말 이름을 담는다(FE 추가 폴백 불필요). **이 값은 실행 시점에 확정되어 PROGRESS 파트 payload에 저장되므로**, label 없이 summary로 폴백된 경우라도 히스토리 재현·새로고침 복원 시 조회 당시 summary가 아니라 **그때 그 실행 시점의 이름이 고정 표시**된다([structure.md 스냅샷 포함](../recipe/structure.md#스냅샷-포함)).
- **`content` 텍스트 분기**: 플랜(N≥2)이면 "{title} 플랜 실행 중 (k/N 레시피)"(레시피 단위), 단일(N=1)이면 "{레시피명} 실행 중 (k/N)"(스텝 단위, 기존 관측 유지).

### RESULT (실행 결과 블록)

실행 완료 시 AI 턴에 append되는 결과 **파트**. PROGRESS 파트와 같은 `executionId`를 공유한다(한 실행 = progress·result 파트).

payload는 **레시피별 구조**다(플랜 결과 카드 = chat.html Case 21 정본: 레시피별 결과 한 줄). **단일 실행(N=1)도 `recipes` 1개로 통일**한다.

```json
{
  "kind": "result",
  "schemaVersion": 2,
  "executionId": 456,
  "title": "입사지원",
  "overallStatus": "success | partial",
  "recipes": [
    {
      "sequence": 0,
      "recipeName": "입사지원",
      "status": "success | skipped | failed | stopped | cancelled",
      "resultValues": { "applicationId": "A-123", "status": "제출완료" },
      "resultLabels": { "applicationId": "지원번호" },
      "summary": "지원이 완료되었습니다 (번호: A-123)"
    }
  ]
}
```

- `title`: 실행 제목. 플랜 결과 헤더("📋 플랜 완료 (N/N)")·단일 결과 표시에 쓰인다.
- `overallStatus`: 실행 전체 최종 상태(`success`/`partial`). RESULT 파트는 정상 종료(SUCCESS/PARTIAL)에서만 append된다(FAILED는 결과 미발행).
- `recipes[]`: 레시피별 결과. `sequence`(0-base), `recipeName`(스냅샷 이름), `status`(레시피 상태 소문자 코드), `resultValues`, `resultLabels`(선택), `summary`(레시피별 결과 요약 텍스트 = 템플릿 치환 결과 또는 최소 요약).
- `recipes[].resultValues`: ④ 결과 정의로 추린 결과 값(진실). key는 결과 정의 변수명(원본 key) 그대로. 그 레시피 스냅샷 정의 기준으로 산출한다. 값은 스칼라(문자열/숫자/불리언)뿐 아니라 **배열/객체도 가능**하다(목록 조회 결과 등). 배열/객체 값은 `summary`(마크다운) 렌더 시 `{{#each}}`로 표시되고, 사이드 패널 상세 드릴다운에서도 노출된다.
- `recipes[].resultLabels`: 결과 key → 표시명(사람말) 맵. **결과 정의(④)에 `label`이 등록된 key만 포함**한다(선택). 값 자체(`resultValues`)와 표기(`resultLabels`)를 분리해, 스키마를 깨지 않고 표시명을 동반한다.
- **표시명 폴백**: FE는 값을 표기할 때 `resultLabels[key]`가 있으면 표시명, 없으면 **원본 key 그대로** 쓴다. 중첩/배열 key(`items[0].price`)는 label 없으면 key 경로 그대로, 값이 없거나 null이면 "값 없음"으로 표시한다(폴백 체인: [structure.md](../recipe/structure.md#표시명label-폴백-체인)).
- `recipes[].summary`: 그 레시피의 결과 요약 텍스트. 레시피 스냅샷 `resultTemplate`이 있으면 **Handlebars로 렌더한 마크다운 문자열**(값 치환 `{{key}}` + 반복 `{{#each}}` + 조건 `{{#if}}` + 헬퍼), 없으면 최소 요약(값 나열). 서버가 실행 완료 시 1회 렌더해 저장하며, FE는 이 값을 **마크다운으로 렌더**한다(표/리스트/강조). 생성 방식은 [execution.md 실행 완료/결과 요약](../recipe/execution.md#실행-완료--결과-요약), 템플릿 문법은 [authoring.md ⑤ 결과 메시지 템플릿](../recipe/authoring.md#-결과-메시지-템플릿) 참조.
- **`content` 텍스트 분기**: 플랜(N≥2)이면 "{title} 플랜 N개 레시피를 완료했습니다" + 레시피별 한 줄("✓ 1. 이름 — 결과") 나열, 단일(N=1)이면 그 레시피의 `summary`를 그대로 담는다. `content`/`summary`는 **마크다운**이며 FE가 마크다운으로 렌더한다(rehype-sanitize로 HTML/스크립트 차단).
- 표시명은 사람말 요약(`content`/`summary`)을 보강할 뿐, 상세·히스토리에서 원본 key 노출을 막지 않는다([파트 단위 이원화](#content-vs-payloadjson-파트-단위-이원화) 유지).

### INVESTIGATE (정보 조회 진행 블록)

정보 조회(investigate) 루프 시작 시 AI 턴에 INVESTIGATE **파트** 1개가 append되고, 소스별 조회 단계마다 `message_update`로 **그 턴을 갱신**한다(레시피 실행 PROGRESS 패턴 재사용). 파트는 `investigationId`로 [INVESTIGATION](#investigation-계층조회-사실)을 가리킨다(조회도 정규 계층으로 저장 — 실행과 형제). 조회는 실행(EXECUTION)이 아니므로 `executionId`는 없다.

```json
{
  "kind": "investigate_progress",
  "schemaVersion": 1,
  "investigationId": 789,
  "status": "running | done | failed | timeout",
  "steps": [
    { "source": "api_spec", "query": "회원가입", "status": "running | success | failed | skipped" }
  ]
}
```

- `status`: 루프 전체 상태. `done`(정상 종료, 최종 답변 별도 발행) / `failed`(전 커넥터 실패 등) / `timeout`(120초 초과). **비정상 종료(예외/타임아웃) 시 반드시 `failed`/`timeout`으로 확정하며 `running` 잔존을 두지 않는다**(finally에서 확정 `message_update` 발행 — 새로고침 시 유령 진행 블록 방지).
- `steps[]`: 조회 단계. 각 항목은 `source`(`api_spec` / `confluence`) / `query`(조회 질의) / `status`.
  - `skipped`: 조회를 실제 수행하지 못한 경우 — 미등록 커넥터 source, confluence인데 서비스 `confluenceSpaceKey` 미연결, 또는 중복 `(source, query)` 캐시 재사용. **`skipped`도 조회 카운터를 소비한다**(카운터 우회 차단 — [investigation.md 루프 카운터 정의](../chat/scenarios/investigation.md#루프-카운터-정의-종료-보장-봉인)).
- `content`에는 이 구조에서 파생한 표시용 진행 요약(Markdown, 예: "🔍 API 스펙 조회 중 — 회원가입")을 담는다.
- **최종 답변은 이 INVESTIGATE 파트를 갱신하지 않는다.** 완료 시 파트 `status`를 `done`으로 확정하고, 같은 AI 턴에 답변 `TEXT` 파트 + (출처 있으면) `REFERENCES` 파트를 append한다(별도 턴이 아니라 같은 턴의 후속 파트).
- 조회의 사실(질의·소스·스텝·답변 요약)은 [INVESTIGATION 계층](#investigation-계층조회-사실)에 정규 저장된다. 파트의 payload는 렌더 스냅샷, INVESTIGATION은 분석·감사용 사실이다.
- 상세 흐름: [investigation.md 진행 상태 표시](../chat/scenarios/investigation.md#진행-상태-표시-sse).

### references (정보 조회 참고 자료)

investigate 답변 뒤에 붙는 `REFERENCES` 파트의 `payloadJson`. 파트로 저장되어 **새로고침 시 복원**된다. (조회 스텝별 원본 출처는 INVESTIGATION_STEP에도 남지만, 화면 칩 렌더용 스냅샷은 이 파트 payload가 담당한다.)

```json
{
  "kind": "references",
  "schemaVersion": 1,
  "references": [
    { "source": "api_spec", "label": "POST /api/v1/users", "url": "/specs/1/endpoints/42" }
  ]
}
```

- `references[]`: 조회한 소스의 원본 링크. `source`(`api_spec` / `confluence`) / `label`(버튼 표시명) / `url`(클릭 대상).
- **`url`은 내부/외부 두 형태이며 FE가 형태로 동작을 분기한다:**
  - **내부(`/specs/{apiSpecId}/endpoints/{endpointId}`, `api_spec`)** → 라우트 이동이 아니라 **그 자리(채팅 인라인)에서 엔드포인트 상세 아코디언을 펼치는 식별자**다. FE는 `url`에서 `apiSpecId`·`endpointId`를 파싱해 `GET /api/v1/specs/{apiSpecId}`로 상세를 조회한다.
  - **외부(http/https, `confluence`의 `{CONFLUENCE_BASE_URL}/wiki/spaces/{KEY}/pages/{id}`, 추후 figma)** → **새 탭으로 연다**(`target="_blank"` + `rel="noopener noreferrer"`). 인라인 확장 아님.
- `label`: `api_spec`은 method+path(예: `POST /api/v1/users`), `confluence`는 페이지 제목.
- 조회한 소스가 없으면 references payload 없이 순수 `TEXT`로 발행한다(참고 자료 섹션 미표시).
- FE 렌더: 카드 UI [참고 자료형](../chat/card-ui.md#참고-자료형-상세)으로 답변 하단에 칩 리스트 표시. `api_spec` 칩은 인라인 아코디언, `confluence` 칩은 새 탭(위 url 형태 분기).

### CARD

카드는 기존 `cardType` 구조를 그대로 유지하되 **`payloadJson` 안에 담긴다**(공통 필드 `kind:"card"` + `schemaVersion` + `cardType` + 유형별 필드). 유형별 필드는 아래 카드 UI 상세 참조.

---

## 카드 UI payloadJson 상세

아래 필드는 `payloadJson`의 `cardType`별 추가 필드다(공통 `kind:"card"` + `schemaVersion` 위에 얹힘). `execution_mode`는 기존 `recipeName`/`description`/`inputVariables`/`buttons` 구조를 그대로 유지한다.

| cardType | 용도 | 추가 필드 |
|----------|------|-----------|
| `execution_mode` | 실행 모드 선택 | `recipeId`, `recipeName`, `description`, `inputVariables: [{ key, label, value, source, required }]`, `buttons: ["auto", "manual"]` |
| `plan` | 플랜 제안 (읽기 전용 미리보기) | `rationale`, `recipes: [{ recipeId, name, serviceName, previewValues: [{ key, label, value, source }] }]`, `buttons: ["cancel", "auto"]` |
| `result` | 실행 결과 보기 | `recipeId`, `executionId`, `timestamp` |
| `retry` | 실패 후 재시도 | `executionId`, `failedStepIndex` |
| `auth_required` | 인증 필요 | `loginPageUrl`, `executionId` |
| `candidates` | 유사 레시피 후보 | `recipes: [{ id, name, description }]` |
| `service_select` | 서비스 선택 | `services: [{ name, label }]` |
| `references` | 정보 조회 참고 자료 (investigate 출처 인용) | `references: [{ source, label, url }]` (별도 `REFERENCES` 파트의 `kind:"references"` payload — 위 [references 스키마](#references-정보-조회-참고-자료)) |

### execution_mode 카드 상세

"뭘 실행하는지, 어떤 값이 필요한지"를 카드에서 바로 보여준다(버튼만 노출하지 않는다).

| 필드 | 설명 |
|------|------|
| `recipeName` | 실행할 레시피명 (카드 상단 제목) |
| `description` | 레시피 한줄 설명 |
| `inputVariables` | 실행에 필요한 값 목록. 각 항목: `{ key, label, value, source, required }` |
| `buttons` | `["auto", "manual"]` — FE 라벨 매핑은 아래 |

**inputVariables 항목 필드**

| 필드 | 설명 |
|------|------|
| `key` | 입력 변수 키 (`{{userInput.X}}`의 X) |
| `label` | 표시 라벨 |
| `value` | 현재 채워진 값 (미충족이면 null) |
| `source` | 값 출처: `utterance`(🗣️ 발화) / `default`(📌 기본값) / `none`(✏️ 미입력) |
| `required` | 필수 여부 |

- 값이 채워진 항목: `label`, `value`, 출처 아이콘 표시 (🗣️ 발화 / 📌 기본값)
- 미충족 필수 항목: 값 자리에 **"(입력 필요)"** 표시 (✏️ 미입력)
- 참고: execution_mode 카드는 단일 레시피 실행 직전(pre-run) 수집이라 값 출처는 발화/기본값/미입력 3종만 쓴다. [플랜](../recipe/plan.md)의 값 출처 뱃지 중 🔗 이전 결과는 스텝 간 데이터가 있는 플랜 실행에만 해당하며 이 카드에는 나타나지 않는다. ✏️ 미입력(source=`none`)은 "아직 안 채워짐(입력 필요)"을 뜻하며, plan.md의 ✏️ 직접 입력(값 소스로서 키보드 입력)과는 의미가 다르다.

**buttons 라벨 매핑** (동작 정의는 [execution.md 실행 모드](../recipe/execution.md#실행-모드) 참조)

| button 값 | FE 라벨 | 동작 |
|-----------|---------|------|
| `auto` | **바로 실행** | 발화/기본값으로 즉시 진행 |
| `manual` | **값 확인 후 실행** | 액션 피커로 전체 값 확인·수정 후 진행 |

### plan 카드 상세

플랜 제안(`propose_plan`)을 인터랙티브 카드로 보여준다. 스킵·순서변경·값 사전 편집이 확정되어 있다([plan-proposal.md 편집 상태 카드](../chat/scenarios/plan-proposal.md#편집-상태-카드-스킵-반영)). 레시피 추가/제거는 [2단계 백로그](../chat/scenarios/plan-proposal.md#2단계-백로그-여기서-미구현).

| 필드 | 설명 |
|------|------|
| `rationale` | AI가 이 조합을 제안한 짧은 한국어 근거 (`propose_plan.rationale`). 카드 상단 💡로 노출 |
| `recipes` | 순서대로 실행할 레시피 목록. 각 항목: `{ recipeId, name, serviceName, previewValues, variables }` |
| `previewValues[]` | 각 레시피의 값 미리보기(아코디언 접힘 상태). `{ key, label, value, source }` |
| `variables[]` | 각 레시피의 전체 변수 정의. `{ name, label, type, required, default, options }` — [값 지정] 아코디언 편집 폼을 별도 조회 없이 그린다 ([plan.md 변수 스키마 전달](../recipe/plan.md#결정-3-변수-스키마를-카드-payload에-전달)) |
| `buttons` | `["cancel", "auto"]` — [취소](실행 안 함) / [자동 실행](플랜 실행 진입) |

- **`previewValues[].source` (정직화):** 제안 시점엔 **레시피 기본값만** 확정 표시한다. `default`(📌 기본값) / **`runtime`(값 미정 — `value`는 null)** 2종만 사용한다. `runtime`의 화면 표기는 **"실행 중 결정" 하나로 통일**한다("실행 중 입력"과 혼용하지 않음). **발화 추출값(🗣️)은 제안 카드에 표시하지 않는다**(레시피별 분배 규칙이 1단계 범위 밖 — [plan-proposal.md 값 미리보기](../chat/scenarios/plan-proposal.md#값-미리보기-결정-1--정직하게-축소)). 🗣️ 발화·🔗 이전 결과는 **실행 중 액션 피커**에서만 등장([plan.md 데이터 자동 채움](../recipe/plan.md#데이터-자동-채움-우선순위-실행-중-액션-피커)).
- **`variables`는 카드 payload에 자족적으로 실린다** — FE는 사용자가 [값 지정] 아코디언을 펼칠 때 이 정의로 편집 폼을 그리며, 편집값은 `startPlan` 요청의 `recipeInputs`로만 반영된다(카드 payload 자체는 불변, 새로고침 시 제안 원본 복원).
- 편집(스킵/순서변경/값 사전 편집)·실행 승인([자동 실행]) 이후의 오케스트레이션·진행/실패/중단은 [plan.md](../recipe/plan.md) 참조.

---

## SSE 이벤트

### 연결

- 엔드포인트: `GET /api/v1/sse/connect`
- **세션 쿠키로 자동 인증**(EventSource `withCredentials: true`) — 쿼리 토큰/유저ID 없음. `userId`는 세션에서 도출 (상세: [인증](#인증))
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
| `CHAT` | 대화 메시지/카드 (진행/결과 블록, 액션 피커·인증 카드 포함) | ✅ |
| `SESSION` | 대화방 상태/목록 | ✅ |
| `SYSTEM` | 시스템/연결 수준 신호 (heartbeat + 추후 공지/토큰만료) | ✅ |
| `NOTIFICATION` | 알림센터 | ⬜ 예약 (추후) |

> **실행 진행/완료는 별도 EXECUTION 이벤트가 아니라 CHAT 메시지로 흐른다.** 진행 블록·결과 블록이 각각 MESSAGE(PROGRESS/RESULT)이므로, 그 생성·갱신은 `message_new`/`message_update`로 전달된다. (기존 `execution_progress`/`execution_complete` 커스텀 이벤트는 폐지 — 아래 이벤트 타입 참조)

### 이벤트 타입

| event | category | nature | 설명 | data 구조 |
|-------|----------|--------|------|-----------|
| `message_new` | CHAT | DATA | 새 턴 도착 | 턴 JSON 전체(`parts[]` 포함) |
| `message_update` | CHAT | DATA | 기존 턴 갱신 (파트 append/상태 변경 — 진행 블록 갱신 등) | `{ sessionId, messageId, message: {...parts 전체 스냅샷} }` |
| `session_status` | SESSION | SIGNAL | 대화방 **처리 상태** 변경 (입력 영역 구동, 고빈도) | `{ sessionId, status }` (아래 상태값) |
| `session_list_update` | SESSION | SIGNAL | 대화방 **목록 한 줄** 갱신 (추가/이름·서비스·읽음·상태 흡수 — upsert 전용) | `{ op, conversation }` (아래) |
| `session_deleted` | SESSION | SIGNAL | 대화방 **삭제**. 보고 있던 탭은 홈으로 이탈 + 안내, 목록은 재조회로 제거 | `{ conversationId }` |
| `heartbeat` | SYSTEM | SIGNAL | 연결 유지용 | `{}` |

> **폐지**: 기존 `execution_progress` / `execution_complete`(EXECUTION 카테고리)는 사용하지 않는다. 실행 진행/완료는 아래 "실행 SSE 흐름"대로 PROGRESS/RESULT 파트의 `message_new`/`message_update`(턴 갱신)로 대체된다.

### 실행 SSE 흐름 (message_new / message_update)

실행 진행·결과가 메시지로 저장·복원되므로, 실행 이벤트도 CHAT 메시지 이벤트로 흐른다.

| 시점 | 이벤트 | 대상 |
|------|--------|------|
| 실행 시작 | `message_update` | **촉발 카드 파트가 속한 턴에** PROGRESS 파트 append (`status:"running"`, steps 초기 상태) → 그 턴의 파트 배열 전체 스냅샷 재전송. 촉발 파트가 없으면(직접 실행/재개) `message_new`로 새 AI 턴 생성 후 append |
| 스텝 보고 | `message_update` | **그 턴**의 PROGRESS 파트 `steps[]`/`status` 갱신(턴 전체 스냅샷 재전송) |
| 완료 | `message_update` | PROGRESS 파트 `status`를 `success`/`failed`로 확정 + RESULT 파트 append |

- 진행·결과가 **같은 AI 턴의 파트**로 append되므로, 실행이 촉발된 턴 안에서 진행→결과가 순서대로 쌓인다. 한 턴에 실행이 여러 개면 각 실행의 PROGRESS/RESULT 파트가 각자 `executionId`로 구분된다.
- 실행 종료 사유(성공/중지/취소/실패)는 PROGRESS 파트 `status`(+ EXECUTION 계층의 종료 기록)로 표현한다. FE는 PROGRESS `status`와 RESULT 유무로 후속 액션 버튼을 결정한다.
- 새로고침 복원: 대화 턴(+파트)을 로드하면 PROGRESS/RESULT 파트가 그대로 딸려와 진행/결과 블록이 복원된다(FE 메모리 의존 없음). 실행 사실·연결은 [execution.md 새로고침 복원](../recipe/execution.md#브라우저-새로고침--탭-닫기).

#### 실행 재개(resume) SSE 흐름

PARTIAL(실패/중단) 종료된 실행을 [이어서 실행]으로 재개할 때. `POST /api/v1/executions/{executionId}/resume`가 트리거이며, **기존 EXECUTION을 RUNNING으로 되돌려** 첫 미완료 레시피부터 재시도한다(상세: [plan.md 이어서 실행 (PARTIAL 재개)](../recipe/plan.md#이어서-실행-partial-재개)).

| 시점 | 이벤트 | 대상 |
|------|--------|------|
| 재개 요청 접수 | (REST 응답) | 대화방 락 재획득. 이미 처리 중이면 `409`. 재개 불가 상태(SUCCESS/CANCELLED)면 거부 |
| 재개 시작 | `session_status` | `executing`로 재전이 (모든 탭 입력 잠금) |
| 재개 시작 | `message_new` | **새 AI 턴 + PROGRESS 파트** 발행 (재개 진행 표시 — 기존 진행 파트와 별개 턴) |
| 이후 진행/완료 | `message_update` | 일반 [실행 SSE 흐름](#실행-sse-흐름-message_new--message_update)과 동일 (reportStep 기반 스텝 갱신 → 완료 시 PROGRESS 파트 확정 + RESULT 파트 append) |

- 재개는 새 EXECUTION을 만들지 않으므로 `EXECUTION.TRIGGER_PART_ID`(최초 촉발 파트)는 유지되고, 재개 진행은 새 턴의 PROGRESS 파트로 표시된다.
- 대화방 락은 실행 중과 동일 규칙(동시 요청 불가)을 따른다([execution.md 실행 중 입력 제한](../recipe/execution.md#실행-중-입력-제한)).

#### 실행 종료 사유 (outcome)

`success`/`failed` 이분법으로는 "사용자 취소 vs 중지 vs 서버 오류"를 구분할 수 없어, 종료 사유를 명시한다. 이 값은 EXECUTION 계층에 기록되며, FE는 PROGRESS 상태와 함께 이 값으로 **후속 액션 버튼**을 결정한다.

| outcome | 의미 | 유발 주체 | FE 액션 |
|---------|------|----------|---------|
| `SUCCESS` | 정상 완료 | 서버 | 완료 카드 ([결과 보기]) |
| `STOPPED` | 사용자 중지 | 사용자([중지]) | 중단 카드 + [이어서 실행](중단 지점부터 재개). 현재까지 진행분은 히스토리에 보존 |
| `CANCELLED` | 사용자 취소 | 사용자([취소]) | "취소되었습니다" 안내. 히스토리에 기록으로 남음 (재개 대상 아님) |
| `FAILED` | 실행 오류 | 시스템(스텝 실패/타임아웃 등) | 에러 카드 + [이어서 실행](실패 지점부터 재개). 단일 스텝 재시도는 `retriable`이면 [다시 실행] |

- **중지(STOPPED)와 취소(CANCELLED)는 상태로 구분해 기록한다.** 히스토리는 "무슨 일이 있었나"의 기록이므로 [중지]와 [취소]를 다른 사건으로 남긴다(사용자가 히스토리에서 구분해 봄 + 필터/집계 가능). 중단 시 실행의 `RESULT_SUMMARY`에 사유 + 완료 스텝 수를 자동 기록한다(예: "취소됨 · 1/3 스텝 완료").
- **이어서 실행(PARTIAL 재개)**: PARTIAL 종료된 실행(실패 또는 STOPPED)은 [이어서 실행]으로 재개할 수 있다 — 기존 EXECUTION을 RUNNING으로 되돌려 첫 미완료(FAILED/PENDING) 레시피부터 재시도한다(레시피 단위, CONTEXT 이어받음). CANCELLED는 재개하지 않는다. 트리거·오케스트레이션: [plan.md 이어서 실행 (PARTIAL 재개)](../recipe/plan.md#이어서-실행-partial-재개) · resume SSE 흐름은 아래 [실행 재개(resume) SSE 흐름](#실행-재개resume-sse-흐름).
- `retriable` (FAILED에만 의미): Transient 오류(타임아웃, 5xx 등)면 `true` → [다시 실행] 노출. 구조적 오류(스크립트 버그, 잘못된 레시피 정의)면 `false` → 재실행 버튼 숨김. 분류 기준: [error-handling.md](error-handling.md)
- `failedStepIndex` (FAILED/STOPPED): 실패/중단된 스텝 위치. PROGRESS `steps[]`에서 실패/중단 스텝의 `index`와 일치. 단, [이어서 실행](PARTIAL 재개)은 **레시피 단위 재시도**라 이 스텝 위치가 아니라 첫 미완료 레시피의 처음부터 재개한다(스텝 단위 부분 재개는 백로그).

### 대화방 처리 상태 (session_status.status)

여러 탭에서 같은 대화방을 열었을 때, 한 탭의 처리 상태를 다른 탭도 즉시 반영하기 위한 값. FE는 이 값으로 **입력 영역**을 렌더링한다.

| status | 의미 | 입력 영역 UI |
|--------|------|-------------|
| `idle` | 유휴 | 정상 입력 가능 |
| `ai_responding` | AI 응답 생성 중 | "⏳ 답변이 진행 중입니다" + 입력 잠금 |
| `executing` | 레시피/플랜 실행 중 | "⏳ 레시피 실행 중... [중지]" + 입력 잠금 |
| `input_waiting` | 사용자 입력 대기 | 액션 피커만 활성, 자유 채팅 잠금 |

- 상태 전이는 요청을 시작한 탭뿐 아니라 **모든 탭(같은 사용자 Global SSE)**에 전달됨
- 입력 잠금 이유를 입력 지점에 명시(옵션 2). 실행 상세는 채팅 영역의 진행 블록(PROGRESS 파트, `message_update`로 턴 갱신)으로 별도 표시

### 대화방 목록 갱신 (session_list_update)

대화방 목록에 영향을 주는 변경(추가/이름변경/서비스변경/읽음/상태)을 **하나의 이벤트로 통합**한다. 별도 read/rename 이벤트를 두지 않아 파편화를 막는다. **삭제는 이 이벤트가 아니라 별도 [`session_deleted`](#대화방-삭제-session_deleted)로 발행**한다(관심사 분리 — 목록 갱신 vs 삭제 이탈).

**FE 소비 방식 = 목록 재조회(SIGNAL).** 이 이벤트는 SIGNAL(갱신 트리거)이므로, FE는 payload의 `conversation` 한 줄을 목록에 **직접 병합/정렬하지 않고** 목록 API(`GET /api/v1/conversations`)를 재조회한다. 이유:

- **순서·내용의 단일 소스 = 서버.** 정렬 규칙(`lastMessageAt DESC` 등)을 FE에 중복 구현하지 않는다. FE가 한 줄을 병합하며 재정렬하면, 서버 정렬과 미세하게 어긋나거나(예: tie-break) REST/SSE의 필드 포맷 불일치(예: 날짜 직렬화 차이)로 정렬이 깨지는 버그 클래스가 생긴다. 재조회는 이를 원천 차단한다.
- **대화방 목록은 낙관적 UI 대상이 아니다** (낙관적 표시는 사용자 발신 메시지에만 — 아래 [낙관적 UI](#낙관적-ui-사용자-발신-메시지)). 서비스 변경/이름 변경 등 목록 항목 변경도 FE가 낙관적으로 목록을 조작하지 않고, API 성공이 유발하는 이 이벤트 → 재조회로 반영한다.
- **규모상 비용 무시 가능**: 대화방은 사용자당 수십 개(200건 상한)라 목록 재조회 왕복은 수 KB·수십 ms 수준이며 실시간성 체감 손해가 없다.

payload의 `conversation` 스냅샷은 BE 계약(아래 스키마)으로 유지하되, 현재 FE는 이를 **재조회 트리거로만** 사용한다(스냅샷 필드를 직접 렌더에 병합하지 않음). 향후 재조회 비용이 문제가 될 규모가 되면 이 스냅샷으로 부분 병합하도록 최적화할 여지는 남는다.

```json
{
  "op": "upsert",
  "conversation": {
    "id": 123,
    "title": "회원가입 테스트",
    "apiSpecId": 1,
    "serviceName": "사람인",
    "status": "idle | ai_responding | executing | input_waiting",
    "lastMessageAt": "2026-09-01T10:00:00",
    "unread": true,
    "updatedAt": "2026-09-01T10:00:00"
  }
}
```

- `serviceName`: 대화방의 활성 서비스명(표시용). 대화 목록에서 **서비스 배지**로 노출한다([chat/overview.md 서비스 배지](../chat/overview.md#서비스-배지-표시)). 서비스 미지정 시 null. 목록 조회 API 응답(`ConversationSummaryResponse`)과 이 SSE 스냅샷(`ConversationListSnapshot`) **양쪽에 동일하게 포함**한다.

| op | 의미 | conversation |
|----|------|--------------|
| `upsert` | 추가·갱신 통합 (생성/이름변경/서비스변경/읽음/상태변경) | 목록 한 줄 전체 스냅샷 |

**발행 시점 / 규칙**
- **추가(upsert)**: 대화방 row 생성 순간이 아니라 **첫 메시지 전송 시점**에 발행 (빈 대화는 목록에 안 쌓임 — overview.md)
- **읽음**: 대화방 진입 시 읽음 API → `LAST_READ_AT` 갱신 → `upsert`(unread=false)로 모든 탭 뱃지 동기화
- **삭제**: 이 이벤트가 아니라 [`session_deleted`](#대화방-삭제-session_deleted)로 발행한다.
- `session_status`(고빈도 처리상태)는 목록 이벤트와 **분리 유지** — 입력 영역 구동용. 목록 뱃지는 session_list_update의 `status`로 반영. 성격(저빈도 목록 vs 고빈도 상태)이 달라 분리

### 대화방 삭제 (session_deleted)

대화방 삭제는 목록 갱신과 **분리된 별도 SIGNAL 이벤트**로 발행한다. `session_list_update`가 "살아있는 방 한 줄 갱신"만 담당하고, 삭제는 이 이벤트가 담당한다(관심사 분리).

```json
{ "conversationId": 123 }
```

**발행**: 대화방 소프트 삭제(`DELETE /api/v1/conversations/{id}`) 성공 시 모든 탭(같은 사용자 Global SSE)에 발행한다.

**FE 처리**:
- 수신한 `conversationId`가 **현재 보고 있는 대화방**이면 → 홈(`/`)으로 이동 + "보고 있던 대화가 삭제되었어요" 안내(토스트).
- 그리고 **목록을 재조회**(`loadConversations`)해 삭제된 방을 목록에서 제거한다(별도 `session_list_update` 불필요 — 이 이벤트 처리가 재조회를 겸함).
- **본인이 이 탭에서 직접 삭제한 경우**: 삭제 요청 직후 FE가 이미 현재 대화를 정리(`clearConversation`)하므로 `currentConversationId`가 불일치해 이탈/안내가 발생하지 않는다. **다른 탭에서 삭제된 경우에만** 이탈+안내가 뜬다(`currentConversationId` 비교로 자연 구분).

### 상태 해제 (취소 / 중지 / 완료)

`ai_responding` / `executing` / `input_waiting`를 벗어나 `idle`로 돌아가는 것은 **항상 서버가 판단하고 SSE로 전파**한다. FE가 임의로 잠금을 풀지 않는다.

| 트리거 | 경로 | 결과 |
|--------|------|------|
| 액션 피커 [취소] | **FE → 취소 API 호출** (`POST /api/v1/conversations/{id}/cancel`) | 서버가 대기/락 해제 → `session_status: idle` 전파 + "취소되었습니다" 메시지(message_new) |
| 실행 중 [중지] | **FE → 중지 API 호출** (`POST /api/v1/conversations/{id}/stop`) | 현재 스텝까지 저장 후 중단 → PROGRESS `status:"failed"` 확정(`message_update`) + `session_status: idle` 전파 |
| 정상 완료 | 서버 내부 | PROGRESS 확정(`message_update`) + RESULT 생성(`message_new`) + `session_status: idle` 전파 |

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
| `message_new` | 채팅에 즉시 렌더링 (턴+파트, 파트 타입별) | 상태 뱃지(🔵) 업데이트 |
| `message_update` | 해당 턴을 파트 배열 전체로 교체 (진행 파트 갱신 포함). 파트 `id`를 key로 안정 렌더 | 무시 (진입 시 로드) |
| `session_status` | **입력 영역 상태 반영** (idle/ai_responding/executing/input_waiting) | 목록 뱃지 업데이트 |
| `session_list_update` | 목록 재조회로 반영 (upsert 전용) | 목록 재조회로 반영 |
| `session_deleted` | 보고 있는 방이면 홈 이동 + 안내, 그 후 목록 재조회 | 목록 재조회로 반영(삭제된 방 제거) |

> **여러 탭 동기화**: 같은 대화방을 여러 탭에서 열어도 모두 같은 Global SSE로 `session_status`를 받으므로, 한 탭에서 실행/응답이 진행되면 **다른 탭의 입력 영역도 즉시 잠기고 이유가 표시된다.** (탭이 "현재 보고 있는 대화방"이면 입력 영역 반영, 아니면 목록 뱃지)

### 낙관적 UI (사용자 발신 메시지)

SSE 왕복을 기다리면 내 메시지가 화면에 늦게 뜨는 체감 지연이 있다. 전송 API는 거의 즉시 리턴하므로, **접수 성공 직후 임시 메시지를 표시**한다.

1. 전송 → 전송 API가 **2xx(접수됨)** 리턴하면, FE가 **임시 사용자 턴(id 없음, TEXT 파트)을 렌더** + 즉시 입력 잠금
2. 서버는 async 처리 → 확정 턴을 `message_new`(실제 id)로 SSE 발행
3. FE는 `message_new` 도착 시 **해당 대화방의 임시(id 없음) 턴을 전부 제거하고 확정본을 렌더**
   - 임시는 "확정본이 오면 대체될 자리 채우기"일 뿐이므로, 매칭 키 없이 전부 제거로 충분 (대화방 락이 "임시 최대 1개" 불변식 보장)
4. 순서는 턴 `id` 기준 정렬 — 임시 턴은 항상 목록 끝에 두고, 확정된 턴끼리는 `id` 오름차순. AI 응답이 먼저 도착해도 화면 순서 안 꼬임

**적용 범위 / 예외**
- 낙관적 표시는 **접수 성공한 본인 사용자 메시지에만.** AI 응답·다른 탭·다른 대화방 메시지는 SSE 도착 시 표시(낙관적 대상 아님)
- **전송 API 실패**(4xx/5xx/네트워크): 임시 메시지를 아예 그리지 않음 → "전송 실패" 안내 + 입력 유지 (실패 임시 처리 문제가 원천 소거됨)
- 이중 전송은 대화방 락(session_status)이 막고, 접수 성공 직후 입력을 선제 잠가 중복 클릭도 차단

### 종결 보장 (termination guarantee)

무한 로딩을 막기 위한 핵심 원칙.

- **서버는 처리 결과가 성공이든 실패든 반드시 종결 이벤트를 SSE로 보낸다.** (AI 응답 완료 `message_new`, 실행 종료 시 PROGRESS 확정 `message_update` + RESULT `message_new`, 또는 오류 `SYSTEM` 메시지) — 어떤 경우에도 대화방이 `ai_responding`/`executing`에 갇히지 않도록 마지막에 `session_status: idle`을 전파
- 처리 중 서버 예외/크래시로 종결 이벤트를 못 보낸 경우를 대비해, **FE는 응답 지연 타임아웃**(예: 일정 시간 내 관련 SSE 없음)을 두고 "응답이 지연됩니다. 새로고침 해주세요" 안내 + 입력 잠금 해제 여부는 재조회로 결정
- 서버 기동 시 `ai_responding`/`executing`로 남은 대화방을 `idle`로 정리(위 상태 해제 참조)

> **구현/배포 주의 (추후):**
> - heartbeat 전송 실패 시 해당 emitter를 즉시 제거 (좀비 커넥션·메모리 누수 방지)
> - 브라우저는 도메인당 SSE 동시 연결 6개 제한(HTTP/1.1) — 배포 시 HTTP/2 권장
> - 프록시(Nginx 등)는 `proxy_buffering off` + 유휴 타임아웃 상향 필요 (SSE 실시간성 보장)
> - SSE 인증은 **세션 쿠키**로 하므로 쿼리 토큰을 쓰지 않는다(URL 노출 이슈 없음). same-origin에서 쿠키가 자동 전송되고 `userId`는 세션에서 도출한다(위 [인증](#인증)). 크로스 오리진 배포가 필요해지면 그때 쿠키 도메인/`SameSite` 구성을 검토

---

---

## AI 컨텍스트 변환 규칙 (파트 → LLM 메시지)

최근 N턴을 LLM에 넘길 때, 턴의 파트를 그대로 직렬화하지 않고 **화이트리스트 + 요약**으로 변환한다. 목적: 판단에 필요한 것만 넣어 토큰을 아끼고 품질을 높인다. (전달 건수 상한은 [ai-config.md](../chat/ai-config.md) `historyLimit`을 따른다.)

| 파트 타입 | LLM 전달 | 변환 방식 |
|-----------|:---:|-----------|
| `TEXT` | ✅ | 본문 그대로(role=user/assistant) |
| `RESULT` | ✅ | **결과 요약만** — `resultValues` + `summary`. 원시 응답(RESPONSE_JSON)은 제외 |
| `REFERENCES`(조회 답변 동반) | ✅ | 답변 `TEXT`에 녹은 내용만. url/링크는 제외 |
| `PROGRESS` | ❌ | 중간 진행 상태는 완료 후 불필요 |
| `INVESTIGATE`(진행) | ❌ | 조회 진행 로그 불필요(최종 답변 TEXT만 전달) |
| `CARD` / `ACTION_PICKER` | ❌ | buttons/variables 등 렌더 메타 제외. 필요 시 "플랜 제안함: A→B" 수준의 한 줄 요약만 |
| (SYSTEM 안내 TEXT) | ❌(원칙) | 대부분 노이즈. 다음 판단에 필요한 사실만 예외적으로 한 줄 |

- 원칙: **발화 텍스트 + 실행 결과 요약 + 조회 답변**만 컨텍스트로 보낸다. 진행 상태·UI 구조·원시 데이터·링크는 제외한다.
- 이렇게 하면 실행이 있었던 턴은 "무엇을 했고 결과가 무엇인지"(요약)가 전달되어, AI가 후속 발화(예: "방금 만든 회원으로 주문해줘")를 정확히 이해한다.

---

## EXECUTION 계층 (사실/히스토리)

파트가 "화면에 보이는 것"의 진실이라면, **EXECUTION 테이블은 실행이라는 사실의 진실**이다. 둘은 독립 계층이다.

- 실행 기록은 EXECUTION에 독립적으로 남으므로, **대화를 삭제해도 실행 히스토리는 유지**된다. ([panel/history.md](../panel/history.md) · [recipe/execution.md 스텝별 상태 저장](../recipe/execution.md#스텝별-상태-저장))
- **두 방향의 연결**:
  - **파트 → 실행 (렌더 정참조)**: `MESSAGE_PART.EXECUTION_ID`. PROGRESS·RESULT 파트가 자기가 그릴 실행을 가리킨다. 한 실행이 progress·result 여러 파트로 나타나므로 1:N(N쪽=파트 FK)이다.
  - **실행 → 파트 (촉발 역참조)**: `EXECUTION.TRIGGER_PART_ID`. 실행을 촉발한 파트(예: execution_mode 카드 파트)를 가리킨다. **한 턴에 실행이 여러 개여도** 각 실행이 자기 촉발 파트를 특정한다(턴 단위 링크로는 구분 불가). (스키마: [db/execution.md](../../db/execution.md))
- 화면 복원은 턴+파트 로드로 하고(파트의 executionId로 실행 상세 조회), "무슨 일이 있었나"의 사실 조회·집계·필터는 EXECUTION 계층으로 한다. 종료 사유(outcome)·완료 스텝 수 등 히스토리 성격 데이터는 EXECUTION에 기록한다.

## INVESTIGATION 계층 (조회 사실)

정보 조회(investigate)도 실행과 같은 원리로 **사실 계층에 정규 저장**한다(EXECUTION의 형제). "어떤 질문에 어떤 소스를 조회해 어떤 답을 냈나"를 분석·감사할 수 있게 한다.

- **파트 → 조회 (렌더 정참조)**: `MESSAGE_PART.INVESTIGATION_ID`. INVESTIGATE 파트가 자기가 그릴 조회를 가리킨다.
- **조회 → 파트 (촉발 역참조)**: `INVESTIGATION.TRIGGER_PART_ID`. 조회를 촉발한 파트를 가리킨다.
- 조회 스텝(소스·질의·상태·출처)은 `INVESTIGATION_STEP`에, 최종 답변 요약은 `INVESTIGATION.ANSWER_SUMMARY`에 남는다. 화면 칩 렌더용 스냅샷은 REFERENCES 파트 payload가 담당한다(사실 vs 렌더 스냅샷 분리). (스키마: [db/investigation.md](../../db/investigation.md))
- `GET /api/v1/investigations/{id}`로 조회 상세를 제공한다(실행의 `GET /executions/{id}`에 대응).

---

## 인증

SSE 연결은 **세션 쿠키로 인증**한다. JWT/쿼리 토큰을 쓰지 않는다.
```
GET /api/v1/sse/connect        // EventSource withCredentials: true
```

- `EventSource`는 커스텀 헤더를 지원하지 않지만, **세션 쿠키는 same-origin 요청에서 자동 전송**되므로 헤더 없이 인증된다(로컬은 Vite 프록시로 same-origin). `withCredentials: true`로 쿠키를 함께 보낸다.
- `userId`는 요청 파라미터가 아니라 **세션에서 도출**한다.
- 세션 만료/무효화 시 서버가 401로 연결을 거부 → 클라이언트가 재로그인 후 재연결. (인증 방식 전반: [auth.md 인증 방식](auth.md#인증-방식))
