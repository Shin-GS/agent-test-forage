---
status: draft
last-updated: 2026-08-27
---

# Tool Use 호출 구조

## 개요

사용자 발화 → 1회 AI 호출로 의도 분석 + 분기가 동시에 처리됨.
AI가 적절한 tool을 직접 선택하여 호출. 별도 의도 분류 단계 없음.

## BE → AI 호출

```json
{
  "model": "gpt-4o-mini",
  "messages": [
    { "role": "system", "content": "{시스템 프롬프트}" },
    { "role": "user", "content": "{대화 이력}" },
    { "role": "user", "content": "{사용자 발화}" }
  ],
  "tools": [ ... ]
}
```

## Tools 정의

### execute_recipe

```json
{
  "name": "execute_recipe",
  "description": "단일 레시피를 실행합니다. 사용자가 특정 작업을 요청했고 매칭되는 레시피가 1개일 때 사용.",
  "parameters": {
    "recipeId": { "type": "number", "description": "실행할 레시피 ID" },
    "extractedValues": { "type": "object", "description": "발화에서 명시적으로 언급된 값 (추측 금지)" }
  }
}
```

### propose_plan

```json
{
  "name": "propose_plan",
  "description": "여러 레시피를 순서대로 실행하는 플랜을 제안합니다. 복합 작업이 필요할 때 사용.",
  "parameters": {
    "recipeIds": { "type": "array", "items": { "type": "number" }, "description": "순서대로 실행할 레시피 ID 배열" },
    "extractedValues": { "type": "object", "description": "발화에서 추출한 값" }
  }
}
```

### select_service

```json
{
  "name": "select_service",
  "description": "어느 서비스에서 진행할지 선택을 요청합니다. 서비스가 미지정이거나 다른 서비스가 적합할 때 사용.",
  "parameters": {
    "suggestedServices": { "type": "array", "items": { "type": "string" }, "description": "추천 서비스 목록 (최대 3개). 유추 불가 시 빈 배열." }
  }
}
```

### show_candidates

```json
{
  "name": "show_candidates",
  "description": "유사한 레시피가 여러 개 매칭될 때 후보 목록을 보여줍니다.",
  "parameters": {
    "candidates": { "type": "array", "items": { "type": "object", "properties": { "id": { "type": "number" }, "name": { "type": "string" }, "description": { "type": "string" } } } }
  }
}
```

### clarify

```json
{
  "name": "clarify",
  "description": "발화가 모호하여 추가 정보를 요청합니다. 확실하지 않을 때 반드시 이것을 사용.",
  "parameters": {
    "message": { "type": "string", "description": "사용자에게 물어볼 내용" }
  }
}
```

### no_match

```json
{
  "name": "no_match",
  "description": "매칭되는 레시피가 없을 때 안내합니다.",
  "parameters": {}
}
```

### chat

```json
{
  "name": "chat",
  "description": "레시피 실행이 아닌 일반 대화, 질문, 잡담에 응답합니다.",
  "parameters": {
    "message": { "type": "string", "description": "사용자에게 보여줄 답변 (Markdown)" }
  }
}
```

---

## message 정책

| tool | message | UI 구성 방식 |
|------|---------|------------|
| execute_recipe | 없음 | FE가 recipeId로 레시피 정보 조회 → 고정 템플릿 |
| propose_plan | 없음 | FE가 recipeIds로 정보 조회 → 고정 템플릿 |
| select_service | 없음 | FE가 suggestedServices로 고정 템플릿 |
| show_candidates | 없음 | FE가 candidates로 고정 템플릿 |
| no_match | 없음 | FE 고정 문구 ("해당 레시피가 없습니다...") |
| clarify | ✅ AI 생성 | 맥락에 맞는 재질문 필요 |
| chat | ✅ AI 생성 | 일반 대화 답변 |

---

## 시스템 프롬프트

```
당신은 API 워크플로우 실행 도우미입니다.
사용자의 발화를 분석하여 가장 적절한 tool을 호출하세요.

## 원칙
- 레시피 목록에 없는 작업은 매칭하지 마세요. no_match를 호출하세요.
- 확실하지 않으면 추측하지 말고 clarify를 호출하세요.
- extractedValues에는 발화에서 명시적으로 언급된 값만 넣으세요. 추측 금지.
- 여러 레시피가 순서대로 필요한 복합 작업이면 propose_plan을 호출하세요.
- 유사한 레시피가 2개 이상 매칭되면 show_candidates를 호출하세요 (최대 5개).
- referenceId가 있으면 해당 레시피를 우선 매칭하세요.
- 레시피 요청이 아닌 일반 대화/질문이면 chat을 호출하세요.
- 서비스가 미지정인데 서비스 특정이 필요한 요청이면 select_service를 호출하세요.
- message는 한국어로, 간결하게 작성하세요.

## 현재 대화방 서비스
{service 또는 "미지정"}

## 사용 가능한 레시피 (서비스 지정 시에만 제공)
{recipes_json}

## 사용 가능한 서비스 (서비스 미지정 시에만 제공)
{services_json — 이름 + 한 줄 설명}

## 최근 대화 이력
{history}

## 참조 중인 레시피
{referenceId 또는 "없음"}
```

---

## 컨텍스트 분기

| 대화방 상태 | AI에게 전달하는 것 |
|------------|-----------------|
| 서비스 지정됨 | 해당 서비스의 레시피 목록 (이름+설명+태그+ID) |
| 서비스 미지정 | 서비스 목록 (이름+한 줄 설명) — 레시피는 미전달 |

서비스 미지정 시 AI는 `select_service` 또는 `chat`만 호출 가능 (레시피 정보가 없으므로 execute_recipe/propose_plan 호출 불가).

---

## AI 응답 → BE 처리

```java
String toolName = toolCall.getFunction().getName();
String args = toolCall.getFunction().getArguments();

switch (toolName) {
    case "execute_recipe"  -> handleExecuteRecipe(args);
    case "propose_plan"    -> handleProposePlan(args);
    case "select_service"  -> handleSelectService(args);
    case "show_candidates" -> handleShowCandidates(args);
    case "clarify"         -> handleClarify(args);
    case "no_match"        -> handleNoMatch(args);
    case "chat"            -> handleChat(args);
}
```

각 핸들러가 SSE로 FE에 메시지 전달 → FE가 tool에 따라 UI 렌더링.

---

## tool별 UI 요약

| tool | 채팅 메시지 | 액션 피커 | 사이드 패널 |
|------|-----------|-----------|------------|
| execute_recipe | AI message | [자동 실행] [직접 입력] [취소] | 참조 태그 설정 |
| propose_plan | AI message | 플랜 UI (체크박스, 드래그, 값 지정) | 변화 없음 |
| select_service | AI message | 서비스 선택 버튼 / search-select | 변화 없음 |
| show_candidates | AI message | 후보 목록 선택 | 변화 없음 |
| clarify | AI message (재질문) | 없음 | 변화 없음 |
| no_match | AI message (안내) | 없음 | 변화 없음 |
| chat | AI message (답변) | 없음 | 변화 없음 |
