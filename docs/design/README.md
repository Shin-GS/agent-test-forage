# 디자인 명세

이 폴더는 서비스의 디자인 명세를 HTML/CSS로 관리합니다.
빌드 없이 브라우저에서 바로 확인할 수 있습니다.

## 사용법

1. HTML 파일을 브라우저에서 직접 열기
2. 상단 케이스 탭으로 상태별 화면 전환
3. 버튼/모달 등 인터랙션 직접 확인

## 구조

```
docs/design/
├── README.md            # 이 문서
├── shared/              # 디자인 시스템 (토큰 + 컴포넌트)
│   ├── tokens.css       # 디자인 토큰 (컬러, 타이포, 간격, 그림자)
│   ├── base.css         # 리셋 + 레이아웃 + 반응형 + 유틸리티
│   ├── components.css   # 공통 컴포넌트 스타일
│   └── system.html      # 컴포넌트 카탈로그 (브라우저에서 확인)
└── web/                 # 화면별 디자인 명세
    ├── login.cases.md   # 로그인 케이스 정의
    ├── login.html       # 로그인 HTML (케이스별 탭 전환)
    ├── chat.cases.md    # 채팅 케이스 정의
    ├── chat.html        # 채팅 HTML
    ├── panel.cases.md   # 사이드 패널 케이스 정의
    └── panel.html       # 사이드 패널 HTML
```

## 화면 목록

| 화면 | 파일 | 케이스 | 상태 |
|------|------|--------|------|
| 컴포넌트 카탈로그 | `shared/system.html` | — | DONE |
| 로그인 | `web/login.html` | `web/login.cases.md` (4개) | DONE |
| 채팅 (메인) | `web/chat.html` | `web/chat.cases.md` (17개) | DONE |
| 사이드 패널 | `web/panel.html` | `web/panel.cases.md` (5개) | DONE |
| 레시피 편집 | `web/recipe-editor.html` | `web/recipe-editor.cases.md` (7개) | DONE |
| 설정 | `web/settings.html` | `web/settings.cases.md` (3개) | DONE |
| 전체 히스토리 | `web/history.html` | `web/history.cases.md` (4개) | DONE |
| 관리자 | `web/admin.html` | `web/admin.cases.md` (3개) | DONE |

## 디자인 원칙

- **다크 테마 only** — 개발자 도구 특성에 맞춘 선택
- **반응형** — Desktop(1024px+) + Tablet(768px~1023px) 지원
- **토큰 기반** — 모든 시각값은 `tokens.css` 변수 참조 (하드코딩 금지)
- **실구현 가능** — React + TailwindCSS로 바로 옮길 수 있는 구조
- **접근성** — focus-visible, sr-only, 충분한 색상 대비

## 상태 범례

- `TODO` — 미작성
- `DRAFT` — 초안
- `DONE` — 완성
- `OUTDATED` — 업데이트 필요

## 규칙

1. 새 화면 추가 시 이 목록에 등록
2. 색상/간격은 절대 하드코딩 금지 — 반드시 `var()` 사용
3. 새 토큰이 필요하면 `tokens.css`에 먼저 추가 후 사용
4. cases.md에 정의된 케이스를 HTML에서 모두 구현
5. 화면명은 `docs/specs/glossary.md`의 용어와 일치
