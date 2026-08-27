---
sourceId: spec-driven-workflow
sourceVersion: "1.1"
sourceUpdatedAt: 2026-08-27
inclusion: manual
---

# Spec 기반 개발 워크플로우

## 목적

복잡한 기능을 구현할 때, "무엇을 만들 것인가"(spec)를 먼저 합의하고 "어떻게 만들 것인가"(구현)로 넘어가는 절차를 정의한다.

## Spec 세션 사용 기준

| 상황 | 접근 방식 |
|------|-----------|
| 단순 버그 수정, 설정 변경, 1~2파일 수정 | Vibe 세션으로 바로 처리 |
| 기능 추가 (새 화면, API 3개+, 상태 전이 포함) | Spec 세션 사용 |
| 리팩토링 (파일 10개+, 구조 변경) | Spec 세션 사용 |
| 요구사항이 모호하거나 선택지가 있음 | Spec 세션 |

## Steps

### Step 1: Requirements 작성
- Outcome 중심 (구현 방법 아님)
- 코드 블록 금지
- 수용 기준 포함
- 범위 외 명시
- 8KB 이내

### Step 2: Design 작성
- 시그니처 수준 (구현부 미포함)
- 데이터 흐름
- 기술적 결정 + 대안 기록
- 12KB 이내

### Step 3: Tasks 분해
- 체크리스트 형식
- 파일 경로 포함
- 한 태스크 = 파일 1~3개

### Step 4: 구현
- 태스크 순서대로 하나씩
- 각 태스크 완료 시 verification-loop
- spec과 다른 방향 → design 먼저 수정

### Step 5: 완료 및 정리
- 완료된 spec은 archive
- summary.md 작성

## Spec 분할 기준

| 기준 | 임계값 |
|------|--------|
| requirements 크기 | 8KB 초과 → 분할 |
| tasks 수 | 15개 초과 → Phase 분할 |
| 예상 구현 기간 | 3일 초과 → 분할 |
| 관심사 | 2개 이상 도메인 → 분리 |
