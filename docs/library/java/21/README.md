# AI Test Forge 클라이언트 — Java 21

Spring Boot 서버에 탑재하여 API 스펙을 ai-test-forge에 자동 등록하는 라이브러리.

## 요구사항

- Java 21+
- Spring Boot 3.x (spring-boot-autoconfigure, spring-web)
- OpenAPI 문서 노출 (springdoc, `/v3/api-docs`) — 어노테이션 기능 사용 시 필수

## 설치

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.testforge:testforge-client-java21:0.0.1-SNAPSHOT")
}
```

## 설정

```yaml
# application.yml
ai-test-forge:
  enabled: true                                # 기본 true, false로 비활성화
  server-url: https://ai-test-forge.example.com  # 등록 대상 + CORS 허용 대상
  register-token: ${TESTFORGE_TOKEN}           # 등록 보안 토큰 (시크릿)
  name: "demo-shop"                            # 사용자에게 보이는 서비스 이름
  base-url: https://shop-api.example.com       # 이 서버 도메인 (식별 키)
  docs-url: /v3/api-docs                        # OpenAPI 경로 (기본값)
  service:
    description: "온라인 쇼핑몰 API"
    domain: "커머스"
    capabilities: ["회원가입", "상품등록", "주문"]
    notes: "스테이징 환경"
  jira:
    project-key: "SHOP"                         # 정보 조회용 Jira 프로젝트 (선택)
  auth:
    profiles:
      - name: "일반"
        login-page-url: "https://shop.example.com/login"
```

- 설정 후 앱을 기동하면 자동 등록된다 (별도 코드 불필요). **등록은 기동당 1회.**
- 재배포/재기동 시 다시 1회 등록되어 최신 스펙으로 갱신된다. 주기적 heartbeat는 없다.
- 등록은 앱 기동을 막지 않는다. 실패 시 3회 재시도 후 로그만 남기고 계속 진행.
- 등록 완료 시 콘솔에 배너가 출력된다.

## 어노테이션 (API별 제어)

컨트롤러 메서드에 부착. springdoc이 있어야 동작한다.

### @TestForgeExclude

API를 ai-test-forge 목록에서 제외 (내부/관리용).

```java
@TestForgeExclude(reason = "관리자 전용")  // reason 선택
@DeleteMapping("/admin/users/{id}")
public void deleteUser(...) { }
```

- 메서드 또는 컨트롤러 클래스에 부착 가능 (클래스에 붙이면 전체 제외)
- `reason` 생략 가능

### @TestForgeConfirm

실행 전 사용자 확인 요구 (결제 등 되돌릴 수 없는 API).

```java
@TestForgeConfirm(message = "실제 결제가 발생합니다")  // message 선택
@PostMapping("/orders/{id}/pay")
public PaymentResponse pay(...) { }
```

- `message` 생략 시 기본 문구 사용 ("이 작업은 되돌릴 수 없습니다. 실행하시겠습니까?")

## 전제조건 (CORS 쿠키 인증)

레시피 실행 시 FE 브라우저가 이 서버 API를 직접 호출한다. 쿠키 인증이 동작하려면:

| 배포 형태 | 세션 쿠키 | 이유 |
|-----------|-----------|------|
| 같은 상위 도메인의 서브도메인 (권장) | `SameSite=Lax` | same-site라 HTTPS 없이도 쿠키 전송 |
| 서로 다른 도메인 (크로스 사이트) | `SameSite=None; Secure` + HTTPS | 크로스 사이트 쿠키 전송 요건 |

이 조건은 외부 서버가 충족해야 하며 라이브러리로 강제하지 않는다.

## 동작 안 할 때 확인

| 증상 | 확인 |
|------|------|
| 등록 안 됨 | `base-url`, `server-url`, `register-token` 설정 확인. 로그의 재시도 메시지 |
| 어노테이션 무시됨 | springdoc이 클래스패스에 있는지 (`/v3/api-docs` 접근 가능 여부) |
| CORS 에러 | `server-url`이 정확한지, 쿠키 SameSite/Secure 조건 |

## 빌드 (개발자용)

```bash
cd packages/library/java/21
./gradlew assemble          # jar 생성
# 참고: 로컬에 Java 21이 없으면 test 태스크가 실패할 수 있음 (컴파일은 release=21로 정상)
```

기획: [스펙 등록 방식](../../../specs/spec/registration.md)
