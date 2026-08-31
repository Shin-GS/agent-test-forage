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
- 메인 서버에 POST (name, baseUrl, specJson, specHash, authProfiles, **serviceInfo**, jira)
- SHA-256 해시로 변경 감지 (heartbeat 시 해시만 전송, 불일치 시 재전송)
- 등록은 앱 기동을 블로킹하지 않음. 실패 시 최대 3회 재시도 후 로그만 남기고 계속 진행 (외부 서버는 정상 기동)
- 등록 완료 시 콘솔에 배너 로그 출력 (예: `AI Test Forge: 스펙 등록 완료 — demo-shop (42개 API)`)

### 등록 계약 버저닝

여러 언어/버전의 라이브러리가 하나의 ai-test-forge 서버로 등록하므로, 등록 요청 body에 버전을 명시한다.

- `schemaVersion`: 등록 계약(body 구조) 버전. **서버가 이 값으로 파싱을 분기**하여 여러 버전 요청을 흡수한다 (호환 관리 주체 = 서버).
- `client`: 라이브러리 언어/버전 (진단용).
- 라이브러리는 자기 schemaVersion을 명시해서 전송.
- 계약이 하위호환 불가하게 바뀌면 API 경로를 `/api/v2/`로 승격.
- 상세 계약: [db/spec.md](../../db/spec.md)

## 서비스 식별 (name / baseUrl)

- `name`: 사용자에게 보이는 **서비스 이름**. 채팅 매칭/사이드바 태그/AI 컨텍스트에 노출 (중복 허용)
- `baseUrl`: 시스템 **식별 키** (고유). 서버 도메인
- 예: `demo-shop` — 사용자가 "demo-shop에서 회원가입 해줘"로 지칭

> 환경(dev/stg/prod)은 ai-test-forge 인스턴스를 환경별로 따로 배포하여 구분한다. 스펙에 environment 필드를 두지 않는다.

## 서비스 설명 (serviceInfo)

각 서버가 "무엇을 하는 서비스인지" 설명하는 메타 정보. AI가 서비스 선택/의도 매칭 시 컨텍스트로 활용한다.

| 항목 | 설명 | 필수 |
|------|------|------|
| `description` | 한 줄 요약 (AI 서비스 매칭 컨텍스트) | 권장 |
| `domain` | 도메인 영역 (예: 커머스, 결제, 회원) | 선택 |
| `capabilities` | 이 서비스로 할 수 있는 작업 키워드 (복수) | 선택 |
| `notes` | 주의사항 (예: 스테이징 전용, rate limit) | 선택 |

```yaml
# 외부 서버 application.yml
ai-test-forge:
  name: "demo-shop"
  service:
    description: "온라인 쇼핑몰 API"
    domain: "커머스"
    capabilities: ["회원가입", "상품등록", "주문"]
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

외부 서버 컨트롤러에 붙여서 API별 제어. 프로토타입은 **안전 관련 2종**만 제공한다.

| 어노테이션 | 역할 |
|-----------|------|
| `@TestForgeExclude` | API 목록에서 제외 (내부/관리용 API를 AI가 못 보게) |
| `@TestForgeConfirm` | 실행 전 사용자 확인 필요 (결제 등 되돌릴 수 없는 API) |

- OpenAPI 스펙에 `x-test-forge-*` 확장 필드로 변환되어 전송됨
- springdoc(OperationCustomizer)이 클래스패스에 있을 때만 동작 (없으면 자동 스킵)

```java
@TestForgeConfirm(message = "실제 결제가 발생합니다")
@PostMapping("/orders/{id}/pay")
public PaymentResponse pay(...) { ... }

@TestForgeExclude
@DeleteMapping("/admin/users/{id}")   // 관리자용, 테스트에서 제외
public void deleteUser(...) { ... }
```

> 추후 확장 후보: `@TestForgeHint`, `@TestForgeBlock`, `@TestForgeGroup`, `@TestForgeReadOnly`. 필요 시 추가 (어노테이션은 추가는 안전, 제거는 breaking이므로 최소로 시작).

## 인증 프로필

- 외부 서버 설정에서 `login-page-url` 정의
- 스펙 등록 시 함께 전송/저장
- 레시피 실행 중 401/403 발생 시 해당 URL을 사용자에게 제공

> 표기 규칙: **yml 설정 키는 kebab-case**(`login-page-url`, `project-key`, `server-url`), Spring이 relaxed binding으로 프로퍼티에 매핑한다. 등록 **요청 body의 JSON 필드**는 camelCase(`loginPageUrl`, `serviceInfo`, `authProfiles`) — 서로 다른 레이어다.

```yaml
# 외부 서버 application.yml
ai-test-forge:
  auth:
    profiles:
      - name: "관리자"
        login-page-url: "https://example.com/admin/login"
      - name: "일반 사용자"
        login-page-url: "https://example.com/login"
```

## CORS 자동 허용

레시피 실행 시 **FE 브라우저가 외부 서버 API를 직접 호출**하므로, 외부 서버가 ai-test-forge 도메인을 CORS 허용해야 한다. client-spring 라이브러리가 이를 자동 설정한다.

```yaml
# 외부 서버 application.yml
ai-test-forge:
  server-url: "https://ai-test-forge.example.com"  # CORS allowed-origin에 자동 등록
```

라이브러리 자동 설정 내용:
- `allowedOrigins`: 위 `server-url`
- `allowCredentials: true` (쿠키 세션 전달용)
- OPTIONS 프리플라이트 허용

### 전제조건 (외부 서버 소관)

FE가 쿠키 인증으로 API를 호출하려면 아래 조건이 필요하다. 라이브러리로 강제할 수 없고 외부 서버가 충족해야 한다.

| 조건 | 이유 |
|------|------|
| 세션 쿠키 `SameSite=None; Secure` | 크로스 도메인에서 쿠키 전송되려면 필수 |
| HTTPS | `SameSite=None`은 `Secure` 필수 |
| FE `fetch(credentials: 'include')` | 쿠키 포함 요청 (FE 구현 시 처리) |

- 위 조건 미충족 시 401 로그인 플로우가 동작하지 않음
- 로컬 개발 환경은 https 또는 예외 처리 별도 안내

## Jira 연결 (정보 조회용)

`investigate` 툴의 Jira 커넥터가 조회할 프로젝트를 서비스에 연결한다. 상세: [정보 조회 루프](../chat/scenarios/investigation.md)

```yaml
# 외부 서버 application.yml
ai-test-forge:
  jira:
    project-key: "SHOP"   # 이 서비스와 연관된 Jira 프로젝트 키
```

| 항목 | 위치 | 이유 |
|------|------|------|
| Jira 프로젝트 키 (`project-key`) | 서비스별 (yml / 관리자 수정) | 서비스마다 다름 |
| Jira 인스턴스 baseUrl + API 토큰 | **ai-test-forge 서버 환경변수 (시크릿)** | 민감정보. 조직당 보통 1개 인스턴스 |

- **호출 주체는 ai-test-forge 서버(BE)**. FE가 아님 (토큰이 시크릿이므로)
- projectKey도 서비스 설명과 동일하게 관리자 우선 정책 적용

## 스펙 상태

| 상태 | 설명 | 전이 |
|------|------|------|
| ACTIVE | 정상 | heartbeat 유지 |
| STALE | 5분 이상 heartbeat 없음 | heartbeat 재수신 시 ACTIVE 복귀 |
| INACTIVE | 관리자가 수동 비활성 | 관리자만 ACTIVE 복귀 (자동 삭제 제외) |
| (삭제) | 24시간 이상 heartbeat 없음 → 소프트 삭제 | INACTIVE는 대상 아님. 초기 버전 기준(공격적 삭제 방지) |

> 프로토타입에서는 스펙 파싱을 **동기 처리**한다. 대형 스펙(5MB+) 비동기 파싱(REGISTERING 상태)은 추후 필요 시 도입.

## 재등록 정합성

서버가 여러 번 실행/재배포되면 매번 등록/heartbeat이 온다. 데이터 정합성 처리:

- 식별: `baseUrl`로 기존 스펙을 찾음 (heartbeat의 specHash가 같으면 아무 것도 안 함)
- specHash 불일치 → 전체 재전송 → 아래 병합

| 대상 | 재등록 처리 |
|------|------------|
| 서비스 메타 (설명/Jira) | 관리자 수정본 우선 보존, yml 변경은 감지만 |
| API 엔드포인트 | `method + path` 키로 upsert |
| └ 기존 API | 스키마 갱신 (**내부 ID 유지** → 레시피 참조 보존) |
| └ 신규 API | 추가 |
| └ 스펙에서 사라진 API | **비활성(DEPRECATED) 마킹** (삭제 X → 레시피 보호) |
| 인증 프로필 | 전체 재구성 |

- DEPRECATED API를 참조하는 레시피는 유효성 검증에서 경고 (즉시 실행 실패 방지)
- 경로 변경(`/v1/users` → `/v2/users`)은 삭제+신규로 취급 (구 API는 DEPRECATED)
- 상세 스키마: [db/spec.md](../../db/spec.md)

## 등록 보안

- 아무 서버나 등록하지 못하도록, 등록/heartbeat 요청에 **공유 시크릿 토큰** 필요
- 헤더 `X-TestForge-Token` — 외부 서버 yml에 설정, ai-test-forge 서버 환경변수와 대조
- 불일치 시 `401`

```yaml
# 외부 서버 application.yml
ai-test-forge:
  register-token: ${TESTFORGE_TOKEN}   # 시크릿, 환경변수로 주입
```
