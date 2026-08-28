---
status: confirmed
last-updated: 2026-08-27
---

# ai-test-forge 기획 문서

AI 기반 API 워크플로우 실행 플랫폼. 사용자가 채팅으로 의도를 전달하면 AI가 적절한 레시피를 찾아 실행하고, 결과를 보여준다.

**핵심 원칙**: 모든 API 호출은 반드시 레시피를 통해서만 수행한다.

---

## 문서 맵

### 처음 읽을 문서
- [용어 사전](glossary.md)
- [로그인/권한](common/auth.md)

### 공통
- [로그인, 회원가입, 권한/역할](common/auth.md)
- [에러 처리 정책](common/error-handling.md)
- [메시징 및 SSE 이벤트](common/messaging.md) — 메시지 포맷, SSE 이벤트 타입, 연결 정책
- [AI 응답 가이드](common/response-guide.md)

### 채팅
- [채팅 인터페이스 구조](chat/overview.md)
- [액션 피커 상세](chat/action-picker.md)
- [카드 UI 유형](chat/card-ui.md)

### 레시피
- [레시피 구조](recipe/structure.md) — 스텝 타입, 조건 분기, 변수
- [실행 플로우](recipe/execution.md) — 실행 모드, 진행 상태, 인증
- [레시피 작성 UI/UX](recipe/authoring.md)
- [버전 관리](recipe/versioning.md)

### 사이드 패널
- [패널 구조](panel/overview.md) — 뷰 전환, 참조 태그
- [작업 히스토리](panel/history.md)

### 스펙
- [스펙 등록 방식](spec/registration.md) — 어노테이션, 인증 프로필

### 별도 페이지
- [레시피 편집](pages/recipe-editor.md)
- [전체 히스토리](pages/history-full.md)
- [설정](pages/settings.md)

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
| 대화에서 1회용 즉석 레시피 생성 + 실행 | 추후 고도화. 등록 없이 AI가 임시 레시피를 즉석 구성하여 바로 실행. 저장하지 않는 1회용. |
| 레시피 공유/승인 프로세스 | 추후 고도화 시 구현 |
| 알림 기능 | 추후 고도화. 현재는 채팅 목록 갱신 정도 |
| 외부 SaaS 커넥터 연동 (Jira, Slack 등) | 추후 고도화. 커넥터 개념으로 외부 API를 레시피에서 호출 가능하게 확장 |
