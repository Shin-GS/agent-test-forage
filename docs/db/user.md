---
status: draft
last-updated: 2026-09-10
ref: docs/specs/common/auth.md, docs/specs/pages/settings.md
---

# 사용자 도메인 DB 설계

로그인/권한. (AI/실행 설정은 DB가 아니라 서버 설정 파일로만 관리 — 아래 참고)

## 테이블 개요

| 테이블 | 역할 |
|--------|------|
| `APP_USER` | 사용자 계정 (아이디/비밀번호/역할) |

> MySQL 예약어 회피를 위해 `USER` 대신 `APP_USER` 사용.

---

## APP_USER

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | |
| `USERNAME` | VARCHAR(50) | 로그인 아이디 (UNIQUE) |
| `PASSWORD` | VARCHAR(255) | bcrypt 해시 |
| `NAME` | VARCHAR(100) | 표시 이름 |
| `ROLE` | VARCHAR(20) | USER / ADMIN |
| `STATUS` | VARCHAR(20) | ACTIVE / INACTIVE (관리자가 비활성화) |
| `LAST_LOGIN_AT` | DATETIME NULL | 마지막 접속 |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**: `UQ_APP_USER_USERNAME` : UNIQUE (`USERNAME`)

- 역할: 일반 사용자 / 관리자 (auth.md)
- OTP/2단계 인증은 **추후** (컬럼도 지금 안 둠, 필요 시 추가)
- 비밀번호는 bcrypt (security-coding 규칙)

---

## AI/실행 설정은 DB에 저장하지 않는다

AI provider/모델, 대화 이력 전달 수, 스텝/전체 타임아웃 등은 **서버 설정 파일(.env / application.yml)로만**
관리한다(settings.md: 설정 페이지는 읽기 전용, ai-config.md: 설정 파일이 유일한 소스). 사용자·관리자
모두 런타임에 못 바꾸므로 **사용자별 설정 테이블(USER_SETTING)을 두지 않는다.**

- 근거: 비용·안정성에 직결되는 값이라 런타임 변경 경로를 두지 않고, 설정 소스를 파일로 단일화한다.
  값이 전역 고정이라 사용자마다 다르게 저장할 필요가 없다.
- 설정 페이지의 "설정 조회"는 이 파일 값을 읽어 표시할 뿐이며, DB를 거치지 않는다.
- 사용자별 개인화(예: 개인 이력 전달 수)를 나중에 정말 도입한다면 그때 `USER_SETTING`을 신설한다.

---

## 계정 생성 정책

- 셀프 회원가입은 없다. 계정은 **관리자가 관리자 페이지에서 직접 생성**한다 (아이디 + 비밀번호 + 역할).
  상세는 [관리자 페이지](../specs/pages/admin.md), 정책은 [로그인/권한](../specs/common/auth.md) 참조.
- 비밀번호는 항상 bcrypt 해시로 저장한다. 평문 저장 금지.

## 최초 관리자 계정 생성

관리자 페이지 자체에 접근하려면 먼저 관리자 계정이 있어야 하므로, **서버 기동 시 seed로 자동 생성**한다.

### 기본: 기동 시 seed 자동 생성

- 서버 기동 시 **ADMIN 역할 계정이 하나도 없으면**, `.env`의 초기 관리자 아이디/비밀번호로 관리자 계정을 자동 생성한다.
- **이미 ADMIN 계정이 있으면 스킵**한다(멱등). 매 기동마다 안전하게 실행된다.
- 비밀번호는 seed 시점에 애플리케이션과 동일한 인코더(BCrypt)로 해시하여 저장한다(평문 저장 금지).
- `.env` 키(확정):

  | 키 | 설명 |
  |----|------|
  | `ADMIN_SEED_USERNAME` | 초기 관리자 아이디 |
  | `ADMIN_SEED_PASSWORD` | 초기 관리자 평문 비밀번호 (seed 시 BCrypt 해시하여 저장) |

- **기본값을 두지 않는다.** 두 키 중 하나라도 비어 있으면 seed를 **스킵**한다(약한 기본 계정 자동 생성 방지). 관리자가 없는 상태로 시작하면 아래 수동 INSERT로 부트스트랩한다.
- 관련 `.env` 값은 [설정 파일](../specs/pages/settings.md) 관리 대상이며, 최초 로그인 후 관리자 페이지에서 계정을 관리한다. 최초 로그인 뒤 초기 비밀번호는 변경을 권장한다.

### 보조: MySQL 쿼리로 수동 생성

seed 대신 수동으로 만들 수도 있다(부트스트랩 UI 없음). `PASSWORD`는 bcrypt 해시값이어야 하므로, 원하는 평문 비밀번호를 먼저 bcrypt로 인코딩한 뒤 그 결과를 INSERT 한다.

#### 1) bcrypt 해시 생성

애플리케이션이 사용하는 것과 동일한 인코더로 해시를 만든다. 예시 방법:

- 서버 부팅 셸/테스트에서 Spring Security의 `BCryptPasswordEncoder().encode("원하는비밀번호")` 결과를 복사
- 또는 CLI: `htpasswd -bnBC 10 "" "원하는비밀번호" | tr -d ':\n' | sed 's/$2y/$2a/'` (결과 `$2a$...` 해시)

> bcrypt는 salt가 포함되므로 실행할 때마다 해시 문자열이 달라지는 것이 정상이다. 아래 예시의 해시는 자리표시자이며 그대로 쓰지 말고 직접 생성한 값으로 교체한다.

#### 2) INSERT (파라미터 바인딩 대신 관리자 수동 실행)

```sql
INSERT INTO APP_USER
  (USERNAME, PASSWORD, NAME, ROLE, STATUS, CREATED_AT, UPDATED_AT)
VALUES
  ('admin', '$2a$10$REPLACE_WITH_REAL_BCRYPT_HASH', '관리자', 'ADMIN', 'ACTIVE', NOW(), NOW());
```

- `USERNAME`은 UNIQUE 이므로 중복 시 실패한다.
- 생성 후에는 이 관리자 계정으로 로그인하여, 이후 계정은 관리자 페이지에서 생성한다.

---

## 확장 고려

- OTP: `IS_OTP_ENABLED`, `OTP_SECRET` 컬럼 추가로 확장 가능 (지금 안 둠)
- 관리자가 발급한 비밀번호의 최초 로그인 시 변경 강제가 필요해지면 `MUST_CHANGE_PASSWORD` 플래그를 APP_USER에 추가로 확장 가능 (지금 안 둠)
- 소셜 로그인 등은 별도 `USER_IDENTITY` 테이블로 확장 가능 (지금 불필요)
- 사용자별 설정 개인화가 필요해지면 `USER_SETTING` 신설 (현재는 전역 파일 설정이라 불필요)
