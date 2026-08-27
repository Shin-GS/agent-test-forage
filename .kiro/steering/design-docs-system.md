---
sourceId: design-docs-system
sourceVersion: "1.2"
sourceUpdatedAt: 2026-07-01
inclusion: manual
---

# 디자인 문서 시스템

## 개요

빌드 도구 없이 순수 HTML/CSS/JS만으로 관리하는 디자인 문서 시스템.
브라우저에서 바로 열어서 화면별 디자인 시안을 케이스별로 확인할 수 있다.

## 디렉토리 구조

```
docs/design/
├── index.html                  ← 전체 화면 목록
├── README.md                   ← 현재 화면 목록 + 상태
├── shared/
│   ├── design-manifest.js      ← 카테고리별 화면 목록 + 뷰포트 설정
│   ├── tokens.css              ← 디자인 토큰
│   ├── base.css                ← 기본 스타일 + case-switcher
│   ├── components.css          ← 공용 컴포넌트
│   └── {도메인}-system.html    ← 컴포넌트 카탈로그
├── {category}/
│   ├── {화면}.html             ← 화면 디자인 시안
│   └── {화면}.cases.md         ← 기획서
```

## 컨벤션

### 파일명
- HTML: `{화면명-kebab-case}.html`
- 케이스: `{화면명-kebab-case}.cases.md`
- 동일 디렉토리에 1:1 매칭

### 스타일 규칙
- 색상/간격은 반드시 `var()` 사용 (하드코딩 금지)
- 새 토큰 필요 시 tokens.css에 먼저 추가
- 2개 화면 이상에서 반복되는 패턴 → components.css로 추출
- 1개 화면 전용 스타일 → 해당 HTML `<style>` 내 유지

### 인터랙션
- vanilla JS만 사용 (외부 라이브러리 금지)
- 데이터 fetch는 하지 않음 (정적 더미 데이터로 모든 케이스 표현)

### 새 화면 추가 시
1. design-manifest.js에 항목 추가
2. cases.md 작성 (기획)
3. HTML 작성 (디자인)
4. README.md 갱신
