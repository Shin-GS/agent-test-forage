/**
 * 관리자 페이지 (사용자 관리 + 서비스 설명)
 * Priority: medium
 * 스펙 등록/상태(ACTIVE/STALE/INACTIVE/삭제)는 spec.js에서 커버.
 */
const ADMIN_TESTS = {
  feature: "admin",
  screen: "관리자 페이지",
  cases: [
    {
      id: "ADMIN-001",
      title: "관리자 전용 접근 제어",
      precondition: "일반 사용자 계정 로그인",
      steps: [
        "일반 사용자로 /admin 직접 접근 시도",
        "접근 거부(리다이렉트 또는 403) 확인",
        "관리자 계정으로 재접근 시 정상 진입 확인"
      ],
      expected: "관리자만 접근 가능, 일반 사용자는 차단"
    },
    {
      id: "ADMIN-002",
      title: "서비스 설명 표시 (스펙 상세)",
      precondition: "스펙 상세 진입",
      steps: [
        "description/domain/capabilities/notes 표시 확인",
        "라이브러리 yml에서 온 값이 표시되는지 확인"
      ],
      expected: "서비스 설명 4개 필드가 스펙 상세에 표시"
    },
    {
      id: "ADMIN-003",
      title: "서비스 설명 관리자 편집 (yml보다 우선)",
      precondition: "스펙 상세, [편집] 가능",
      steps: [
        "[편집] 클릭 후 description 등 수정",
        "저장 후 '관리자 수정됨' 배지 표시 확인",
        "이후 라이브러리 재등록(yml 값 다름) 시에도 관리자 값이 유지되는지 확인",
        "yml 변경 감지 시 '변경 감지' 경고 + 변경 내용 보기 표시 (강제 덮어쓰기 안 함) 확인"
      ],
      expected: "관리자 편집값이 yml보다 우선, yml 변경은 경고만 표시"
    },
    {
      id: "ADMIN-004",
      title: "API 엔드포인트 DEPRECATED 표시",
      precondition: "재등록으로 일부 API가 스펙에서 사라짐",
      steps: [
        "스펙 상세 API 목록 진입",
        "사라진 API에 DEPRECATED 배지 표시 확인 (물리 삭제 아님)",
        "해당 API를 참조하는 레시피에 경고 표시 확인"
      ],
      expected: "사라진 API는 DEPRECATED 마킹, 참조 레시피에 경고"
    },
    {
      id: "ADMIN-005",
      title: "사용자 목록 표시",
      precondition: "관리자 페이지 사용자 관리 탭",
      steps: [
        "사용자 목록 표시 확인 (아이디/역할/상태)",
        "역할(ADMIN/USER)과 계정 상태 표시 확인"
      ],
      expected: "사용자 목록이 역할/상태와 함께 표시"
    },
    {
      id: "ADMIN-006",
      title: "사용자 역할 변경 (confirm 필요)",
      precondition: "사용자 목록",
      steps: [
        "특정 사용자의 역할 변경 시도 (USER → ADMIN)",
        "확인(confirm) 모달 표시 확인",
        "확인 시 역할 변경 반영, 취소 시 원복 확인"
      ],
      expected: "역할 변경은 confirm 후 적용"
    },
    {
      id: "ADMIN-007",
      title: "사용자 초대",
      precondition: "사용자 관리 탭",
      steps: [
        "[초대] 클릭 후 아이디 입력 + 역할 지정",
        "임시 비밀번호가 생성되는지 확인",
        "목록에 신규 사용자 반영 확인"
      ],
      expected: "아이디+역할로 초대 생성, 임시 비밀번호 발급 후 목록에 반영"
    },
    {
      id: "ADMIN-008",
      title: "계정 비활성화 (모달 확인)",
      precondition: "사용자 목록",
      steps: [
        "특정 사용자 계정 비활성화 시도",
        "확인 모달 표시 확인",
        "확인 시 비활성 상태로 전환, 해당 계정 로그인 차단 확인"
      ],
      expected: "계정 비활성화는 모달 확인 후 적용, 로그인 차단"
    }
  ]
};

export default ADMIN_TESTS;
