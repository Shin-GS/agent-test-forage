/**
 * 테스트 파일 매니페스트
 * 모든 기능별 테스트 데이터 파일 목록을 관리합니다.
 */
const TEST_MANIFEST = [
  {
    id: "chat",
    name: "채팅 + 레시피 실행 + 플랜",
    file: "../web/chat.js",
    priority: "critical",
    description: "메시지 전송, SSE 응답, 레시피 실행, 플랜 제안/실행, 액션 피커, 에러 처리"
  },
  {
    id: "panel",
    name: "사이드 패널",
    file: "../web/panel.js",
    priority: "high",
    description: "레시피 목록/상세, 히스토리, 결과 상세, 패널 리사이즈, 채팅 목록 접기"
  },
  {
    id: "recipe-editor",
    name: "레시피 편집/관리",
    file: "../web/recipe-editor.js",
    priority: "high",
    description: "레시피 CRUD, 스텝 편집, 유효성 검증, 변수 관리"
  },
  {
    id: "login",
    name: "로그인/인증",
    file: "../web/login.js",
    priority: "high",
    description: "아이디/비밀번호 로그인, JWT 세션, 만료 처리, 비밀번호 변경"
  },
  {
    id: "spec",
    name: "스펙 등록/관리",
    file: "../web/spec.js",
    priority: "high",
    description: "클라이언트 라이브러리 등록, heartbeat, ACTIVE/STALE, 관리자 수동 관리"
  },
  {
    id: "settings",
    name: "설정",
    file: "../web/settings.js",
    priority: "medium",
    description: "AI/실행 설정 읽기 전용 조회(파일로만 변경), 비밀번호 변경"
  },
  {
    id: "history",
    name: "전체 히스토리 페이지",
    file: "../web/history.js",
    priority: "medium",
    description: "히스토리 테이블/필터, 결과 상세, 플랜 히스토리, 대화 삭제 후 독립 유지"
  },
  {
    id: "admin",
    name: "관리자 페이지",
    file: "../web/admin.js",
    priority: "medium",
    description: "접근 제어, 서비스 설명 편집(yml 우선), DEPRECATED, 사용자 관리(역할/초대/비활성)"
  }
];

export default TEST_MANIFEST;
