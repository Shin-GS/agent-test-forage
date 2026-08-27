---
sourceId: design-system
sourceVersion: "1.3"
sourceUpdatedAt: 2026-07-01
inclusion: always
---

# 디자인 시스템 구축 워크플로우

## 목적

프로젝트마다 일관된 디자인 시스템을 구축하여, AI가 페이지를 구현할 때 시각적 일관성을 보장한다.

## 필수 전제: 디자인 시스템 없이 페이지 구현 금지

> ⚠️ tokens.css + components.css + 시스템 HTML이 없는 상태에서 페이지 구현을 시작하면 안 된다.

## Steps

### Step 0: docs/design/ 초기 구조 세팅
- design-manifest.js, tokens.css, base.css, components.css, index.html, README.md

### Step 1: 디자인 토큰 정의 (tokens.css)
- 색상 팔레트, 간격 체계, 폰트 크기, 테두리 radius, 그림자

### Step 2: 공통 컴포넌트 정의 (components.css + system.html)
- Button, Input, Card, Modal 등 기본 컴포넌트 스타일 + 카탈로그

### Step 3: 페이지 디자인 명세 (케이스별 HTML)
- cases.md에 정의된 상태별 HTML 구현. tokens.css 변수만 사용.

### Step 4: FE 코드 구현
- HTML 명세를 React로 1:1 변환. 모든 케이스 구현.

## tokens.css 예시

```css
:root {
  --color-primary: #3B82F6;
  --color-primary-hover: #2563EB;
  --color-background: #111827;
  --color-surface: #1F2937;
  --color-text: #F9FAFB;
  --color-text-muted: #9CA3AF;
  --color-error: #EF4444;
  --color-success: #10B981;

  --space-xs: 4px;
  --space-sm: 8px;
  --space-md: 16px;
  --space-lg: 24px;
  --space-xl: 32px;

  --font-size-sm: 14px;
  --font-size-md: 16px;
  --font-size-lg: 20px;
  --font-size-xl: 24px;

  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-full: 9999px;
}
```

## 주의사항

- 페이지별 HTML은 빌드 없이 브라우저에서 바로 확인 가능해야 함
- 색상/간격은 절대 하드코딩하지 않음 — 반드시 var() 사용
- 새 토큰이 필요하면 tokens.css에 먼저 추가 후 사용
