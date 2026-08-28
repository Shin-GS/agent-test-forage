---
status: draft
last-updated: 2026-08-27
---

# 대화 시나리오 개요

## 기본 원칙

모든 사용자 발화는 **1회 AI 호출 (Tool Use 패턴)**으로 처리.
AI가 발화를 분석하여 적절한 tool을 직접 선택하고 호출한다.
별도 의도 분류 단계 없이, tool 선택 자체가 의도 분석이다.

## 아키텍처

```
사용자 메시지 전송
    │
    ▼
BE: 컨텍스트 조립 (서비스, 레시피 목록, 이력, 참조 태그)
    │
    ▼
AI 호출 (system prompt + tools 정의 + user message)
    │
    ▼
AI가 tool_call 반환
    │
    ▼
BE: tool_call.name에 따라 핸들러 실행 → SSE로 FE에 전달
    │
    ▼
FE: tool별 UI 렌더링 (액션 피커 / 채팅 메시지 / 진행 상태)
```

## Tool 목록

| tool | 설명 | UI 결과 |
|------|------|---------|
| `execute_recipe` | 단일 레시피 실행 | 액션 피커: [자동 실행] [직접 입력] |
| `propose_plan` | 플랜 제안 (레시피 여러 개 조합) | 액션 피커: 플랜 UI |
| `select_service` | 서비스 선택 요청 | 액션 피커: 서비스 목록 |
| `show_candidates` | 유사 레시피 후보 표시 | 액션 피커: 후보 선택 |
| `clarify` | 추가 정보 요청 | 채팅: 재질문 메시지 |
| `no_match` | 매칭 실패 안내 | 채팅: 안내 메시지 |
| `chat` | 일반 대화/질문 응답 | 채팅: AI 답변 메시지 |

## 전체 흐름

```
사용자 발화
    │
    ▼
AI tool 선택
    │
    ├─ execute_recipe → [레시피 실행](recipe-execution.md)
    ├─ propose_plan   → [플랜 제안](plan-proposal.md) → 실행
    ├─ select_service → [서비스 선택](service-selection.md) → 재호출
    ├─ show_candidates→ 후보 선택 → execute_recipe로 합류
    ├─ clarify        → 채팅 메시지 (자유 대화 가능)
    ├─ no_match       → 채팅 메시지 (레시피 생성 안내)
    └─ chat           → 채팅 메시지 (일반 답변)
```

## 실행 중 AI 개입

레시피 실행 자체는 FE가 수행. 아래 상황에서만 별도 AI 호출:

| 상황 | AI 호출 | 참조 |
|------|---------|------|
| "AI 생성" 필드 발견 | [fast] 필드 값 생성 | [ai-generation.md](ai-generation.md) |
| 실행 완료 + 템플릿 없음 | [fast] 결과 요약 | [result-summary.md](result-summary.md) |

## 시나리오 상세 문서

| 파일 | 역할 |
|------|------|
| [intent-classification.md](intent-classification.md) | Tool Use 호출 구조 + tools 정의 + 프롬프트 |
| [service-selection.md](service-selection.md) | select_service tool 처리 |
| [plan-proposal.md](plan-proposal.md) | propose_plan tool 처리 |
| [recipe-execution.md](recipe-execution.md) | execute_recipe 이후 실행 흐름 |
| [ai-generation.md](ai-generation.md) | 실행 중 AI 필드 생성 |
| [result-summary.md](result-summary.md) | 실행 완료 후 AI 요약 |

## 확장 방법

새 기능 추가 시:
1. `tools` 배열에 새 function 정의 추가
2. BE에 해당 tool 핸들러 구현
3. FE에 해당 UI 렌더링 추가
4. 시나리오 문서 작성

intent enum이나 분류 로직 변경 불필요.

## 구현 시 주의사항

| 항목 | 설명 | 대응 |
|------|------|------|
| AI 매칭 정확도 | 비슷한 이름의 레시피가 여러 개면 잘못 고를 수 있음 | 레시피 설명을 명확하게 작성 + 프롬프트 튜닝 (운영하며 개선) |
| 서비스 선택 후 재호출 | 원래 발화를 BE가 보관했다가 서비스 확정 후 재전송해야 함 | BE에서 "pending message" 상태 관리 |
| AI 응답 지연 | Tool Use 1회 = 1~3초. 서비스 선택 → 재호출이면 2~6초 | 로딩 인디케이터 ("분석 중...") 즉시 표시 |
