---
status: confirmed
last-updated: 2026-09-15
---

# ai-test-forge 기획 문서

AI 기반 API 워크플로우 실행 플랫폼. 사용자가 채팅으로 의도를 전달하면 AI가 적절한 레시피를 찾아 실행하고, 결과를 보여준다.

**핵심 원칙**: 모든 API 호출은 반드시 레시피를 통해서만 수행한다.

**UI 원칙**: ChatGPT 스타일 레이아웃(좌측 사이드바에 네비게이션 통합) + **라이트 테마 기본**. 색상은 tokens 변수 기반이며 다크/라이트 토글은 백로그(`data-theme` 확장 여지만 유지). 상세: [chat/overview.md 레이아웃·테마](chat/overview.md#레이아웃-chatgpt-스타일).

## 호출 주체 구분

| 작업 | 호출 주체 | 인증 |
|------|----------|------|
| 레시피 API 실행 | FE 브라우저 (직접) | 외부 서버 쿠키 세션 (CORS 자동 허용) |
| 정보 조회 (investigate: api_spec, jira) | BE (ai-test-forge 서버) | 내부 DB / 서버 시크릿 토큰 |
| AI 루프 (OpenAI 호환 API 직접 호출, OpenRouter) | BE | 서버 시크릿 API 키 |

## 검증용 데모 서버

프로토타입 검증(스펙 등록/CORS/쿠키 인증/레시피 실행)을 위한 데모 서버는 **이 레포 안 `packages/demo/`**에 둔다. 업종이 서로 다른 여러 가상 서버(이커머스/병원/은행 등)를 두어 "여러 서버의 API를 레시피로 실행"하는 본질을 검증한다.

- 인메모리 상태(DB 없음), 쿠키 세션 인증, 라이브러리(`packages/library`) 경유 자동 등록
- 모든 데이터·레시피·확인은 API로만 수행(DB 직접 접근 금지)
- 상세: [데모 서버 문서](demo/README.md)

---

## 문서 맵

### 처음 읽을 문서
- [용어 사전](glossary.md)
- [로그인/권한](common/auth.md)

### 공통
- [로그인, 권한/역할](common/auth.md)
- [에러 처리 정책](common/error-handling.md)
- [메시징 및 SSE 이벤트](common/messaging.md) — 메시지 포맷, SSE 이벤트 타입, 연결 정책
- [AI 프롬프트 설계](common/ai-config.md) — 모델 구성, 토큰 절약, 호출 구조
- [AI 응답 가이드](common/response-guide.md)

### 채팅
- [채팅 인터페이스 구조](chat/overview.md)
- [액션 피커 상세](chat/action-picker.md)
- [카드 UI 유형](chat/card-ui.md)
- 시나리오:
  - [의도 분류 + 레시피 매칭](chat/scenarios/intent-classification.md)
  - [서비스 선택](chat/scenarios/service-selection.md)
  - [플랜 제안](chat/scenarios/plan-proposal.md)
  - [레시피 실행](chat/scenarios/recipe-execution.md)
  - [AI 필드 생성](chat/scenarios/ai-generation.md)
  - [결과 요약](chat/scenarios/result-summary.md)
  - [정보 조회 루프](chat/scenarios/investigation.md) — investigate 툴, 커넥터, agentic loop

### 레시피
- [레시피 구조](recipe/structure.md) — 스텝 타입, 조건 분기, 변수
- [실행 플로우](recipe/execution.md) — 실행 모드, 진행 상태, 인증
- [레시피 작성 UI/UX](recipe/authoring.md)
- [플랜](recipe/plan.md) — AI가 레시피를 조합한 1회성 실행 계획
- [버전 관리](recipe/versioning.md)

### 사이드 패널
- [패널 구조](panel/overview.md) — 뷰 전환, 참조 태그
- [작업 히스토리](panel/history.md)

### 스펙
- [스펙 등록 방식](spec/registration.md) — 어노테이션, 인증 프로필, 서비스 설명

### 데모 서버 (검증용)
- [데모 서버 개요](demo/README.md) — 가상 서버 목록, API로만 원칙, 구현 순서
- [로컬 실행 가이드](demo/local-setup.md) — hosts/서브도메인/쿠키 정책
- [demo-shop](demo/demo-shop.md) — 이커머스 API 카탈로그 + 레시피 정의

### 별도 페이지
- [레시피 편집](pages/recipe-editor.md)
- [전체 히스토리](pages/history-full.md)
- [설정](pages/settings.md)
- [관리자 페이지](pages/admin.md) — 스펙 관리, 사용자 관리

### 네비게이션

사이드바 하단 아이콘으로 별도 페이지 이동:

```
┌─────────┐
│ 대화 목록 │
│ ...     │
├─────────┤
│ 📋 📊 ⚙️ │  ← 레시피 관리, 히스토리, 설정
└─────────┘
```

- 채팅 페이지가 메인 (사이드바 + 대화 영역 + 사이드 패널)
- 별도 페이지 진입 시 채팅 레이아웃을 벗어남 (전체 화면)
- 레시피 편집 중 이탈 시 저장 확인 ("저장하지 않은 변경사항이 있습니다")
- 관리자 전용: 스펙 관리 (`/admin/specs`)

---

## 추후 계획

| 항목 | 상태 |
|------|------|
| 대화를 통한 레시피 생성 기능 | 추후 고도화 시 구현 |
| 레시피 공유/승인 프로세스 | 추후 고도화 시 구현 |
| 알림 기능 | 추후 고도화. 현재는 채팅 목록 갱신 정도 |
| Figma 커넥터 (정보 조회) | 추후. API key 확보 + Jira 커넥터 사용성 검증 완료 후 추가 |
| 정보 조회 커넥터 확장 (Confluence, Notion 등) | 추후. 커넥터 인터페이스로 조회 소스 확장 |
| 외부 SaaS 레시피 연동 (Slack 등) | 추후 고도화. 커넥터 개념으로 외부 API를 레시피에서 호출 가능하게 확장 |
