/**
 * 관리자 페이지 — A(스펙 관리 + RBAC) + B(사용자 관리) 범위
 * Priority: medium
 *
 * 범위 구분:
 * - A (완료): RBAC(라우트 가드/nav 게이팅/관리 API 403), 스펙 목록/상세 표시,
 *   상태 관리(비활성/활성/삭제), 공용/관리자 목록 노출 분리, 빈 상태 — ADMIN-001~019.
 * - B (완료): 사용자 관리(/admin/users) — 진입/목록/검색/생성/역할·상태·비밀번호 변경,
 *   자기 보호 + 마지막 ACTIVE ADMIN 보호, 삭제 액션 없음 — ADMIN-030~053.
 * - C (이번 작업): 관리자 수동 서버/API 등록 — 서버 수동 CRUD, API 편집 페이지(파라미터/바디/헤더 정의),
 *   source(LIBRARY/MANUAL) 구분 + 재등록 upsert 보존/승격, 자동 API 읽기전용, 복제, 권한/이탈가드 — ADMIN-060~077.
 *   (서비스 설명 편집은 C에서 활성화 — ADMIN-063. 헤더 실행 주입/레시피 편집 자동필드는 2단계 제외)
 * - 별도 작업: (구)서비스 설명 편집 skip — ADMIN-020 (C에서 대체·활성화되나 위치 확보용으로 유지, 삭제 금지).
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
    },

    // ── C: 관리자 수동 서버/API 등록 (/admin/specs) — 이번 작업 ────────────
    // 라이브러리를 못 붙이는 외부 서버(정부/서드파티 API)를 관리자가 직접 등록·편집.
    // source(LIBRARY/MANUAL) 구분 + 재등록 upsert 보존. 헤더는 "정의만"(실행 주입은 2단계).
    // 원천 규칙(upsert 병합/source 전환)의 BE 검증은 spec.js와 연계, admin.js는 화면 조작·표시 관심사.

    // 서버 수동 등록 (Case 8)
    {
      id: "ADMIN-060",
      title: "[C] 서버 수동 등록 진입 + 정상 등록",
      precondition: "관리자 로그인, 미등록 baseUrl 준비",
      steps: [
        "/admin/specs 목록 상단 [+ 서버 등록] 클릭 → /admin/specs/new 폼 페이지 진입 확인",
        "서비스명 + baseUrl(https://) + 설명 + 도메인 입력",
        "[등록] 클릭 → 성공 후 해당 스펙 상세로 이동하는지 확인",
        "생성된 스펙이 목록에 ACTIVE로 노출되는지 확인"
      ],
      expected: "[+ 서버 등록]으로 폼 진입, 정상 등록 후 상세 이동 + 목록 반영"
    },
    {
      id: "ADMIN-061",
      title: "[C] 서버 등록 baseUrl 검증 (형식 + 중복 병합)",
      precondition: "관리자 로그인, 이미 등록된 baseUrl을 알고 있음",
      steps: [
        "baseUrl을 스킴 없이(예: example.go.kr) 입력하고 등록 시도 → 형식 검증 실패(인라인/400) 확인",
        "이미 등록된 baseUrl을 입력 → '기존 서버에 병합됨' 인라인 경고 표시 확인",
        "경고 상태로 진행 시 신규 스펙이 생기지 않고 기존 스펙 상세로 이동(병합)되는지 확인"
      ],
      expected: "baseUrl 형식 검증 + 중복 시 기존 스펙 병합(신규 미생성)"
    },
    {
      id: "ADMIN-062",
      title: "[C] 서버 인증 프로필 다중 입력 (0개 허용)",
      precondition: "관리자 로그인, 서버 등록 폼",
      steps: [
        "인증 프로필 [+ 추가]로 name + loginPageUrl 행 2개 입력 후 등록 → 상세에 프로필 2개 표시 확인",
        "행 [🗑 삭제]로 프로필 제거 가능 확인",
        "인증 프로필 0개로도 등록 성공하는지 확인(선택 항목)",
        "loginPageUrl에 잘못된 형식 입력 시 검증 안내 확인"
      ],
      expected: "인증 프로필은 다중 추가/삭제, 0개 허용, URL 형식 검증"
    },
    {
      id: "ADMIN-063",
      title: "[C] 서버 메타 편집 (baseUrl 읽기 전용)",
      precondition: "관리자 로그인, 수동 등록 스펙 상세",
      steps: [
        "서비스 설명 섹션 [✎ 편집] 클릭 → 메타 편집 폼 진입",
        "baseUrl 필드가 읽기 전용(readonly)인지 확인 (식별 키라 수정 불가)",
        "서비스명/설명/도메인 수정 후 저장 → 상세 복귀 + 반영 확인",
        "수정 후 adminEdited 상태가 되어 라이브러리 재등록이 메타를 덮어쓰지 않는지 확인(spec.js 연계)"
      ],
      expected: "메타 편집 가능, baseUrl 읽기 전용, 수정본은 라이브러리 재등록에 보존"
    },

    // API(엔드포인트) 수동 CRUD (Case 2 확장 / Case 9)
    {
      id: "ADMIN-064",
      title: "[C] 스펙 상세 API 목록 출처 배지 + 행 액션 표시",
      precondition: "관리자 로그인, 수동/자동 API가 섞인 스펙 상세",
      steps: [
        "API 엔드포인트 행에 출처 배지(🖉 수동 / LIBRARY) 표시 확인",
        "각 행에 [✎ 편집] [⧉ 복제] [🗑 삭제] 액션 버튼 표시 확인",
        "섹션 헤더에 [+ API 추가] 버튼 확인",
        "DEPRECATED API에 DEPRECATED 뱃지가 함께 표시되는지 확인"
      ],
      expected: "API 행에 출처 배지 + 편집/복제/삭제 액션, 섹션에 추가 버튼"
    },
    {
      id: "ADMIN-065",
      title: "[C] 엔드포인트 0개 빈 상태 CTA",
      precondition: "관리자 로그인, API가 하나도 없는(수동 등록 직후) 스펙 상세",
      steps: [
        "API 엔드포인트 섹션에 '아직 API가 없어요' 빈 상태 표시 확인",
        "빈 상태에 [+ API 추가] CTA 버튼이 강조되는지 확인",
        "CTA 클릭 시 API 편집 페이지로 진입하는지 확인"
      ],
      expected: "엔드포인트 0개면 빈 상태 + [+ API 추가] CTA"
    },
    {
      id: "ADMIN-066",
      title: "[C] API 추가 정상 (method/path/설명 + 파라미터/바디)",
      precondition: "관리자 로그인, 스펙 상세에서 [+ API 추가]",
      steps: [
        "API 편집 페이지 진입 → 메서드(POST) + 경로(/orders) + 설명 입력",
        "② 경로 파라미터 · ③ 쿼리 파라미터 · ④ 요청 바디 필드를 테이블로 추가",
        "[저장] → 성공 후 스펙 상세로 복귀 + 목록에 MANUAL 배지로 신규 API 반영 확인",
        "저장된 값이 operationJson(OpenAPI Operation) 형식으로 보존되는지 확인(spec.js 연계)"
      ],
      expected: "파라미터/바디 구조화 입력으로 API 추가, MANUAL로 저장 + operationJson 직렬화"
    },
    {
      id: "ADMIN-067",
      title: "[C] 경로 {변수} 자동 파라미터 제안 + GET 바디 비활성",
      precondition: "관리자 로그인, API 편집 페이지",
      steps: [
        "경로에 /users/{id} 입력 → 경로 파라미터에 id 행이 자동 제안되는지 확인",
        "메서드를 GET/DELETE로 선택 → ④ 요청 바디 섹션이 비활성/숨김되는지 확인",
        "GET에서 입력했던 바디 필드는 보존되되(다시 POST로 바꾸면 복원) 저장 시 GET이면 바디 미포함인지 확인"
      ],
      expected: "경로 변수 자동 제안, GET/DELETE는 바디 비활성(입력값 보존/저장 제외)"
    },
    {
      id: "ADMIN-068",
      title: "[C] (method, path) 중복 저장 차단",
      precondition: "관리자 로그인, 같은 스펙에 POST /orders가 이미 존재",
      steps: [
        "API 추가에서 동일 method+path(POST /orders) 입력 후 저장 시도",
        "저장 전 인라인 경고 또는 서버 400으로 중복이 차단되는지 확인",
        "path를 유일하게 바꾸면 저장 성공하는지 확인"
      ],
      expected: "같은 스펙 내 (method,path) 중복은 인라인 경고 + 서버 400으로 차단"
    },
    {
      id: "ADMIN-069",
      title: "[C] 요청 헤더 '정의만' accordion + 실행 미반영 안내",
      precondition: "관리자 로그인, API 편집 페이지",
      steps: [
        "⑤ 요청 헤더 accordion을 펼침",
        "'🔒 정의만 저장됩니다. 실제 요청 주입은 추후 지원' 안내가 강조 표시되는지 확인",
        "헤더 이름/필수/설명 입력 후 저장 → operationJson parameters[in:header]로 저장되는지 확인",
        "(참고) 실행 시 이 헤더 값이 실제 요청에 주입되지 않음은 2단계 — 이번 범위 밖"
      ],
      expected: "요청 헤더는 정의 저장까지만, '실행 주입 추후' 안내 명시"
    },
    {
      id: "ADMIN-070",
      title: "[C] 자동(LIBRARY) API는 스키마 읽기 전용, 메타만 수정",
      precondition: "관리자 로그인, LIBRARY 출처 API 편집 진입",
      steps: [
        "①경로·②③파라미터·④바디·⑤⑥헤더/응답 스키마 섹션이 읽기 전용인지 확인",
        "실행 전 확인(isConfirmRequired)·AI 목록 제외(isExcluded) 메타 토글만 수정 가능한지 확인",
        "상단에 '스키마는 라이브러리 관리, 메타만 수정 가능' 안내 확인",
        "DEPRECATED(사라진 LIBRARY) API 편집도 동일하게 읽기 전용 + 메타만인지 확인"
      ],
      expected: "자동/DEPRECATED API는 스키마 읽기전용, 메타(확인/제외)만 수정"
    },
    {
      id: "ADMIN-071",
      title: "[C] API 복제 (출처 무관 MANUAL 사본)",
      precondition: "관리자 로그인, 스펙 상세의 API 행",
      steps: [
        "자동(LIBRARY) API 행 [⧉ 복제] → 사본이 생성되고 편집 페이지로 이동하는지 확인",
        "사본의 출처가 항상 MANUAL인지 확인 (자동 API를 복제해도 사본은 수동)",
        "(method,path) 유니크 충돌 방지를 위해 path 변경을 유도받는지 확인",
        "복제 실패(네트워크/5xx) 시 토스트 안내 + 목록 유지 확인"
      ],
      expected: "복제본은 출처 무관 항상 MANUAL, 편집 유도, 실패 시 토스트"
    },
    {
      id: "ADMIN-072",
      title: "[C] API 삭제 — 출처·참조 여부로 분기 (하드 삭제 vs DEPRECATED)",
      precondition: "관리자 로그인, (a)레시피가 참조하는 MANUAL API, (b)참조 없는 MANUAL API, (c)LIBRARY API가 각각 있는 스펙 상세",
      steps: [
        "참조 없는 MANUAL API 행 [🗑 삭제] → ConfirmModal 확인 → 목록에서 완전히 사라지는지 확인(하드 삭제)",
        "방금 삭제한 것과 같은 method+path로 [+ API 추가] 시 재등록 성공하는지 확인(유니크 충돌 없음)",
        "레시피가 참조하는 MANUAL API 삭제 → 하드 삭제가 아니라 DEPRECATED로 보존되어 endpointId(PK)가 유지되는지 확인",
        "LIBRARY API 삭제 → DEPRECATED로 보존되는지 확인(재등록으로 부활 가능)",
        "DEPRECATED로 보존된 API를 참조하던 레시피가 실행 전 유효성 검증에서 경고되는지 확인(즉시 깨지지 않음)"
      ],
      expected: "MANUAL+참조없음=하드삭제(재추가 가능), MANUAL+참조있음·LIBRARY=DEPRECATED 보존, 참조 레시피는 실행 전 경고"
    },

    // source 보존 (재등록 upsert — spec.js 연계, 화면 관점)
    {
      id: "ADMIN-073",
      title: "[C] 수동 API는 라이브러리 재등록에도 보존",
      precondition: "관리자 로그인, MANUAL API가 있는 스펙 + 라이브러리 재등록 발생",
      steps: [
        "스펙에 수동으로 추가한 API(라이브러리 스펙에는 없는 method+path)가 있는 상태",
        "라이브러리가 같은 baseUrl로 재등록(그 수동 API는 미포함)",
        "재등록 후에도 수동 API가 DEPRECATED로 강등되지 않고 ACTIVE로 보존되는지 확인",
        "(대조) 라이브러리 소관 API 중 사라진 것은 DEPRECATED 되는지 확인"
      ],
      expected: "수동 전용 API는 재등록에도 보존, 라이브러리 사라진 API만 DEPRECATED (원천: SPEC-011/013)"
    },
    {
      id: "ADMIN-074",
      title: "[C] 수동 API와 겹치는 라이브러리 등록 시 자동 승격",
      precondition: "관리자 로그인, 수동 등록한 API와 동일 method+path를 라이브러리가 등록",
      steps: [
        "수동으로 POST /orders를 등록(MANUAL)",
        "라이브러리가 같은 baseUrl로 POST /orders 포함해 재등록",
        "해당 API가 라이브러리 값으로 갱신되고 출처 배지가 LIBRARY로 전환되는지 확인",
        "유니크 키(specId,method,path)상 두 행이 공존하지 않고 같은 행이 upsert되는지 확인"
      ],
      expected: "겹치는 API는 라이브러리 값으로 덮어쓰고 source가 LIBRARY로 승격(단일 행 upsert) (원천: SPEC-012)"
    },

    // 권한 / 이탈 가드
    {
      id: "ADMIN-075",
      title: "[C] 수동 등록/편집 API는 비-admin 403 (세션 RBAC)",
      precondition: "일반 사용자 세션 (devtools/콘솔 fetch)",
      steps: [
        "POST /api/v1/specs/manual 를 일반 사용자 세션으로 직접 호출 → 403 확인",
        "POST /api/v1/specs/{id}/endpoints 직접 호출 → 403 확인",
        "PATCH/DELETE 엔드포인트 API 직접 호출 → 403 확인",
        "(자동 등록 POST /specs는 X-TestForge-Token, 수동은 ADMIN 세션으로 보호 — 인증 경로가 다름)"
      ],
      expected: "수동 등록/편집 API는 전부 ADMIN 세션 강제(비-admin 403)"
    },
    {
      id: "ADMIN-076",
      title: "[C] 편집 페이지 저장 안 한 변경 이탈 가드",
      precondition: "관리자 로그인, API 편집 페이지에서 값 입력 중",
      steps: [
        "필드를 수정한 뒤 저장하지 않고 [← 스펙으로]/뒤로가기/새로고침 시도",
        "'저장하지 않은 변경이 있습니다' 경고가 뜨는지 확인",
        "취소하면 편집 상태 유지, 확인하면 이탈되는지 확인",
        "서버 등록 폼(Case 8)에서도 동일하게 이탈 가드가 동작하는지 확인"
      ],
      expected: "저장 전 이탈 시 경고, 확인해야 이탈(서버 등록/ API 편집 공통)"
    },
    {
      id: "ADMIN-077",
      title: "[C] 딥링크/새로고침 진입 로딩 + 대상 없음 처리",
      precondition: "관리자 로그인, 존재하지 않는 endpointId/specId 준비",
      steps: [
        "API 편집 URL(/admin/specs/:id/endpoints/:endpointId/edit)로 직접 진입 → 로딩 표시 후 폼 렌더 확인",
        "존재하지 않는 endpointId로 진입 → 전용 빈 상태 + [스펙으로] 안내 확인 (토스트 아님)",
        "삭제/없는 specId로 서버 편집 진입 → 빈 상태 + [목록으로] 확인",
        "비-admin이 편집 URL 직접 진입 → 라우트 가드가 '/'로 리다이렉트 확인"
      ],
      expected: "딥링크 진입 로딩, 대상 없음은 전용 빈 상태, 비-admin은 리다이렉트"
    }
  ]
};

export default ADMIN_TESTS;
