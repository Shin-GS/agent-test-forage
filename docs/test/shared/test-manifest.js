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
    description: "이메일/비밀번호 로그인, JWT 세션, 만료 처리, 비밀번호 변경"
  },
  {
    id: "spec",
    name: "스펙 등록/관리",
    file: "../web/spec.js",
    priority: "high",
    description: "클라이언트 라이브러리 등록, heartbeat, ACTIVE/STALE, 수동 업로드"
  },
  {
    id: "settings",
    name: "설정",
    file: "../web/settings.js",
    priority: "medium",
    description: "AI Provider/모델 변경, 타임아웃, 비밀번호 변경, 유효성 검증"
  }
];

export default TEST_MANIFEST;
