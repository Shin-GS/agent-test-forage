/**
 * 스펙 등록/관리
 * Priority: high
 */
const SPEC_TESTS = {
  feature: "spec",
  screen: "스펙 관리",
  cases: [
    // === 클라이언트 라이브러리 등록 ===
    {
      id: "SPEC-001",
      title: "클라이언트 라이브러리 push 등록 — Spring Boot 자동",
      precondition: "Spring Boot 서버에 클라이언트 라이브러리 설치됨",
      steps: [
        "Spring Boot 서버 시작",
        "라이브러리가 자동으로 스펙 등록 API 호출",
        "스펙 목록에 새 서비스 표시 확인",
        "상태가 ACTIVE로 표시되는지 확인"
      ],
      expected: "서버 시작 시 자동으로 스펙이 등록되고 ACTIVE 상태 표시"
    },
    {
      id: "SPEC-002",
      title: "heartbeat 유지 — ACTIVE 상태",
      precondition: "스펙이 등록된 서버 실행 중",
      steps: [
        "서버가 주기적으로 heartbeat 전송 확인 (서버 로그)",
        "스펙 상태가 ACTIVE 유지 확인",
        "마지막 heartbeat 시간 표시 확인"
      ],
      expected: "heartbeat 수신 중이면 ACTIVE 상태 유지, 마지막 응답 시간 표시"
    },
    {
      id: "SPEC-003",
      title: "heartbeat 중단 → STALE 전환",
      precondition: "스펙이 ACTIVE 상태",
      steps: [
        "등록된 서버 종료 (heartbeat 중단)",
        "설정된 타임아웃 대기 (기본 5분)",
        "스펙 상태가 STALE로 변경되는지 확인",
        "STALE 경고 표시 확인"
      ],
      expected: "heartbeat 미수신 시 STALE 상태로 전환, 경고 UI 표시"
    },
    {
      id: "SPEC-004",
      title: "STALE → 자동 소프트 삭제 (24시간)",
      precondition: "스펙이 STALE 상태",
      steps: [
        "STALE 상태 24시간 경과 대기",
        "스펙이 자동 소프트 삭제되는지 확인 (DELETED_AT 설정)",
        "삭제 후 목록에서 제거 확인"
      ],
      expected: "STALE 24시간 경과 후 스펙 자동 소프트 삭제, 목록에서 제거"
    },
    // === 관리자 수동 관리 ===
    {
      id: "SPEC-005",
      title: "스펙 수동 비활성화 (INACTIVE)",
      precondition: "ACTIVE 또는 STALE 스펙 존재",
      steps: [
        "스펙 상세에서 [비활성화] 토글 클릭",
        "STATUS가 INACTIVE로 변경되는지 확인",
        "AI 매칭/실행 대상에서 제외되는지 확인",
        "INACTIVE는 자동 삭제 대상에서 제외되는지 확인"
      ],
      expected: "INACTIVE로 전환, AI 매칭/실행 제외, 자동 삭제 대상 아님"
    },
    {
      id: "SPEC-006",
      title: "좀비 스펙 정리 — baseUrl 변경",
      precondition: "서버 URL 변경으로 구 baseUrl 스펙이 STALE로 남음",
      steps: [
        "구 baseUrl 스펙이 heartbeat 끊겨 STALE 표시 확인",
        "관리자가 구 스펙을 수동 INACTIVE 또는 삭제",
        "신규 baseUrl 스펙이 별도로 ACTIVE 등록됨 확인",
        "목록이 신규 스펙만 유효 상태로 정리되는지 확인"
      ],
      expected: "구 baseUrl 좀비 스펙을 관리자가 수동 정리, 신규 스펙만 유효"
    },
    // === API 엔드포인트 ===
    {
      id: "SPEC-007",
      title: "API 엔드포인트 목록 표시",
      precondition: "스펙 등록 완료",
      steps: [
        "스펙 상세 페이지 진입",
        "등록된 API 엔드포인트 목록 표시 확인",
        "각 엔드포인트의 메서드, 경로, 설명 표시 확인",
        "요청/응답 스키마 확인 가능 여부"
      ],
      expected: "등록된 모든 API 엔드포인트가 메서드/경로/설명과 함께 표시"
    },
    {
      id: "SPEC-008",
      title: "API 엔드포인트 검색/필터",
      precondition: "다수의 엔드포인트 등록됨",
      steps: [
        "검색 필드에 키워드 입력",
        "필터 결과 확인",
        "메서드별 필터 (GET/POST/PUT/DELETE) 확인"
      ],
      expected: "키워드 및 메서드 기준으로 엔드포인트 필터링"
    },
    // === 인증 프로필 (name + loginPageUrl) ===
    {
      id: "SPEC-009",
      title: "인증 프로필 정보 표시",
      precondition: "인증 프로필이 등록된 스펙 존재",
      steps: [
        "스펙 상세에서 인증 프로필 섹션 확인",
        "프로필 이름(name) 표시 확인",
        "로그인 페이지 URL(loginPageUrl) 표시 확인"
      ],
      expected: "인증 프로필의 name과 loginPageUrl이 표시됨 (토큰/키 저장 없음)"
    },
    {
      id: "SPEC-010",
      title: "레시피 실행 시 인증 필요 → 새 탭 로그인",
      precondition: "인증 프로필이 연결된 레시피, 미인증 상태",
      steps: [
        "레시피 실행 시 401 발생 확인",
        "채팅에 [로그인] 안내 카드 표시 확인",
        "loginPageUrl이 새 탭으로 열리는지 확인",
        "외부 서버에서 로그인 완료 후 [로그인 완료] 클릭 → 실행 재개 확인"
      ],
      expected: "인증 필요 시 새 탭 loginPageUrl 이동, 쿠키 세션 획득 후 실행 재개"
    },
    // === 삭제 ===
    {
      id: "SPEC-011",
      title: "스펙 수동 삭제",
      precondition: "삭제할 스펙 존재",
      steps: [
        "스펙 삭제 버튼 클릭",
        "삭제 확인 다이얼로그 표시",
        "'이 스펙을 참조하는 레시피' 경고 표시 확인",
        "확인 클릭 후 삭제 완료"
      ],
      expected: "참조 레시피 경고 후 확인 시 스펙 삭제, 관련 레시피에 경고 표시"
    },
    {
      id: "SPEC-012",
      title: "STALE 스펙 수동 재활성화",
      precondition: "스펙이 STALE 상태, 서버 재시작됨",
      steps: [
        "서버 재시작으로 heartbeat 재개",
        "스펙 상태가 STALE → ACTIVE로 변경 확인",
        "자동 삭제 타이머 해제 확인"
      ],
      expected: "heartbeat 재개 시 즉시 ACTIVE로 복원, 삭제 예약 취소"
    }
  ]
};

export default SPEC_TESTS;
