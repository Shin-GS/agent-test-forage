---
sourceId: project-docs-structure
sourceVersion: "1.3"
sourceUpdatedAt: 2026-07-01
inclusion: manual
---

# 프로젝트 기획 문서 구조

## 개요

프로젝트를 시작할 때 아래 4종의 기획 문서를 먼저 작성해야 한다.

> ⚠️ 디자인 시스템 구축이나 화면 구현 전에 최소한 product.md + glossary.md는 존재해야 한다.

## 문서 4종

### 1. product.md — 서비스 개요
- 서비스 설명, 대상 사용자, 핵심 기능, 기술 스택, 프로젝트 구조, 환경변수, 빌드 & 실행, URL 구조

### 2. business-logic.md — 비즈니스 규칙
- 도메인 모델, 상태 전이, 비즈니스 규칙, 계산/수식, 제약 조건

### 3. glossary.md — 용어 사전
- 도메인 용어, 상태값, 화면 명칭

### 4. {화면}.cases.md — 화면별 케이스 정의
- 진입 조건, 케이스별 조건/표시/인터랙션/결과, 에러 케이스, API 연동

## 문서 간 관계

```
product.md (서비스 전체 맥락)
    ↓
business-logic.md (도메인 규칙)
    ↓ 규칙 참조
{화면}.cases.md (화면별 케이스)
    ↓ 디자인 근거
{화면}.html (디자인 명세)
    ↓ 구현 근거
코드 (React/Java 등)
```

## 문서 ↔ 코드 매핑 (최신화 트리거)

| 코드 변경 | 갱신할 문서 |
|-----------|-----------|
| 새 API 엔드포인트 추가 | product.md, cases.md |
| DB 스키마 변경 | business-logic.md, glossary.md |
| 새 화면 추가 | glossary.md, design-manifest.js, cases.md, HTML |
| 비즈니스 규칙 변경 | business-logic.md → cases.md → HTML → 코드 |
| 상태값/Enum 변경 | glossary.md |

## 프로젝트 시작 시 세팅 순서

1. product.md 작성
2. glossary.md 작성
3. business-logic.md 작성
4. 디자인 시스템 세팅 (docs/design/ 초기 구조)
5. 화면별 cases.md + html 작성

## steering 등록 가이드

```yaml
# product.md — 항상 로드
---
inclusion: always
---

# business-logic.md — 항상 로드
---
inclusion: always
---

# glossary.md — 항상 로드
---
inclusion: always
---
```
