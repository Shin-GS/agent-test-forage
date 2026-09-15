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

## 페이지 상단 바 원칙 (별도 전체 화면 페이지)

채팅 레이아웃을 벗어난 별도 페이지(레시피 관리/편집, 히스토리, 설정, 스펙 관리/상세, 사용자 관리 등)의 상단 규칙. 정본: `docs/specs/common/page-layout.md`.

> **제목만 있는 상단 헤더를 두지 않는다.** 사이드바가 현재 위치를 표시하므로 제목 중복이다. 상단 바는 기능(뒤로가기·저장·필터·액션)이 있을 때만 존재한다.

| 유형 | 조건 | 상단 | 컴포넌트 |
|------|------|------|----------|
| 목록형 | 검색·필터·정렬 또는 우측 액션(만들기/추가) 있음 | 툴바 | `PageToolbar` |
| 상세/편집형 | 뒤로가기·저장 등 컨텍스트 액션 필요 | 액션 바(뒤로가기+제목+액션) | `PageActionBar` |
| 콘텐츠형 | 상단에 담을 기능 없음 | 바 없음 | (없음) |

- 새 별도 페이지는 **`PageShell`로 감싸고** 필요한 슬롯(`toolbar`/`actionBar`)만 채운다. 제목 전용 `page-header`를 직접 마크업하지 않는다.
- 제목은 화면에 크게 노출하지 않되 접근성/탭을 위해 `PageShell`이 **`sr-only <h1>` + `document.title`**을 처리한다.
- 관리자 전용 페이지에 "관리자 전용" 배지를 두지 않는다(사이드바 nav 진입 자체가 관리자 전용).
- 상단 바 높이는 `--header-height` 토큰으로 통일, 툴바는 `flex-wrap` + `sticky`.

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
