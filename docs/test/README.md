# QA 수동 테스트 체크리스트

ai-test-forge 프로젝트의 수동 QA 테스트를 관리하는 브라우저 기반 체크리스트입니다.

## 사용 방법

ES module을 사용하므로 **로컬 서버를 통해 열어야 합니다** (file:// 프로토콜에서는 CORS로 동작 안 함).

```bash
# 방법 1: npx serve (추가 설치 불필요)
npx serve docs/test
# → http://localhost:3000 에서 확인

# 방법 2: VS Code Live Server 확장 사용
# index.html 우클릭 → "Open with Live Server"

# 방법 3: Python
python -m http.server 3000 --directory docs/test
```

## 기능

- **기능별 필터**: 드롭다운으로 특정 기능만 표시
- **상태 필터**: Pass / Fail / Skip / 미진행 필터
- **상태 저장**: localStorage에 자동 저장 (브라우저 닫아도 유지)
- **초기화**: 현재 기능 또는 전체 초기화 가능

## 파일 구조

```
docs/test/
├── README.md              ← 이 파일
├── index.html             ← 진입점 (브라우저에서 열기)
├── shared/
│   ├── test-base.css      ← 공통 스타일 (라이트 테마)
│   ├── test-renderer.js   ← 렌더링 엔진 (ES module)
│   └── test-manifest.js   ← 기능별 테스트 파일 매니페스트
└── web/
    ├── chat.js            ← 채팅 + 레시피 실행 + 플랜 + 정보 조회(investigate) + 결과 렌더 (124개 케이스)
    ├── panel.js           ← 사이드 패널 재설계 (19개 케이스)
    ├── recipe-editor.js   ← 레시피 편집/관리 (41개 케이스)
    ├── login.js           ← 로그인/인증 (8개 케이스)
    ├── spec.js            ← 스펙 등록/관리 (12개 케이스)
    ├── settings.js        ← 설정 (7개 케이스)
    ├── history.js         ← 전체 히스토리 페이지 (16개 케이스)
    └── admin.js           ← 관리자 페이지 A(스펙 관리+RBAC)+B(사용자 관리) (44개 케이스)
```

## 테스트 케이스 현황

| 기능 | 우선순위 | 케이스 수 | ID 접두사 |
|------|----------|-----------|-----------|
| 채팅 + 레시피 + 플랜 + 정보 조회 + 결과 렌더 | critical | 124 | CHAT-xxx |
| 사이드 패널 (재설계) | high | 19 | PANEL-xxx |
| 레시피 편집/관리 | high | 41 | RECIPE-xxx |
| 로그인/인증 | high | 8 | LOGIN-xxx |
| 스펙 등록/관리 | high | 12 | SPEC-xxx |
| 설정 | medium | 7 | SETTINGS-xxx |
| 전체 히스토리 페이지 | medium | 16 | HISTORY-xxx |
| 관리자 페이지 A(스펙 관리+RBAC)+B(사용자 관리) | medium | 44 | ADMIN-xxx |
| **합계** | | **272** | |

> 관리자 페이지 케이스 구분: **A(완료)** = ADMIN-001~019 (RBAC + 스펙 관리), **별도 작업** = ADMIN-020 (서비스 설명 편집, SKIP — 위치 확보용, 삭제 금지), **B(이번 작업)** = ADMIN-030~053 (사용자 관리: 진입/목록/검색/생성/역할·상태·비밀번호 변경 + 자기 보호 + 마지막 ACTIVE ADMIN 보호 + 삭제 액션 없음).

## 새 테스트 추가 방법

1. `web/` 폴더에 `{feature}.js` 파일 생성
2. 아래 형식으로 케이스 정의:

```javascript
const MY_TESTS = {
  feature: "feature-id",
  screen: "화면명",
  cases: [
    {
      id: "FEAT-001",
      title: "테스트 케이스 제목",
      precondition: "전제 조건",
      steps: ["1단계", "2단계"],
      expected: "기대 결과"
    }
  ]
};
export default MY_TESTS;
```

3. `index.html`에서 import 및 `renderer.registerModule()` 추가
4. `shared/test-manifest.js`에 항목 추가
