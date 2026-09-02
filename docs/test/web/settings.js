/**
 * 설정 (읽기 전용 + 비밀번호 변경)
 * Priority: medium
 *
 * AI 설정/레시피 실행 설정은 서버 설정 파일로만 변경되며 화면에서는 조회만 가능하다.
 * 편집 가능한 항목은 계정(비밀번호 변경)뿐이다.
 */
const SETTINGS_TESTS = {
  feature: "settings",
  screen: "설정 페이지",
  cases: [
    {
      id: "SETTINGS-001",
      title: "AI 설정 읽기 전용 표시",
      precondition: "설정 페이지 진입, AI 설정 섹션",
      steps: [
        "AI 설정 섹션 확인",
        "Provider가 'OpenRouter'로 표시되는지 확인 (드롭다운/편집 컨트롤 없음)",
        "Reasoning/Fast 모델명이 텍스트로 표시되는지 확인",
        "'서버 설정 파일로만 변경됩니다' 안내 문구 확인"
      ],
      expected: "AI 설정이 값 텍스트로만 표시되고, 편집 컨트롤(드롭다운/입력/저장)이 없다"
    },
    {
      id: "SETTINGS-002",
      title: "레시피 실행 설정 읽기 전용 표시",
      precondition: "설정 페이지, 레시피 실행 설정 섹션",
      steps: [
        "스텝 타임아웃/전체 타임아웃이 값 텍스트로 표시되는지 확인",
        "편집 컨트롤이 없는지 확인",
        "'서버 설정 파일로만 변경됩니다' 안내 확인"
      ],
      expected: "실행 설정이 조회만 되고 편집 불가"
    },
    {
      id: "SETTINGS-003",
      title: "설정 저장 버튼 부재 확인",
      precondition: "설정 페이지",
      steps: [
        "AI/실행 설정 섹션에 [저장] 버튼이 없는지 확인",
        "값을 바꿀 방법이 화면에 없는지 확인"
      ],
      expected: "설정 저장 경로가 화면에 없다 (파일로만 변경)"
    },
    {
      id: "SETTINGS-004",
      title: "AI 설정 조회 API — 시크릿 미노출",
      precondition: "설정 조회 API 응답",
      steps: [
        "GET /api/v1/settings 응답 확인",
        "provider/모델/이력수/타임아웃이 포함되는지 확인",
        "API 키 등 시크릿이 응답에 없는지 확인"
      ],
      expected: "현재 적용값은 조회되되 API 키 같은 시크릿은 응답에 포함되지 않음"
    },
    {
      id: "SETTINGS-005",
      title: "비밀번호 변경 — 정상",
      precondition: "계정 설정 섹션",
      steps: [
        "'변경하기' 클릭 → 비밀번호 변경 폼 표시",
        "현재 비밀번호 입력",
        "새 비밀번호 + 확인 입력 (일치)",
        "변경 클릭"
      ],
      expected: "비밀번호 변경 성공 토스트 표시"
    },
    {
      id: "SETTINGS-006",
      title: "비밀번호 변경 — 현재 비밀번호 불일치",
      precondition: "계정 설정 섹션, 변경 폼",
      steps: [
        "틀린 현재 비밀번호 입력",
        "새 비밀번호 입력 후 변경 시도"
      ],
      expected: "현재 비밀번호 불일치 에러 표시, 변경되지 않음"
    },
    {
      id: "SETTINGS-007",
      title: "비밀번호 변경 — 새 비밀번호 확인 불일치",
      precondition: "계정 설정 섹션, 변경 폼",
      steps: [
        "현재 비밀번호 정상 입력",
        "새 비밀번호와 확인 값을 다르게 입력",
        "변경 시도"
      ],
      expected: "새 비밀번호 확인 불일치 에러 표시, 변경되지 않음"
    }
  ]
};

export default SETTINGS_TESTS;
