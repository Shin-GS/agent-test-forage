---
sourceId: project-overview
sourceVersion: "1.0"
sourceUpdatedAt: 2026-08-27
inclusion: always
---

# ai-test-forge 프로젝트 핵심 요약

## 프로젝트

AI 기반 API 워크플로우 실행 플랫폼. 채팅으로 레시피를 실행하여 테스트 데이터를 생성.

## 아키텍처

- FE: React 19 + Vite + TailwindCSS + Zustand + React Query
- BE: Spring Boot 4 + Java 25 + MySQL + SSE
- 레시피 API 호출: FE 브라우저에서 직접 외부 서버로 (BE 프록시 아님)
- AI: OpenAI GPT-4o (reasoning) + GPT-4o-mini (fast), Tool Use 패턴

## 핵심 개념

| 용어 | 정의 |
|------|------|
| 레시피 | 등록된 워크플로우 (스텝의 순차 실행) |
| 플랜 | AI가 레시피 여러 개를 조합한 1회성 실행 계획 |
| 스텝 타입 | API / 스크립트 / 서브레시피 / 사용자 입력 |
| 액션 피커 | input 영역에 겹쳐 노출되는 구조화된 입력 UI |
| 사이드 패널 | 우측 보조 영역 (레시피 목록 + 히스토리 + 결과). 항상 열림 |
| 카드 UI | 채팅에 삽입되는 진입점 블록 |

## AI 호출 구조

- Tool Use 패턴: 1회 호출로 의도 분석 + tool 선택 (별도 분류 단계 없음)
- Tools 7종: execute_recipe, propose_plan, select_service, show_candidates, clarify, no_match, chat
- message 정책: clarify/chat만 AI 생성, 나머지는 FE 고정 템플릿
- 서비스 미지정 시: services 목록만 전달 (recipes 미전달)
- 서비스 지정 시: 해당 서비스 레시피만 전달 (15~20개)

## 인터페이스

```
┌─────────┬────────────────────┬──────────────────┐
│ 사이드바  │  대화 영역 (중앙)    │  사이드 패널 (우)  │
│ 대화 목록 │  채팅 + 액션 피커    │  레시피 + 히스토리 │
├─────────┤                    │                  │
│ 📋📊⚙️🖥️👥│                    │                  │
└─────────┴────────────────────┴──────────────────┘
```

## 주요 정책

- 대화방 서비스 설정: AI가 자동 설정 또는 사용자 직접 변경
- 대화방 단위 락: 한 대화방에서 동시 요청 불가
- 할루시네이션 금지: AI가 모르면 clarify, 없으면 no_match
- 실행 모드: [자동 실행] / [직접 입력하며 실행]
- 스텝별 결과 서버 저장: 이어서 실행 + 히스토리 가능
- 히스토리: 대화 삭제해도 독립 유지

## 기획 문서 위치

상세는 `docs/specs/` 참조. README.md가 인덱스.
