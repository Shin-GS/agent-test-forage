---
status: draft
last-updated: 2026-08-28
---

# 시나리오: 서비스 선택

## 트리거

사용자가 채팅에서 메시지를 전송했을 때, 대화방에 서비스가 미지정인 상태.
AI가 [Tool Use 호출](intent-classification.md)에서 `select_service` tool을 선택하면 이 시나리오로 진입한다.

## 전체 흐름

```
사용자 발화 (서비스 미지정)
    │
    ▼
[fast] AI 호출 (Tool Use)
  - 입력: 발화 + 서비스 목록 (이름 + 한 줄 설명) — 레시피는 미전달
  - AI가 select_service tool 호출
    │
    ├─ 유추 가능 → select_service(suggestedServices: [1~3개])
    │     → 채팅: FE 고정 템플릿 "XX 서비스에서 진행할까요?"
    │     → 액션 피커: [서비스A] [서비스B] [취소]
    │     → 선택 시: 대화방 서비스 설정 + 발화 재전송 (Tool Use 재실행)
    │
    └─ 유추 불가 → select_service(suggestedServices: [])
          → 채팅: FE 고정 템플릿 "어느 서비스에서 진행할까요?"
          → 액션 피커: 전체 서비스 목록 (search-select) + [취소]
```

> 서비스 미지정 상태에서 AI는 `select_service` 또는 `chat`만 호출 가능하다.
> 레시피 정보가 전달되지 않으므로 `execute_recipe`/`propose_plan`은 호출 불가.

---

## AI 호출 (Tool Use)

BE → AI 요청은 [intent-classification.md](intent-classification.md)의 공통 구조를 따른다.
서비스 미지정 상태이므로 context에는 **서비스 목록만** 포함되고 레시피는 전달되지 않는다.

```json
{
  "model": "gpt-4o-mini",
  "messages": [
    { "role": "system", "content": "{시스템 프롬프트}" },
    { "role": "user", "content": "{대화 이력}" },
    { "role": "user", "content": "입사지원 해줘" }
  ],
  "tools": [ ... ]
}
```

context로 전달되는 서비스 목록 (이름 + 한 줄 설명):

```json
{
  "services": [
    { "name": "demo-shop", "description": "커머스 주문/상품/회원 API" },
    { "name": "demo-pay", "description": "결제/정산 API" }
  ]
}
```

## AI 응답 (tool call)

```json
{
  "toolCalls": [
    {
      "function": {
        "name": "select_service",
        "arguments": { "suggestedServices": ["demo-shop", "demo-pay"] }
      }
    }
  ]
}
```

- 유추 불가 시: `"suggestedServices": []`
- `select_service`는 **message를 생성하지 않는다.** 안내 문구는 FE 고정 템플릿.

---

## UI 동작

### 유추 가능 (후보 1~3개)

| 영역 | 동작 |
|------|------|
| 채팅 | FE 고정 템플릿 ("~에서 진행할까요?") |
| 액션 피커 | 서비스 버튼 목록 + [취소] |
| 사이드 패널 | 변화 없음 |
| 대화방 상태 | 🟡 입력 대기 |

사용자 액션:
- 서비스 선택 → 대화방 서비스 자동 설정 + 헤더 업데이트 + **발화 재전송 (Tool Use 재실행, 이번엔 레시피 목록 포함)**
- [취소] → 채팅 input 복귀

### 유추 불가

| 영역 | 동작 |
|------|------|
| 채팅 | FE 고정 템플릿 ("어느 서비스에서 진행할까요?") |
| 액션 피커 | search-select (서비스 검색) + [취소] |
| 사이드 패널 | 변화 없음 |

---

## 시스템 프롬프트

`select_service`의 판단 규칙은 [intent-classification.md](intent-classification.md)의 통합 시스템 프롬프트에 포함되어 있다. 서비스 유추 관련 핵심 규칙:

```
- 서비스가 미지정인데 서비스 특정이 필요한 요청이면 select_service를 호출하세요.
- 확실하지 않으면 suggestedServices를 빈 배열로 반환하세요.
- 서비스 목록에 없는 서비스를 추측하지 마세요.
- 최대 3개까지만 추천하세요.
```
