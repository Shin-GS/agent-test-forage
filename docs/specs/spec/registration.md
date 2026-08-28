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
- 메인 서버에 POST (name, environment, baseUrl, specJson, specHash, authProfiles)
- SHA-256 해시로 변경 감지 (heartbeat 시 해시만 전송, 불일치 시 재전송)

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
