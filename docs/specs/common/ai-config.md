---
status: draft
last-updated: 2026-08-27
---

# AI 설정 (인프라)

## 모델 구성

| 역할 | 모델 | 용도 |
|------|------|------|
| reasoning | GPT-4o (또는 동급) | 의도 분석, 플랜 구성 |
| fast | GPT-4o-mini (또는 동급) | AI 생성(필드 값), 이력 요약, 결과 요약 |

모델 교체는 설정만 변경하면 됨. 구조는 모델에 종속적이지 않음.

---

## 토큰 절약 전략

### 단계적 호출

한번에 모든 정보를 보내지 않고, 가벼운 판단 → 필요한 것만 추가 로드.

### 프롬프트 캐싱

- 시스템 프롬프트 + 레시피 목록을 동일한 prefix로 구성
- OpenAI는 동일 prefix 반복 호출 시 자동 캐싱 (비용 50% 절감)
- 변하는 부분(이력 + 발화)은 suffix로

### 이력 압축

- 최근 3~5건 원문 + 그 이전은 요약 1문단
- 요약은 매 5턴마다 fast 모델로 생성해서 BE에 저장
- 레시피 실행 결과: 스텝별 한 줄 요약 + extract된 key-value만 (원시 JSON 제외)

### 레시피 목록 필터링 (AI 호출 전 BE에서 처리)

- 대화방 서비스 설정됨 → 해당 서비스 레시피만 전달
- 서비스 미지정 → 서비스 목록(30개 이름만) 전달하여 먼저 서비스 확정
- AI에게 500개 전체를 보내는 일은 없음

### Function Calling

- AI 응답을 JSON으로 강제 (불필요한 텍스트 출력 방지)
- 출력 토큰 절약 + 파싱 에러 방지

---

## 프로토타입 제약

- 플랜은 1개 서비스의 레시피만 대상 (서비스 간 플랜은 추후)
- 프롬프트는 코드에 하드코딩 (추후 DB/설정으로 이동 가능)
- 이력 요약은 단순 잘라내기로 시작, 추후 AI 요약 도입

---

## 시나리오별 상세

각 분기의 전체 흐름(프롬프트 + UI + 사용자 액션)은 시나리오 문서 참조:

- [의도 분류 + 레시피 매칭](../chat/scenarios/intent-classification.md)
- [서비스 선택](../chat/scenarios/service-selection.md)
- [플랜 제안](../chat/scenarios/plan-proposal.md)
- [레시피 실행](../chat/scenarios/recipe-execution.md)
- [AI 필드 생성](../chat/scenarios/ai-generation.md)
- [결과 요약](../chat/scenarios/result-summary.md)
