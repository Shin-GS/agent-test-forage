---
sourceId: planning-docs
sourceVersion: "1.0"
sourceUpdatedAt: 2026-08-27
inclusion: always
---

# 기획 문서 관리

## 문서 위치

모든 기획 문서는 `docs/specs/` 하위에 도메인별 폴더로 관리한다.

## 구조

```
docs/specs/
├── README.md              ← 문서 맵 (인덱스)
├── glossary.md            ← 용어 사전
├── common/                ← 공통 정책
│   ├── auth.md            ← 로그인, 회원가입, 권한/역할
│   ├── error-handling.md  ← 에러 처리 정책
│   └── response-guide.md  ← AI 응답 가이드
├── chat/                  ← 채팅 인터페이스
│   ├── overview.md        ← 채팅 구조, 대화 관리
│   ├── action-picker.md   ← 액션 피커 상세
│   └── card-ui.md         ← 카드 UI 유형
├── recipe/                ← 레시피
│   ├── structure.md       ← 레시피 구조 (스텝 타입, 조건, 변수)
│   ├── execution.md       ← 실행 플로우 (모드, 진행 상태, 인증)
│   ├── authoring.md       ← 레시피 작성 UI/UX
│   └── versioning.md      ← 버전 관리 정책
├── panel/                 ← 사이드 패널
│   ├── overview.md        ← 패널 구조, 뷰 전환 규칙
│   └── history.md         ← 작업 히스토리 상세
├── spec/                  ← 스펙 등록
│   └── registration.md    ← 등록 방식, 어노테이션, 인증 프로필
└── pages/                 ← 별도 페이지
    ├── recipe-editor.md   ← 레시피 편집 페이지
    ├── history-full.md    ← 전체 히스토리 페이지
    ├── settings.md        ← 설정 페이지
    └── dashboard.md       ← 대시보드 페이지
```

## 규칙

### 문서 작성
- 하나의 md = 하나의 독립된 주제
- 300줄 초과 시 분리 검토
- 문서 간 참조는 상대 경로 링크 사용
- 새 기능 추가 시 해당 도메인 폴더에 md 추가 + README.md에 링크

### 문서 상단 메타
```markdown
---
status: draft | review | confirmed
last-updated: YYYY-MM-DD
---
```

### 코드 변경 시 문서 최신화

| 코드 변경 | 갱신할 문서 |
|-----------|-----------|
| 레시피 구조 변경 | recipe/structure.md |
| 새 스텝 타입 추가 | recipe/structure.md, glossary.md |
| 액션 피커 필드 타입 추가 | chat/action-picker.md |
| API 엔드포인트 추가 | 관련 도메인 문서 |
| 권한/역할 변경 | common/auth.md |
| 새 카드 UI 유형 추가 | chat/card-ui.md |
| 패널 뷰 추가 | panel/overview.md |
| 새 별도 페이지 추가 | pages/ 하위 + README.md |
| 에러 처리 정책 변경 | common/error-handling.md |
| 용어 추가/변경 | glossary.md |

### 폐기 문서 처리
- 더 이상 유효하지 않은 문서는 `docs/specs/_archive/`로 이동
- 삭제하지 않음 (과거 결정 근거 참조용)

## 기획 → 코드 흐름

```
docs/specs/ (기획 확정)
    ↓
docs/design/ (디자인 명세)
    ↓
packages/web/ + packages/server/ (구현)
```

기획 문서가 확정(confirmed)되면 구현을 시작한다. 구현 중 기획 변경이 필요하면 문서를 먼저 수정하고 진행한다.
