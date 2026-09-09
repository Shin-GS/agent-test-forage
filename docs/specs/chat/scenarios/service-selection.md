---
status: draft
last-updated: 2026-09-09
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
    │     → 안내 TEXT + service_select 카드 (추천 후보 목록)
    │
    └─ 유추 불가 → select_service(suggestedServices: [])
          → 안내 TEXT + service_select 카드 (전체 ACTIVE 서비스 폴백)
    │
    ▼
사용자가 카드에서 서비스 선택
    → PATCH /conversations/{id}/service (대화방 서비스 설정 + 카드 CONSUMED)
    → SYSTEM 알림 턴: "대상 서비스가 'XX'(으)로 설정되었어요."
    → 이후 사용자가 이어서 발화하면 그 서비스 컨텍스트로 진행
```

> 서비스 미지정 상태에서 AI는 `select_service` 또는 `chat`만 호출 가능하다.
> 레시피 정보가 전달되지 않으므로 `execute_recipe`/`propose_plan`은 호출 불가.

### 안내 강화 (구현)

카드만으로는 "무엇을 하라는 것인지" 모호하므로, **안내 TEXT 파트를 카드 앞에 함께 발행**한다
(`AssistantMessageDraft.cardWithText` → 한 턴 = TEXT 파트 + CARD 파트, 순서대로 렌더).

- **추천 후보가 있을 때**: "먼저 대상 서비스를 선택해 주세요. 요청하신 작업은 아래 서비스 중 하나로 보여요. 원하는 서비스를 고르시면 이어서 진행할게요. 찾는 서비스가 없으면 우측 패널에서 직접 선택할 수 있어요."
- **추천 후보가 없을 때(유추 불가/investigate hard guard)**: 전체 ACTIVE 서비스를 카드에 폴백으로 담고 "먼저 대상 서비스를 선택해 주세요. 아래에서 고르거나 우측 패널에서 직접 선택할 수 있어요."
- **등록된 서비스가 0개일 때**: "아직 사용할 수 있는 대상 서비스가 없어요. 관리자에게 서비스 등록을 요청해 주세요." (카드는 빈 목록)

> **직접 선택 경로 보장**: AI 추천은 발화 기반 추측이라 틀릴 수 있다(레시피 미생성 등). 추천 목록 외의 서비스도
> 항상 **우측 패널 대상 서비스 블록 드롭다운**에서 직접 선택할 수 있다. 카드는 추천 중심, 전체 선택은 패널이 담당한다(중복 방지).

### 서비스 설정 알림 (SYSTEM)

카드 선택이든 우측 패널 드롭다운이든 **모두 `PATCH /conversations/{id}/service`를 거치며**, 이 API가 성공하면
BE가 **SYSTEM 턴 + TEXT 파트**로 설정 결과를 남긴다(경로 무관 일관, 새로고침·다른 탭 복원). AI/사용자 발화가 아니라
시스템 상태 변경 안내이므로 `ROLE=SYSTEM`으로 통일한다.

- 설정: "대상 서비스가 '{서비스명}'(으)로 설정되었어요."
- 해제(미지정으로 되돌림): "대상 서비스 설정이 해제되었어요."
- **새 대화(아직 서버 미생성)**는 대화방이 없어 알림을 남기지 않는다. 선택값은 FE pending에 보관되어 첫 메시지 전송 시 대화방 생성에 반영된다([overview.md 새 대화 pending](../overview.md)).

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
| 채팅 | 안내 TEXT + service_select 카드(추천 후보 버튼 목록) |
| 사이드 패널 | 변화 없음 (전체 선택은 대상 서비스 블록 드롭다운) |

사용자 액션:
- 카드에서 서비스 선택 → `PATCH /service`로 대화방 서비스 설정 + 카드 CONSUMED + SYSTEM 알림 턴
- 이후 사용자가 이어서 발화하면 그 서비스 컨텍스트로 진행

### 유추 불가

| 영역 | 동작 |
|------|------|
| 채팅 | 안내 TEXT + service_select 카드(전체 ACTIVE 서비스 폴백) |
| 사이드 패널 | 변화 없음 (전체 선택은 대상 서비스 블록 드롭다운) |

---

## 시스템 프롬프트

`select_service`의 판단 규칙은 [intent-classification.md](intent-classification.md)의 통합 시스템 프롬프트에 포함되어 있다. 서비스 유추 관련 핵심 규칙:

```
- 서비스가 미지정인데 서비스 특정이 필요한 요청이면 select_service를 호출하세요.
- 확실하지 않으면 suggestedServices를 빈 배열로 반환하세요.
- 서비스 목록에 없는 서비스를 추측하지 마세요.
- 최대 3개까지만 추천하세요.
```
