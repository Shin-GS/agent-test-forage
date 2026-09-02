---
status: draft
last-updated: 2026-09-02
---

# 데모 로컬 실행 가이드 (hosts / 서브도메인 / 쿠키)

데모 서버는 실제 배포 형태(한 회사의 여러 서비스가 **같은 상위 도메인의 서브도메인**)를 로컬에서 그대로 재현한다.
서브도메인으로 구성하면 same-site가 성립해, 크로스 사이트용 `SameSite=None; Secure`(HTTPS 필수) 없이
`SameSite=Lax` + HTTP로도 브라우저 쿠키 인증이 동작한다.

## 왜 서브도메인인가

- 이 플랫폼의 전제: 여러 서비스가 `*.company.com` 서브도메인으로 존재하고 에이전트도 같은 상위 도메인.
- 서브도메인끼리는 **same-site** → 쿠키가 `SameSite=Lax`로도 전송됨. HTTPS 강제 불필요.
- 서버별 서브도메인이 다르므로 세션 쿠키가 **서버별로 격리**된다(쿠키 섞임 방지).
- `localhost:포트`로만 나누면 모든 서버가 같은 `localhost`에 쿠키를 구워 섞이고, 실제 배포와도 달라진다.

## hosts 파일 수정 (사용자 직접, 관리자 권한)

Windows: `C:\Windows\System32\drivers\etc\hosts`
macOS/Linux: `/etc/hosts`

아래 라인을 추가한다(관리자 권한 필요):

```
127.0.0.1  app.test-forge.local      # FE (웹)
127.0.0.1  agent.test-forge.local    # 에이전트 서버 (packages/server)
127.0.0.1  shop.test-forge.local     # demo-shop
127.0.0.1  hosp.test-forge.local     # demo-hospital
127.0.0.1  bank.test-forge.local     # demo-banking
```

> 모두 `*.test-forge.local` 하위라 same-site가 성립한다.

## 포트 / 도메인 매핑

| 대상 | 로컬 도메인 | 포트 | 세션 쿠키명 |
|------|------------|------|------------|
| FE (웹) | app.test-forge.local | 5173 | — |
| 에이전트 서버 | agent.test-forge.local | 8080 | — |
| demo-shop | shop.test-forge.local | 9101 | `SHOP_SESSION` |
| demo-hospital | hosp.test-forge.local | 9102 | `HOSP_SESSION` |
| demo-banking | bank.test-forge.local | 9103 | `BANK_SESSION` |

- 접속 예: 데모 상품 목록 `http://shop.test-forge.local:9101/products`
- baseUrl(식별 키)도 이 서브도메인을 사용: `http://shop.test-forge.local:9101`

## 쿠키 정책 (환경별)

데모 서버는 세션 쿠키를 환경에 따라 다르게 발급한다.

| 환경 | SameSite | Secure | 이유 |
|------|----------|--------|------|
| 로컬 (서브도메인, HTTP) | `Lax` | 없음 | same-site라 Lax로 전송됨. HTTPS 불필요 |
| 프로덕션 (서브도메인, HTTPS) | `Lax` | `Secure` | same-site 유지 + HTTPS |
| (참고) 크로스 사이트 배포 | `None` | `Secure` | 서로 다른 도메인일 때만 필요 |

- 이 프로젝트의 주 시나리오는 **서브도메인(same-site)**이므로 기본은 `Lax`.
- `SameSite=None; Secure`는 서비스가 완전히 다른 도메인일 때만 쓰는 예외.

## 실행 순서

1. hosts 수정 (위)
2. 에이전트 서버 기동 (MySQL + `AI_API_KEY` 등, 포트 8080)
3. 데모 서버 기동 (demo-shop, 포트 9101) — 라이브러리가 기동 시 에이전트로 자동 등록
   - 데모 서버 yml: `ai-test-forge.server-url: http://agent.test-forge.local:8080`,
     `base-url: http://shop.test-forge.local:9101`, `register-token: <에이전트와 동일>`
4. 등록 확인: `GET http://agent.test-forge.local:8080/api/v1/specs`
5. 레시피 생성/실행 검증

## FE 호출 시 (추후 단계)

- FE는 레시피 API를 `fetch(url, { credentials: 'include' })`로 호출(쿠키 포함).
- 사용자는 사전에 데모 서버 로그인 페이지에서 로그인해 세션 쿠키를 보유해야 한다
  (인증 프로필의 `loginPageUrl`로 안내).
- 데모 서버 CORS는 라이브러리(`TestForgeCorsConfig`)가 `server-url` 기준으로 자동 허용 + `allowCredentials: true`.

## 초기 검증 단계 예외

CLI(curl 등)로만 검증하는 초기 단계에서는 hosts/쿠키 정책의 영향을 받지 않는다
(브라우저 same-site 판정은 CLI에 적용되지 않음). 따라서 hosts 없이 `localhost:9101`로도
등록/레시피 생성/실행 검증이 가능하다. hosts 구성은 **FE 브라우저로 실제 실행**하는 시점에 필요.
