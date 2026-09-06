/**
 * 관리자 페이지 — A(스펙 관리 + RBAC) 범위
 * Priority: medium
 *
 * 범위 구분:
 * - A (이번 작업): RBAC(라우트 가드/nav 게이팅/관리 API 403), 스펙 목록/상세 표시,
 *   상태 관리(비활성/활성/삭제), 공용/관리자 목록 노출 분리, 빈 상태.
 * - B (다음 작업): 사용자 관리(목록/역할변경/추가/비활성) — ADMIN-030~ (skip 대상, 삭제 금지).
 * - 별도 작업: 서비스 설명 편집(yml보다 우선) — ADMIN-020 (skip 대상, 삭제 금지).
 *
 * 스펙 등록/재등록/상태 전이 자체의 원천 검증은 spec.js에서 커버.
 * admin.js는 "관리자 화면에서의 조작 · RBAC · 목록/상세 표시"가 관심사.
 */
const ADMIN_TESTS = {
  feature: "admin",
  screen: "관리자 페이지 (스펙 관리 + RBAC)",
  cases: [
    // ── RBAC (라우트 가드 / nav 게이팅 / 관리 API 강제) ─────────────
    {
      id: "ADMIN-001",
      title: "[A] 일반 사용자 /admin/specs 직접 접근 차단 (리다이렉트)",
      precondition: "일반 사용자(USER) 계정으로 로그인된 상태",
      steps: [
        "주소창에 /admin/specs 직접 입력하여 접근 시도",
        "RequireAdmin 가드가 동작해 '/'로 리다이렉트되는지 확인",
        "스펙 관리 화면 내용이 잠시라도 노출되지 않는지 확인"
      ],
      expected: "non-admin은 /admin/specs 진입 불가, '/'로 리다이렉트"
    },
    {
      id: "ADMIN-002",
      title: "[A] 관리자 nav 노출 게이팅 (ADMIN만 🖥️ 스펙 관리 표시)",
      precondition: "일반 사용자 계정 / 관리자 계정 각각 준비",
      steps: [
        "일반 사용자로 로그인 후 사이드바 하단 확인 → 🖥️ 스펙 관리 nav가 보이지 않음 확인",
        "관리자로 로그인 후 사이드바 하단 확인 → 🖥️ 스펙 관리 nav 노출 확인",
        "👥 사용자 관리 nav는 A 범위에서 미표시(또는 비활성 placeholder)인지 확인"
      ],
      expected: "관리자 nav는 role==ADMIN일 때만 렌더, 사용자 관리는 A에서 미표시/비활성"
    },
    {
      id: "ADMIN-003",
      title: "[A] 관리자 진입 정상 (ADMIN 계정)",
      precondition: "관리자(ADMIN) 계정으로 로그인된 상태",
      steps: [
        "🖥️ 스펙 관리 nav 클릭 또는 /admin/specs 직접 접근",
        "스펙 관리 목록 화면이 정상 렌더되는지 확인"
      ],
      expected: "관리자는 /admin/specs 정상 진입"
    },
    {
      id: "ADMIN-004",
      title: "[A] 비-admin 관리 API 직접 호출 시 403 (서버 강제)",
      precondition: "일반 사용자 세션 (브라우저 devtools/콘솔에서 fetch 호출 가능)",
      steps: [
        "PATCH /api/v1/specs/{id}/deactivate 를 일반 사용자 세션으로 직접 호출",
        "PATCH /api/v1/specs/{id}/activate 를 직접 호출",
        "DELETE /api/v1/specs/{id} 를 직접 호출",
        "세 요청 모두 403 응답인지 확인 (nav 숨김은 UX일 뿐 서버가 강제)"
      ],
      expected: "관리 액션(비활성/활성/삭제)은 비-admin 호출 시 모두 403"
    },

    // ── 스펙 목록 (/admin/specs) ────────────────────────────────
    {
      id: "ADMIN-005",
      title: "[A] 스펙 목록 컬럼 표시 (서비스명/baseUrl/상태/API수/액션)",
      precondition: "관리자 로그인, 스펙이 1건 이상 등록됨",
      steps: [
        "/admin/specs 진입",
        "각 행에 서비스명 / baseUrl / 상태 배지(ACTIVE·INACTIVE 텍스트) / API 수 / 등록 시각 / 액션 표시 확인",
        "상태 배지가 ACTIVE(🟢)·INACTIVE(⚪) 텍스트로 구분되는지 확인"
      ],
      expected: "목록에 서비스명/baseUrl/상태/API수/등록시각/액션이 표시"
    },
    {
      id: "ADMIN-006",
      title: "[A] INACTIVE 스펙 dimmed 표시 + 관리자 목록은 전체(INACTIVE 포함)",
      precondition: "관리자 로그인, ACTIVE·INACTIVE 스펙이 각각 존재",
      steps: [
        "/admin/specs 진입 (내부적으로 includeInactive=true 조회)",
        "INACTIVE 스펙이 목록에 포함되는지 확인",
        "INACTIVE 행이 흐리게(dimmed) 표시되어 ACTIVE와 시각적으로 구분되는지 확인",
        "소프트 삭제된 스펙은 관리자 목록에서도 제외되는지 확인"
      ],
      expected: "관리자 목록은 INACTIVE 포함 전체 조회, INACTIVE는 dimmed, 삭제는 제외"
    },
    {
      id: "ADMIN-007",
      title: "[A] 공용 목록(ACTIVE만) vs 관리자 목록(전체) 노출 분리",
      precondition: "ACTIVE·INACTIVE 스펙이 각각 존재",
      steps: [
        "일반 사용자로 레시피 편집 화면의 서비스 드롭다운(공용 GET /api/v1/specs) 확인",
        "드롭다운에 ACTIVE 스펙만 노출되고 INACTIVE는 빠지는지 확인",
        "관리자 /admin/specs 목록에서는 동일 INACTIVE 스펙이 (dimmed로) 보이는지 대조 확인"
      ],
      expected: "공용 목록/드롭다운은 ACTIVE만, 관리자 목록은 INACTIVE 포함 — 분리 확인"
    },
    {
      id: "ADMIN-008",
      title: "[A] 스펙 목록 빈 상태 (0건)",
      precondition: "관리자 로그인, 등록된 스펙이 하나도 없음",
      steps: [
        "/admin/specs 진입",
        "'등록된 스펙이 없습니다' 안내 표시 확인",
        "외부 서버가 client-spring 라이브러리로 등록하면 자동 노출된다는 안내가 함께 표시되는지 확인"
      ],
      expected: "스펙 0건이면 빈 상태 안내 + 등록 방법 안내 표시"
    },
    {
      id: "ADMIN-009",
      title: "[A] 목록 → 상세 진입 (서비스명 링크, 키보드 접근)",
      precondition: "관리자 로그인, 스펙 1건 이상",
      steps: [
        "목록에서 서비스명 링크를 마우스로 클릭 → /admin/specs/:id 상세로 이동 확인",
        "목록으로 복귀 후 Tab으로 서비스명 링크에 포커스 → Enter로 상세 진입 가능한지 확인"
      ],
      expected: "서비스명 링크로 상세 진입, 마우스·키보드(Enter) 모두 동작"
    },

    // ── 스펙 상세 (/admin/specs/:id, 별도 페이지) ────────────────
    {
      id: "ADMIN-010",
      title: "[A] 스펙 상세 기본 정보 표시",
      precondition: "관리자 로그인, 스펙 상세 진입",
      steps: [
        "서비스명 / baseUrl / 상태 / 등록 시각 표시 확인",
        "상세가 모달이 아닌 별도 페이지(/admin/specs/:id)로 열리는지 확인"
      ],
      expected: "기본 정보 4항목이 별도 페이지에 표시"
    },
    {
      id: "ADMIN-011",
      title: "[A] 서비스 설명 읽기 전용 표시 (편집 버튼 비활성)",
      precondition: "관리자 로그인, 스펙 상세 진입",
      steps: [
        "description / domain / capabilities / notes 4개 필드 표시 확인",
        "관리자 수정본이 있으면 그 값, 없으면 yml 원본이 표시되는지 확인",
        "[편집] 버튼이 비활성(disabled) 상태이며 '별도 작업' 임을 확인 (A 범위에서는 편집 불가)"
      ],
      expected: "서비스 설명은 읽기 전용 표시, 편집은 비활성(별도 작업)"
    },
    {
      id: "ADMIN-012",
      title: "[A] API 엔드포인트 목록 + DEPRECATED 뱃지 표시",
      precondition: "관리자 로그인, 재등록으로 일부 API가 스펙에서 사라진 스펙의 상세",
      steps: [
        "API 목록에 method + path + summary 표시 확인",
        "재등록 시 사라진 API에 DEPRECATED 뱃지가 표시되는지 확인(물리 삭제 아님)"
      ],
      expected: "API 목록 표시, 사라진 API는 DEPRECATED 뱃지로 마킹"
    },
    {
      id: "ADMIN-013",
      title: "[A] 인증 프로필 표시",
      precondition: "관리자 로그인, 인증 프로필이 있는 스펙 상세",
      steps: [
        "인증 프로필의 name + loginPageUrl 표시 확인"
      ],
      expected: "인증 프로필(name/loginPageUrl)이 상세에 표시"
    },
    {
      id: "ADMIN-014",
      title: "[A] 삭제된 스펙 상세 접근 시 404",
      precondition: "관리자 로그인, 소프트 삭제된 스펙의 id를 알고 있음",
      steps: [
        "/admin/specs/:id (삭제된 스펙 id)로 직접 접근",
        "404(없는 리소스) 처리되는지 확인 (삭제 = 없는 것으로 취급)"
      ],
      expected: "삭제된 스펙 상세 접근은 404"
    },

    // ── 상태 관리 (비활성/활성/삭제) ─────────────────────────────
    {
      id: "ADMIN-015",
      title: "[A] 스펙 비활성화 (ConfirmModal 확인)",
      precondition: "관리자 로그인, ACTIVE 스펙 상세",
      steps: [
        "[비활성화] 클릭",
        "ConfirmModal 표시 확인",
        "취소 시 상태 유지(ACTIVE) 확인",
        "다시 [비활성화] → 확인 시 상태가 INACTIVE로 전환되는지 확인",
        "비활성 후 공용 목록/드롭다운(ACTIVE만)에서 빠지는지 확인"
      ],
      expected: "비활성화는 ConfirmModal 확인 후 INACTIVE 전환, 공용 노출에서 제외"
    },
    {
      id: "ADMIN-016",
      title: "[A] 스펙 활성화 (즉시, 확인 불필요)",
      precondition: "관리자 로그인, INACTIVE 스펙 상세",
      steps: [
        "[활성화] 클릭",
        "확인 모달 없이 즉시 ACTIVE로 복귀하는지 확인",
        "활성 후 공용 목록/드롭다운에 다시 노출되는지 확인"
      ],
      expected: "활성화는 즉시 처리(확인 없음), ACTIVE 복귀 후 공용 노출 재개"
    },
    {
      id: "ADMIN-017",
      title: "[A] 상태 전이 멱등성 (이미 그 상태면 no-op)",
      precondition: "관리자 로그인, 상태를 아는 스펙",
      steps: [
        "이미 ACTIVE인 스펙에 activate 재요청 → 에러 없이 ACTIVE 유지(no-op) 확인",
        "이미 INACTIVE인 스펙에 deactivate 재요청 → 에러 없이 INACTIVE 유지(no-op) 확인"
      ],
      expected: "activate/deactivate는 멱등 — 동일 상태 재요청 시 no-op"
    },
    {
      id: "ADMIN-018",
      title: "[A] INACTIVE 스펙 바로 삭제 (ConfirmModal 확인)",
      precondition: "관리자 로그인, INACTIVE 스펙 상세",
      steps: [
        "[삭제] 클릭",
        "ConfirmModal 표시 확인",
        "확인 시 소프트 삭제 처리되고 목록(관리자·공용 모두)에서 제외되는지 확인",
        "삭제 후 해당 상세 재접근 시 404 확인"
      ],
      expected: "INACTIVE에서 바로 삭제 가능, ConfirmModal 확인 후 소프트 삭제 + 목록 제외 + 상세 404"
    },
    {
      id: "ADMIN-019",
      title: "[A] 삭제된 스펙 참조 레시피 경고",
      precondition: "삭제된(또는 INACTIVE) 스펙을 참조하는 레시피 존재",
      steps: [
        "해당 레시피 실행 전 유효성 검증 단계 진입",
        "삭제/비활성 스펙 참조에 대한 경고가 표시되는지 확인 (참조 레시피 자동 삭제는 없음)"
      ],
      expected: "삭제/INACTIVE 스펙 참조 레시피는 실행 전 검증에서 경고"
    },

    // ── 별도 작업 (skip 대상, 삭제 금지) ─────────────────────────
    {
      id: "ADMIN-020",
      title: "[별도 작업] 서비스 설명 관리자 편집 (yml보다 우선) — 이번 A 범위 밖",
      precondition: "※ 별도 작업. A 범위에서는 편집 비활성(ADMIN-011 참조). B/구현 시 활성화되면 테스트.",
      steps: [
        "(별도 작업 시) [편집] 활성화 후 description 등 수정 → 저장 → '관리자 수정됨' 배지 확인",
        "(별도 작업 시) 라이브러리 재등록(yml 값 다름) 시에도 관리자 값 유지 확인",
        "(별도 작업 시) yml 변경 감지 시 경고 + 변경 내용 보기(강제 덮어쓰기 안 함) 확인"
      ],
      expected: "[SKIP] 서비스 설명 편집은 별도 작업 — A 범위에서는 편집 불가(읽기 전용만)"
    },

    // ── B: 사용자 관리 (다음 작업, skip 대상, 삭제 금지) ──────────
    {
      id: "ADMIN-030",
      title: "[B — 다음 작업] 사용자 목록 표시",
      precondition: "※ B(다음 작업). A 범위에서 사용자 관리 nav는 미표시/비활성.",
      steps: [
        "(B 작업 시) 사용자 관리 탭 진입",
        "(B 작업 시) 아이디/역할(ADMIN·USER)/생성일/마지막 접속/상태 표시 확인"
      ],
      expected: "[SKIP] 사용자 관리는 B(다음 작업) — 이번 A 범위 아님"
    },
    {
      id: "ADMIN-031",
      title: "[B — 다음 작업] 사용자 역할 변경 (confirm)",
      precondition: "※ B(다음 작업).",
      steps: [
        "(B 작업 시) USER → ADMIN 역할 변경 시도 → confirm 모달 확인",
        "(B 작업 시) 확인 시 반영(다음 요청부터), 취소 시 원복 확인"
      ],
      expected: "[SKIP] 역할 변경은 B(다음 작업) — 이번 A 범위 아님"
    },
    {
      id: "ADMIN-032",
      title: "[B — 다음 작업] 사용자 추가 (계정 생성)",
      precondition: "※ B(다음 작업). 셀프 회원가입 없음, 관리자가 생성.",
      steps: [
        "(B 작업 시) [+ 사용자 추가] → 아이디+비밀번호+역할 선택 → [생성]",
        "(B 작업 시) 목록에 신규 사용자 반영 확인 (관리자가 비밀번호 직접 지정)"
      ],
      expected: "[SKIP] 사용자 추가는 B(다음 작업) — 이번 A 범위 아님"
    },
    {
      id: "ADMIN-033",
      title: "[B — 다음 작업] 계정 비활성화 (모달 확인)",
      precondition: "※ B(다음 작업).",
      steps: [
        "(B 작업 시) 계정 비활성화 → 확인 모달 → 확인 시 비활성 전환",
        "(B 작업 시) 비활성 계정은 다음 요청부터 401(세션 폐기)로 로그인 차단 확인"
      ],
      expected: "[SKIP] 계정 비활성화는 B(다음 작업) — 이번 A 범위 아님"
    }
  ]
};

export default ADMIN_TESTS;
