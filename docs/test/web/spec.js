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
        "라이브러리가 자동으로 스펙 등록 API 호출 (기동당 1회)",
        "스펙 목록에 새 서비스 표시 확인",
        "상태가 ACTIVE로 표시되는지 확인"
      ],
      expected: "서버 시작 시 자동으로 스펙이 1회 등록되고 ACTIVE 상태 표시"
    },
    {
      id: "SPEC-002",
      title: "재기동 시 재등록 (upsert) — 최신 스펙 갱신",
      precondition: "이미 등록된 서버가 재배포/재기동됨",
      steps: [
        "등록된 서버를 스펙 변경 후 재기동",
        "재기동 시 다시 1회 등록되는지 확인",
        "같은 baseUrl 스펙이 새로 추가되지 않고 기존 항목이 갱신되는지 확인 (upsert)",
        "엔드포인트가 최신 스펙으로 갱신되고, 사라진 API는 DEPRECATED 표시 확인"
      ],
      expected: "재기동 시 같은 baseUrl 스펙을 upsert로 갱신 (중복 생성 없음, PK 유지)"
    },
    // === 관리자 수동 관리 ===
    {
      id: "SPEC-003",
      title: "스펙 수동 비활성화 (INACTIVE)",
      precondition: "ACTIVE 스펙 존재",
      steps: [
        "스펙 상세에서 [비활성화] 토글 클릭",
        "STATUS가 INACTIVE로 변경되는지 확인",
        "AI 매칭/실행 대상에서 제외되는지 확인"
      ],
      expected: "INACTIVE로 전환, AI 매칭/실행 제외"
    },
    {
      id: "SPEC-004",
      title: "좀비 스펙 정리 — baseUrl 변경",
      precondition: "서버 URL 변경으로 구 baseUrl 스펙이 남음",
      steps: [
        "구 baseUrl 스펙은 더 이상 등록되지 않고 ACTIVE로 남아 있음 확인 (자동 삭제 없음)",
        "관리자가 구 스펙을 수동 INACTIVE 또는 삭제",
        "신규 baseUrl 스펙이 별도로 ACTIVE 등록됨 확인",
        "목록이 신규 스펙만 유효 상태로 정리되는지 확인"
      ],
      expected: "구 baseUrl 좀비 스펙을 관리자가 수동 정리, 신규 스펙만 유효"
    },
    // === API 엔드포인트 ===
    {
      id: "SPEC-005",
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
      id: "SPEC-006",
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
      id: "SPEC-007",
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
      id: "SPEC-008",
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
    // === 재등록 시 인증 프로필 (소프트 upsert) ===
    {
      id: "SPEC-009",
      title: "재등록 시 인증 프로필 갱신 (upsert)",
      precondition: "인증 프로필이 등록된 스펙, 프로필 변경 후 재기동",
      steps: [
        "인증 프로필을 수정/추가/삭제한 뒤 서버 재기동",
        "같은 이름(name) 프로필은 기존 항목이 갱신되는지 확인 (중복 생성 없음)",
        "스펙에서 사라진 프로필은 물리 삭제가 아니라 비활성 처리되는지 확인",
        "삭제됐던 프로필이 다시 나타나면 부활(재활성)되는지 확인"
      ],
      expected: "인증 프로필도 소프트 upsert (하드 삭제 없음, 부활은 재활성)"
    },
    // === 삭제 ===
    {
      id: "SPEC-010",
      title: "스펙 수동 삭제",
      precondition: "삭제할 스펙 존재",
      steps: [
        "스펙 삭제 버튼 클릭",
        "삭제 확인 다이얼로그 표시",
        "'이 스펙을 참조하는 레시피' 경고 표시 확인",
        "확인 클릭 후 삭제 완료"
      ],
      expected: "참조 레시피 경고 후 확인 시 스펙 삭제, 관련 레시피에 경고 표시"
    }
  ]
};

export default SPEC_TESTS;
