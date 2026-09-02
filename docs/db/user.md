---
status: draft
last-updated: 2026-09-02
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

## 확장 고려

- OTP: `IS_OTP_ENABLED`, `OTP_SECRET` 컬럼 추가로 확장 가능 (지금 안 둠)
- 사용자 초대(admin.md): 임시 비밀번호 발급은 APP_USER에 `MUST_CHANGE_PASSWORD` 플래그 추가로 확장 가능
- 소셜 로그인 등은 별도 `USER_IDENTITY` 테이블로 확장 가능 (지금 불필요)
- 사용자별 설정 개인화가 필요해지면 `USER_SETTING` 신설 (현재는 전역 파일 설정이라 불필요)
