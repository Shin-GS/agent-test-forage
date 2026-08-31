---
status: confirmed
last-updated: 2026-08-27
---

# 스펙 등록 방식

## 개요

외부 서버가 자신의 API 스펙을 ai-test-forge에 자동 등록하는 구조.

## 등록 흐름

```
외부 서버 (client-spring 라이브러리 포함)
    │
    │ 1. 앱 기동 시 자동 등록 (OpenAPI JSON push)
    │ 2. 30초마다 heartbeat (변경 시에만 재전송)
    │
    ▼
ai-test-forge 서버 POST /api/v1/specs/register
    │
    ▼
DB 저장 → 레시피 작성 시 API 목록으로 활용
```

## 등록 방식

- 클라이언트 라이브러리를 외부 서버에 의존성 추가
- 앱 기동 시 자동으로 `/v3/api-docs`에서 OpenAPI JSON 수집
- 메인 서버에 POST (name, environment, baseUrl, specJson, specHash, authProfiles, **serviceInfo**)
- SHA-256 해시로 변경 감지 (heartbeat 시 해시만 전송, 불일치 시 재전송)

## 서비스 설명 (serviceInfo)

각 서버가 "무엇을 하는 서비스인지" 설명하는 메타 정보. AI가 서비스 선택/의도 매칭 시 컨텍스트로 활용한다.

| 항목 | 설명 | 필수 |
|------|------|------|
| `description` | 한 줄 요약 (AI 서비스 매칭 컨텍스트) | 권장 |
| `domain` | 도메인 영역 (예: 채용, 결제, 회원) | 선택 |
| `capabilities` | 이 서비스로 할 수 있는 작업 키워드 (복수) | 선택 |
| `notes` | 주의사항 (예: 스테이징 전용, rate limit) | 선택 |

```yaml
# 외부 서버 application.yml
ai-test-forge:
  service:
    description: "채용 공고 등록 및 지원자 관리 서비스"
    domain: "채용"
    capabilities: ["회원가입", "공고등록", "입사지원"]
    notes: "스테이징 환경, 실 결제 없음"
```

### 입력 경로 (2가지)

| 경로 | 설명 |
|------|------|
| yml 등록 | 개발자가 `application.yml`에 작성 → 라이브러리가 등록 시 자동 전송 |
| 관리자 수정 | 관리자가 스펙 관리 페이지에서 직접 작성/수정 |

### 우선순위 정책

- **관리자 수정이 우선**한다. yml 재등록(heartbeat 변경 감지) 시 관리자가 수정한 값을 덮어쓰지 않는다.
- yml 값이 변경되면 "변경 감지" 표시만 하고, 관리자가 반영 여부를 선택한다 (강제 덮어쓰기 없음).
- 관리자가 한 번도 수정하지 않은 서비스는 yml 값을 그대로 사용.

### AI 자동 초안 (선택)

설명이 비어 있으면 스펙 등록 시 API 목록을 기반으로 AI(fast 모델)가 초안을 제안할 수 있다. 관리자가 확인/수정 후 저장. 빈 설명 방지 목적.

### AI 컨텍스트 반영

서비스 미지정 대화에서 AI에게 전달하는 "서비스 목록(이름 + 한 줄 설명)"의 설명이 이 `description`으로 채워진다. (참조: [ai-config.md](../common/ai-config.md) 레시피 목록 필터링)

## 커스텀 어노테이션

외부 서버 컨트롤러에 붙여서 API별 제어:

| 어노테이션 | 역할 |
|-----------|------|
| `@TestForgeExclude` | API 목록에서 제외 |
| `@TestForgeBlock` | AI 호출 차단 |
| `@TestForgeConfirm` | 실행 전 확인 필요 |
| `@TestForgeHint` | AI에게 전달할 힌트 문자열 |
| `@TestForgeGroup` | 그룹 분류 |

## 인증 프로필

- 외부 서버 설정에서 `loginPageUrl` 정의
- 스펙 등록 시 함께 전송/저장
- 레시피 실행 중 401/403 발생 시 해당 URL을 사용자에게 제공

```yaml
# 외부 서버 application.yml
ai-test-forge:
  auth:
    profiles:
      - name: "관리자"
        loginPageUrl: "https://example.com/admin/login"
      - name: "일반 사용자"
        loginPageUrl: "https://example.com/login"
```

## 스펙 상태

| 상태 | 설명 |
|------|------|
| REGISTERING | 비동기 파싱 중 (5MB+ 스펙) |
| ACTIVE | 정상 |
| STALE | 5분 이상 heartbeat 없음 |
| (삭제) | 30분 이상 heartbeat 없음 → 자동 삭제 |
