/**
 * 관리자 페이지 — A(스펙 관리 + RBAC) + B(사용자 관리) 범위
 * Priority: medium
 *
 * 범위 구분:
 * - A (완료): RBAC(라우트 가드/nav 게이팅/관리 API 403), 스펙 목록/상세 표시,
 *   상태 관리(비활성/활성/삭제), 공용/관리자 목록 노출 분리, 빈 상태 — ADMIN-001~019.
 * - B (이번 작업): 사용자 관리(/admin/users) — 진입/목록/검색/생성/역할·상태·비밀번호 변경,
 *   자기 보호 + 마지막 ACTIVE ADMIN 보호, 삭제 액션 없음 — ADMIN-030~053.
 * - 별도 작업: 서비스 설명 편집(yml보다 우선) — ADMIN-020 (skip 대상, 삭제 금지).
 *
 * 스펙 등록/재등록/상태 전이 자체의 원천 검증은 spec.js에서 커버.
 * 계정 생성/역할·상태 규칙의 원천 검증은 login.js와 연계. admin.js는 "관리자 화면에서의
 * 조작 · RBAC · 목록/상세 표시 · 사용자 CRUD"가 관심사.
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

    // ── B: 사용자 관리 (/admin/users) — 이번 작업 ────────────────
    // RBAC 뼈대(가드/nav 게이팅/서버 강제)는 A에서 검증. B는 사용자 CRUD·역할/상태·비밀번호 관심사.

    // 진입 / nav 게이팅
    {
      id: "ADMIN-030",
      title: "[B] 일반 사용자 /admin/users 직접 접근 차단 (리다이렉트)",
      precondition: "일반 사용자(USER) 계정으로 로그인된 상태",
      steps: [
        "주소창에 /admin/users 직접 입력하여 접근 시도",
        "RequireAdmin 가드가 동작해 '/'로 리다이렉트되는지 확인",
        "사용자 관리 화면 내용이 잠시라도 노출되지 않는지 확인"
      ],
      expected: "non-admin은 /admin/users 진입 불가, '/'로 리다이렉트"
    },
    {
      id: "ADMIN-031",
      title: "[B] 사이드바 👥 사용자 관리 nav 노출 게이팅 (ADMIN만)",
      precondition: "일반 사용자 계정 / 관리자 계정 각각 준비",
      steps: [
        "일반 사용자로 로그인 후 사이드바 하단 확인 → 👥 사용자 관리 nav가 보이지 않음 확인",
        "관리자로 로그인 후 사이드바 하단 확인 → 👥 사용자 관리 nav 노출 확인",
        "👥 클릭 시 /admin/users 로 이동하는지 확인"
      ],
      expected: "👥 사용자 관리 nav는 role==ADMIN일 때만 렌더, 클릭 시 /admin/users 진입"
    },

    // 목록 화면
    {
      id: "ADMIN-032",
      title: "[B] 사용자 목록 컬럼 표시 (아이디/이름/역할/상태/마지막접속/액션)",
      precondition: "관리자 로그인, 사용자가 2명 이상 존재",
      steps: [
        "/admin/users 진입",
        "각 행에 아이디 / 이름 / 역할 배지(관리자·사용자) / 상태 배지(활성·비활성) / 마지막 접속 / 액션 버튼 표시 확인",
        "역할·상태가 텍스트가 아닌 배지 형태로 구분 표시되는지 확인",
        "이름 미입력 계정은 이름 칸이 비어있거나 placeholder로 표시되는지 확인"
      ],
      expected: "목록에 아이디/이름/역할배지/상태배지/마지막접속/액션이 표시"
    },
    {
      id: "ADMIN-033",
      title: "[B] 본인 행 '(나)' 배지 + INACTIVE 계정 dimmed 표시",
      precondition: "관리자 로그인, ACTIVE·INACTIVE 계정이 각각 존재",
      steps: [
        "/admin/users 진입",
        "세션 사용자 본인 행에 '(나)' 배지가 표시되는지 확인",
        "INACTIVE(비활성) 계정 행이 흐리게(dimmed) 표시되어 ACTIVE와 시각적으로 구분되는지 확인"
      ],
      expected: "본인 행에 '(나)' 배지, INACTIVE 계정은 dimmed로 구분"
    },
    {
      id: "ADMIN-034",
      title: "[B] 아이디 검색 + 결과 카운트 (부분 일치)",
      precondition: "관리자 로그인, 아이디에 'user'가 포함된 계정 여러 개 존재",
      steps: [
        "상단 검색창에 'user' 입력",
        "아이디 부분 일치로 목록이 필터링되는지 확인",
        "필터된 결과 건수(카운트)가 표시되는지 확인",
        "검색어를 지우면 전체 목록(페이징 없이 전체)으로 복귀하는지 확인"
      ],
      expected: "아이디 부분 일치 검색 + 결과 카운트 표시, 검색어 제거 시 전체 복귀"
    },
    {
      id: "ADMIN-035",
      title: "[B] 검색 결과 없음 상태",
      precondition: "관리자 로그인, 존재하지 않을 아이디 문자열 준비",
      steps: [
        "검색창에 어떤 계정과도 일치하지 않는 문자열 입력",
        "'검색 결과가 없습니다' 안내가 표시되는지 확인",
        "(목록 자체는 본인 계정이 항상 있어 완전히 비지 않음 — 검색 결과 없음과 구분되는지 확인)"
      ],
      expected: "검색 결과가 없으면 '검색 결과가 없습니다' 안내 표시"
    },

    // 계정 생성 (모달)
    {
      id: "ADMIN-036",
      title: "[B] 사용자 추가 정상 (모달: 아이디+이름+비밀번호+역할)",
      precondition: "관리자 로그인, 미사용 아이디 준비 (셀프 회원가입 없음 — 관리자가 생성)",
      steps: [
        "[+ 사용자 추가] 클릭 → 폼 모달 표시 확인",
        "아이디(3~50자) + 이름(선택) + 비밀번호(8자 이상) 입력 + 역할 라디오(사용자/관리자) 선택",
        "[생성] 클릭 → 모달 닫힘 + 목록에 신규 계정 반영 확인",
        "생성한 계정의 아이디/비밀번호로 로그인 가능한지 확인 (관리자가 비밀번호 직접 지정)"
      ],
      expected: "모달로 계정 생성, 목록 반영 + 지정한 비밀번호로 로그인 가능"
    },
    {
      id: "ADMIN-037",
      title: "[B] 사용자 추가 아이디 검증 (3~50자, 공백 불가)",
      precondition: "관리자 로그인, 사용자 추가 모달 열림",
      steps: [
        "아이디를 2자 이하로 입력하고 생성 시도 → 검증 실패(400) 안내 확인",
        "아이디에 공백을 포함하여 생성 시도 → 검증 실패(400) 안내 확인 (앞뒤/내부 공백 모두 불가)",
        "아이디를 51자 이상으로 입력하고 생성 시도 → 검증 실패(400) 안내 확인",
        "유효한 아이디(3~50자, 공백 없음)로는 정상 생성되는지 확인"
      ],
      expected: "아이디 3~50자·공백 불가 규칙 위반 시 400, 유효 값은 생성 성공"
    },
    {
      id: "ADMIN-038",
      title: "[B] 사용자 추가 아이디 중복 400",
      precondition: "관리자 로그인, 이미 존재하는 아이디를 알고 있음",
      steps: [
        "사용자 추가 모달에서 이미 존재하는 아이디 입력 + 나머지 유효 입력",
        "[생성] 클릭",
        "아이디 중복으로 400 에러 안내가 표시되고 계정이 생성되지 않는지 확인"
      ],
      expected: "중복 아이디 생성 시 400, 계정 미생성"
    },
    {
      id: "ADMIN-039",
      title: "[B] 사용자 추가 비밀번호 8자 미만 400 + 이름 100자 제한",
      precondition: "관리자 로그인, 사용자 추가 모달 열림",
      steps: [
        "비밀번호를 7자 이하로 입력하고 생성 시도 → 검증 실패(400) 안내 확인",
        "이름을 101자 이상 입력하고 생성 시도 → 검증 실패 안내 확인 (이름은 선택이지만 입력 시 100자 이하)",
        "이름을 비워두고 비밀번호 8자 이상으로는 정상 생성되는지 확인 (이름은 선택)"
      ],
      expected: "비밀번호 8자 미만 400, 이름 100자 초과 거부, 이름 미입력 허용"
    },

    // 역할 변경 (ConfirmModal)
    {
      id: "ADMIN-040",
      title: "[B] 역할 승격 USER→ADMIN (ConfirmModal, 다음 요청부터 반영)",
      precondition: "관리자 로그인, USER 역할의 타 계정 존재",
      steps: [
        "대상 USER 계정 행의 [역할 변경] 클릭 → ConfirmModal 표시 확인",
        "취소 시 역할 유지(USER) 확인",
        "다시 [역할 변경] → 확인 시 역할이 ADMIN(관리자 배지)으로 바뀌는지 확인",
        "해당 계정이 다음 요청부터 관리자 기능(👥 nav 등)을 사용할 수 있는지 확인 (즉시 강제 로그아웃 없음)"
      ],
      expected: "승격은 ConfirmModal 확인 후 ADMIN 전환, 다음 요청부터 반영"
    },
    {
      id: "ADMIN-041",
      title: "[B] 역할 강등 ADMIN→USER (ConfirmModal, 다음 요청부터 반영)",
      precondition: "관리자 로그인, 본인 외 ACTIVE ADMIN 계정이 1명 이상 존재 (ACTIVE ADMIN 총 2명 이상)",
      steps: [
        "본인이 아닌 ADMIN 계정 행의 [역할 변경] 클릭 → ConfirmModal 표시 확인",
        "확인 시 역할이 USER(사용자 배지)로 바뀌는지 확인",
        "강등된 계정이 다음 요청부터 관리자 nav/화면에 접근 불가한지 확인"
      ],
      expected: "강등은 ConfirmModal 확인 후 USER 전환, 다음 요청부터 관리자 접근 차단"
    },

    // 상태 변경 (비활성 ConfirmModal / 활성 즉시)
    {
      id: "ADMIN-042",
      title: "[B] 계정 비활성화 (ConfirmModal, 다음 요청부터 로그인 차단)",
      precondition: "관리자 로그인, 본인이 아닌 ACTIVE 계정 존재 (해당 계정으로 별도 세션 로그인 가능)",
      steps: [
        "대상 ACTIVE 계정 행의 [비활성] 클릭 → ConfirmModal 표시 확인",
        "확인 시 상태가 INACTIVE(비활성 배지)로 전환되고 행이 dimmed 처리되는지 확인",
        "해당 계정의 기존 세션이 다음 요청부터 401(세션 만료)로 차단되는지 확인",
        "해당 계정으로 신규 로그인 시도 시 로그인이 차단되는지 확인"
      ],
      expected: "비활성화는 ConfirmModal 확인 후 INACTIVE, 다음 요청부터 세션 만료 + 로그인 차단"
    },
    {
      id: "ADMIN-043",
      title: "[B] 계정 활성화 (즉시, 확인 불필요)",
      precondition: "관리자 로그인, INACTIVE 계정 존재",
      steps: [
        "대상 INACTIVE 계정 행의 [활성화] 클릭",
        "확인 모달 없이 즉시 ACTIVE(활성 배지)로 복귀하는지 확인",
        "해당 계정으로 다시 로그인 가능한지 확인"
      ],
      expected: "활성화는 즉시 처리(확인 없음), ACTIVE 복귀 후 로그인 재개"
    },

    // 비밀번호 변경 (모달)
    {
      id: "ADMIN-044",
      title: "[B] 비밀번호 변경 (모달, 관리자가 직접 지정, 8자 이상)",
      precondition: "관리자 로그인, 본인이 아닌 계정 존재",
      steps: [
        "대상 계정 행의 [비번] 클릭 → 새 비밀번호 입력 모달 표시 확인",
        "새 비밀번호를 7자 이하로 입력 → 8자 미만 검증 실패(400) 안내 확인",
        "새 비밀번호를 8자 이상으로 입력 후 저장 → 성공 처리 확인 (임시 비밀번호 자동 발급이 아님)",
        "대상 계정으로 새 비밀번호 로그인 가능 + 기존 비밀번호는 불가한지 확인"
      ],
      expected: "관리자가 새 비밀번호 직접 지정(8자 이상), 변경 후 새 비번으로 로그인 가능"
    },
    {
      id: "ADMIN-045",
      title: "[B] 비밀번호 변경은 기존 세션에 영향 없음",
      precondition: "대상 계정으로 이미 로그인된 별도 세션이 열려 있음 (관리자는 다른 세션)",
      steps: [
        "관리자가 대상 계정의 비밀번호를 새 값으로 변경",
        "대상 계정의 기존 세션에서 임의 요청 수행 → 401 없이 정상 동작하는지 확인 (세션 재확인은 STATUS/ROLE만 검증)",
        "본인(관리자) 비밀번호를 변경한 경우에도 본인 세션이 유지되는지 확인"
      ],
      expected: "비밀번호 변경은 세션을 폐기하지 않음 — 재로그인 강제 없음(본인/타인 동일)"
    },

    // 자기 보호 (서버 강제 400 + 버튼 게이팅)
    {
      id: "ADMIN-046",
      title: "[B] 자기 보호 — 본인 강등/비활성 불가 (버튼 disabled + 서버 400)",
      precondition: "관리자 본인 계정으로 로그인 (ACTIVE ADMIN 여러 명 존재해도 무관)",
      steps: [
        "본인 행의 [역할 변경](강등) 버튼과 [비활성] 버튼이 disabled + 이유 툴팁으로 노출되는지 확인",
        "devtools/콘솔에서 PATCH /api/v1/admin/users/{본인id}/role 로 USER 강등 직접 호출 → 400 확인",
        "PATCH /api/v1/admin/users/{본인id}/status 로 INACTIVE 비활성 직접 호출 → 400 확인"
      ],
      expected: "본인 강등/비활성은 버튼 비활성 + 서버 400으로 차단"
    },
    {
      id: "ADMIN-047",
      title: "[B] 자기 보호 — 본인 비밀번호 변경은 허용",
      precondition: "관리자 본인 계정으로 로그인",
      steps: [
        "본인 행의 [비번] 버튼이 활성 상태인지 확인",
        "본인 비밀번호를 8자 이상 새 값으로 변경 → 성공 처리 확인",
        "본인 세션이 유지되고(재로그인 강제 없음) 새 비밀번호로 재로그인도 가능한지 확인"
      ],
      expected: "본인 비밀번호 변경은 허용 — 세션 유지, 새 비번 로그인 가능"
    },

    // 마지막 ACTIVE ADMIN 보호 (서버 강제 400 + 버튼 게이팅)
    {
      id: "ADMIN-048",
      title: "[B] 마지막 ACTIVE ADMIN 보호 — 유일 ACTIVE ADMIN 강등/비활성 불가",
      precondition: "ACTIVE ADMIN이 본인 1명뿐 (다른 ADMIN 없거나 모두 INACTIVE)",
      steps: [
        "본인(유일 ACTIVE ADMIN) 행의 [역할 변경](강등)·[비활성] 버튼이 disabled + 이유 툴팁으로 노출되는지 확인",
        "PATCH /api/v1/admin/users/{본인id}/role 로 강등 직접 호출 → 400 확인",
        "PATCH /api/v1/admin/users/{본인id}/status 로 비활성 직접 호출 → 400 확인 (활성 관리자 0명 방지)"
      ],
      expected: "ACTIVE ADMIN이 1명뿐이면 그 계정 강등/비활성은 버튼 비활성 + 서버 400"
    },
    {
      id: "ADMIN-049",
      title: "[B] ACTIVE ADMIN 2명 이상이면 강등/비활성 가능",
      precondition: "ACTIVE ADMIN이 2명 이상 존재 (본인 + 타 ADMIN)",
      steps: [
        "본인이 아닌 ADMIN 계정의 [역할 변경](강등) → ConfirmModal 확인 → USER로 강등 성공 확인",
        "강등 후에도 ACTIVE ADMIN이 최소 1명 유지되는지 확인",
        "(참고) 강등으로 ACTIVE ADMIN이 1명이 되면, 남은 그 계정은 이후 마지막 ACTIVE ADMIN 보호(ADMIN-048)가 걸리는지 확인"
      ],
      expected: "ACTIVE ADMIN 2명 이상이면 강등/비활성 허용, 마지막 1명은 보호로 전환"
    },
    {
      id: "ADMIN-050",
      title: "[B] INACTIVE ADMIN 강등은 활성 관리자 카운트에 무영향",
      precondition: "ACTIVE ADMIN 1명(본인) + INACTIVE ADMIN 1명 존재",
      steps: [
        "INACTIVE 상태의 ADMIN 계정을 [역할 변경]으로 강등(USER) 시도 → 정상 처리되는지 확인",
        "이때 마지막 ACTIVE ADMIN 보호에 걸리지 않는지 확인 (보호 카운트는 ROLE=ADMIN AND STATUS=ACTIVE만 계산)"
      ],
      expected: "INACTIVE ADMIN 강등은 허용 — 활성 관리자 수 카운트에서 제외되므로 보호 미적용"
    },

    // 대상 없음
    {
      id: "ADMIN-051",
      title: "[B] 존재하지 않는 사용자 대상 액션 404",
      precondition: "관리자 로그인, 존재하지 않는(또는 이미 없는) userId 준비",
      steps: [
        "PATCH /api/v1/admin/users/{없는id}/role 직접 호출 → 404 확인",
        "PATCH /api/v1/admin/users/{없는id}/status 직접 호출 → 404 확인",
        "PATCH /api/v1/admin/users/{없는id}/password 직접 호출 → 404 확인"
      ],
      expected: "역할/상태/비밀번호 변경 대상이 없으면 404"
    },
    {
      id: "ADMIN-052",
      title: "[B] 사용자 관리 API는 조회조차 ADMIN 전용 (비-admin 403)",
      precondition: "일반 사용자 세션 (devtools/콘솔에서 fetch 호출 가능)",
      steps: [
        "GET /api/v1/admin/users 를 일반 사용자 세션으로 직접 호출 → 403 확인",
        "POST /api/v1/admin/users 로 계정 생성 직접 호출 → 403 확인",
        "PATCH /api/v1/admin/users/{id}/role 직접 호출 → 403 확인 (admin prefix가 ADMIN 강제)"
      ],
      expected: "사용자 관리 API는 목록 조회 포함 전부 비-admin 호출 시 403"
    },
    {
      id: "ADMIN-053",
      title: "[B] 사용자 계정 삭제 액션 없음 (비활성화로 대체)",
      precondition: "관리자 로그인, 사용자 목록/행 액션 확인",
      steps: [
        "사용자 행 액션에 [삭제] 버튼이 존재하지 않는지 확인 (역할/상태/비밀번호만)",
        "계정 정리는 [비활성]으로만 수행되는지 확인 (감사/이력 보존 목적)",
        "(참고) 스펙 관리(A)는 소프트 삭제를 제공하지만 사용자 계정은 삭제하지 않음을 대조 확인"
      ],
      expected: "사용자 계정은 삭제 액션 없음 — 비활성화로만 정리(하드/소프트 삭제 모두 범위 밖)"
    }
  ]
};

export default ADMIN_TESTS;
