---
status: confirmed
last-updated: 2026-09-08
---

# 시나리오: 결과 요약

레시피 실행 완료 시 결과 메시지 발행 흐름의 전체 개요는 [execution.md 실행 완료 / 결과 요약](../../recipe/execution.md#실행-완료--결과-요약) 참조. 이 문서는 그중 **템플릿이 없어 fast AI로 요약하는 경우**를 상세히 다룬다.

## 트리거

레시피 실행 완료 시, 해당 레시피에 결과 메시지 템플릿(⑤)이 정의되지 않은 경우.

## 전체 흐름

```
레시피 실행 완료 (템플릿 없음)
    │
    ▼
FE → BE: 결과 요약 요청 (레시피명 + 스텝별 결과)
    │
    ▼
[fast] AI 호출: 결과 요약 생성
    │
    ▼
채팅: AI 생성 요약 메시지 + 카드 UI [결과 보기]
```

---

## AI 입력

> **원시 응답 미전달 원칙**: AI에는 **스텝별 summary(steps)** · **resultValues**(④ 결과 정의로 추린 값) · **resultLabels**(결과 key → 표시명 맵)만 전달한다.
> 스텝의 원시 응답(raw response) 전체는 넘기지 않는다. (토큰 절감 + 민감정보 노출 최소화)

> **표시명 전달 원칙**: AI가 원본 key(`applicationId` 등)를 사람말로 임의 창작하지 않도록, [표시명 폴백 체인](../../recipe/structure.md#표시명label-폴백-체인)으로 결정된 표시명을 함께 전달한다.
> - `steps[].name`은 서버가 실행 시점에 폴백 체인((1) 스텝 표시명 → (2) 엔드포인트 summary → (3) method+path)으로 확정한 사람말 이름이다.
> - `resultLabels`는 결과 정의(④)에 `label`이 등록된 key만 포함한다. label이 없는 key는 맵에 없고, AI는 그 경우 원본 key를 그대로 쓴다(임의 창작 금지). 원칙: [response-guide.md 표시명 폴백 체인](../../common/response-guide.md#표시명label-폴백-체인)

```json
{
  "system": "시스템 프롬프트",
  "context": {
    "recipeName": "입사지원",
    "steps": [
      { "name": "로그인 확인", "status": "success", "summary": "성공" },
      { "name": "이력서 확인", "status": "success", "summary": "프론트엔드 개발자 이력서 발견" },
      { "name": "공고 선택", "status": "success", "summary": "네이버 백엔드 (3년차)" },
      { "name": "입사지원 제출", "status": "success", "summary": "지원번호 APP-2026-0831" }
    ],
    "resultValues": {
      "applicationId": "APP-2026-0831",
      "jobTitle": "네이버 백엔드 개발자"
    },
    "resultLabels": {
      "applicationId": "지원번호"
    }
  }
}
```

## AI 출력

```json
{
  "summary": "입사지원이 완료되었습니다.\n- 공고: 네이버 백엔드 개발자\n- 지원번호: APP-2026-0831"
}
```

---

## UI 동작

| 영역 | 동작 |
|------|------|
| 채팅 | AI 요약 메시지 표시 (Markdown) — 결과 전달의 주 경로 |
| 채팅 | 카드 UI (결과 제공형): 레시피명 + 실행 시각. [결과 보기]는 프로토타입에서 비활성/생략 (사이드 패널 미구현) |
| 사이드 패널 | 히스토리 refresh |
| 대화방 상태 | 유휴 |

프로토타입 결과 표시 스코프는 [execution.md 결과 표시(프로토타입 스코프)](../../recipe/execution.md#결과-표시-프로토타입-스코프) 와 정합.

---

## 템플릿이 정의된 경우

AI 호출 없이 BE가 템플릿의 `{{변수}}`를 실행 context 값으로 치환하여 직접 결과 메시지(`message_new`)를 생성.
이 시나리오(fast AI 요약)는 사용하지 않음.

- 치환 변수 범위는 **④ 결과 정의 + ② 사용자 입력 변수**로 한정한다(extract 원시 변수 직접 참조 금지).
- 상세: [execution.md 실행 완료 / 결과 요약 — (a) 템플릿이 있는 경우](../../recipe/execution.md#실행-완료--결과-요약)

---

## 프롬프트 전문

### 시스템 프롬프트

```
레시피 실행 결과를 사용자에게 간결하게 요약해주세요.

## 원칙
- 2~3줄로 핵심 결과만 요약
- 성공/실패 여부 명시
- 주요 생성 데이터 (ID, 이름 등) 포함
- 불필요한 인사말이나 장식 없이 간결하게

## 레시피 정보
- 이름: {recipeName}
- 스텝별 결과: {steps}
- 결과 값: {resultValues}
- 결과 값 표시명: {resultLabels}  ← 결과 key의 사람말 이름. 요약 시 원본 key 대신 이 표시명을 사용. 표시명이 없는 key는 원본 key를 그대로 쓰고 임의로 지어내지 않는다.

## 응답 형식 (JSON만)
{
  "summary": "요약 메시지 (Markdown)"
}
```
