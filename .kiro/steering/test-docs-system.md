---
sourceId: test-docs-system
sourceVersion: "1.2"
sourceUpdatedAt: 2026-07-01
inclusion: manual
---

# 테스트 문서 시스템

## 개요

빌드 도구 없이 순수 HTML/CSS/JS만으로 관리하는 수동 테스트 체크리스트 시스템.
브라우저에서 바로 열어서 기능별 테스트 항목을 확인하고 체크할 수 있다.

## 디렉토리 구조

```
docs/test/
├── index.html              ← 통합 뷰어
├── README.md               ← 전체 테스트 현황
├── shared/
│   ├── test-manifest.js    ← 카테고리별 테스트 파일 목록
│   ├── test-base.css       ← 뷰어 공통 스타일
│   └── test-renderer.js    ← __TEST_DATA__ → HTML 렌더링
├── {category}/
│   └── {기능명}.js         ← 테스트 데이터 (window.__TEST_DATA__)
```

## 테스트 데이터 파일 스키마

```javascript
window.__TEST_DATA__ = {
  "feature": "기능명",
  "screen": "화면명",
  "lastUpdated": "YYYY-MM-DD",
  "priority": "critical | high | medium | low",
  "cases": [
    {
      "id": "{약어}-{N|E}-{번호}",
      "type": "normal | exception",
      "regression": false,
      "regressionNote": "",
      "scenario": "테스트 시나리오",
      "precondition": "전제 조건",
      "action": "사용자 조작",
      "expected": "기대 결과",
      "dbCheck": "DB 확인 사항"
    }
  ]
};
```

## ID 체계
- 파일약어: 파일명 각 단어 첫 글자 (예: user-signup → US)
- 정상: `{약어}-N-001`
- 예외: `{약어}-E-001`

## priority 기준

| 우선순위 | 기준 | 배포 전 행동 |
|---------|------|-------------|
| critical | 돈, 결제, 인증 | 반드시 전체 통과 |
| high | 핵심 플로우 | 관련 기능 변경 시 필수 |
| medium | 일반 기능 | 주기적 확인 |
| low | 보조 기능 | 대규모 변경 시에만 |

## 새 테스트 추가 절차
1. `docs/test/{category}/{기능명}.js` 생성
2. `test-manifest.js`에 항목 추가
3. `README.md` 갱신
