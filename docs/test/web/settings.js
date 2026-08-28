/**
 * 설정
 * Priority: medium
 */
const SETTINGS_TESTS = {
  feature: "settings",
  screen: "설정 페이지",
  cases: [
    {
      id: "SETTINGS-001",
      title: "AI Provider 변경",
      precondition: "설정 페이지 진입, AI 설정 섹션",
      steps: [
        "AI Provider 드롭다운 클릭",
        "다른 Provider 선택 (예: OpenAI → Anthropic)",
        "저장 버튼 클릭",
        "변경 반영 확인"
      ],
      expected: "Provider 변경 후 저장 성공, 이후 AI 호출 시 새 Provider 사용"
    },
    {
      id: "SETTINGS-002",
      title: "모델 변경",
      precondition: "Provider 선택된 상태",
      steps: [
        "모델 드롭다운 클릭",
        "사용 가능한 모델 목록 표시 확인",
        "다른 모델 선택",
        "저장 확인"
      ],
      expected: "선택한 모델이 저장되고, 이후 AI 호출에 반영"
    },
    {
      id: "SETTINGS-003",
      title: "대화 이력 전달 수 변경",
      precondition: "AI 설정 섹션",
      steps: [
        "대화 이력 전달 수 필드 확인 (기본값 표시)",
        "값 변경 (예: 10 → 20)",
        "저장 클릭",
        "저장 성공 확인"
      ],
      expected: "설정된 수만큼 이전 대화를 AI에 전달"
    },
    {
      id: "SETTINGS-004",
      title: "타임아웃 설정 변경",
      precondition: "AI 설정 섹션",
      steps: [
        "타임아웃 값 확인 (기본값 표시)",
        "값 변경 (예: 30초 → 60초)",
        "저장 클릭",
        "저장 성공 확인"
      ],
      expected: "변경된 타임아웃이 저장되고, API 호출 시 적용"
    },
    {
      id: "SETTINGS-005",
      title: "비밀번호 변경 — 정상",
      precondition: "계정 설정 섹션",
      steps: [
        "현재 비밀번호 입력",
        "새 비밀번호 입력",
        "새 비밀번호 확인 입력",
        "변경 버튼 클릭",
        "성공 토스트 표시 확인"
      ],
      expected: "비밀번호 변경 성공 토스트 표시"
    },
    {
      id: "SETTINGS-006",
      title: "비밀번호 변경 — 현재 비밀번호 불일치",
      precondition: "계정 설정 섹션",
      steps: [
        "틀린 현재 비밀번호 입력",
        "새 비밀번호 입력",
        "변경 버튼 클릭",
        "에러 메시지 확인"
      ],
      expected: "'현재 비밀번호가 올바르지 않습니다' 에러 표시"
    },
    {
      id: "SETTINGS-007",
      title: "저장 성공 토스트",
      precondition: "설정 변경 후",
      steps: [
        "설정 값 변경",
        "저장 버튼 클릭",
        "성공 토스트 메시지 표시 확인",
        "토스트 자동 사라짐 확인 (3~5초)"
      ],
      expected: "저장 성공 시 토스트 표시 후 자동 사라짐"
    },
    {
      id: "SETTINGS-008",
      title: "유효성 검증 — 빈 필드 및 범위 초과",
      precondition: "설정 페이지",
      steps: [
        "필수 필드를 비우고 저장 시도",
        "에러 메시지 표시 확인",
        "범위 초과 값 입력 (예: 타임아웃 999999)",
        "범위 에러 메시지 확인",
        "유효한 값 입력 후 저장 가능 확인"
      ],
      expected: "유효하지 않은 입력 시 에러 표시, 저장 차단"
    }
  ]
};

export default SETTINGS_TESTS;
