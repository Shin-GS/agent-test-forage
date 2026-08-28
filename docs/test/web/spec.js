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
      title: "STALE → 자동 삭제 (30분)",
      precondition: "스펙이 STALE 상태",
      steps: [
        "STALE 상태 30분 경과 대기",
        "스펙이 자동 삭제되는지 확인",
        "삭제 후 목록에서 제거 확인"
      ],
      expected: "STALE 30분 경과 후 스펙 자동 삭제, 목록에서 제거"
    },
    // === 수동 업로드 ===
    {
      id: "SPEC-005",
      title: "수동 업로드 등록 — OpenAPI JSON",
      precondition: "OpenAPI 3.0 JSON 파일 준비",
      steps: [
        "스펙 등록 페이지에서 '수동 업로드' 선택",
        "OpenAPI JSON 파일 업로드",
        "파싱 결과 미리보기 확인",
        "등록 확인 클릭"
      ],
      expected: "JSON 파일이 파싱되어 API 엔드포인트 목록 표시, 등록 완료"
    },
    {
      id: "SPEC-006",
      title: "수동 업로드 — 잘못된 파일 형식",
      precondition: "비정상 JSON 또는 OpenAPI 미준수 파일",
      steps: [
        "잘못된 형식 파일 업로드 시도",
        "파싱 에러 메시지 표시 확인",
        "등록이 차단되는지 확인"
      ],
      expected: "파싱 에러 메시지 표시, 등록 불가"
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
    // === 인증 프로필 ===
    {
      id: "SPEC-009",
      title: "인증 프로필 정보 표시",
      precondition: "인증이 필요한 스펙 등록됨",
      steps: [
        "스펙 상세에서 인증 프로필 섹션 확인",
        "인증 타입 (Bearer, Basic, API Key 등) 표시",
        "인증 설정 상태 표시"
      ],
      expected: "인증 프로필 정보가 올바르게 표시됨"
    },
    {
      id: "SPEC-010",
      title: "인증 프로필 설정 변경",
      precondition: "인증 프로필 존재",
      steps: [
        "인증 프로필 편집 클릭",
        "인증 정보 수정 (토큰, 키 등)",
        "저장 클릭",
        "변경 사항 반영 확인"
      ],
      expected: "인증 프로필 수정 후 레시피 실행 시 새 인증 정보 사용"
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
