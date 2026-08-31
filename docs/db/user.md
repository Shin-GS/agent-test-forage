---
status: draft
last-updated: 2026-08-28
ref: docs/specs/common/auth.md, docs/specs/pages/settings.md
---

# 사용자 도메인 DB 설계

로그인/권한/설정.

## 테이블 개요

| 테이블 | 역할 |
|--------|------|
| `APP_USER` | 사용자 계정 (아이디/비밀번호/역할) |
| `USER_SETTING` | 사용자별 설정 (AI provider, 타임아웃 등) |

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

## USER_SETTING

사용자별 설정 (settings.md). 사용자 1명 : 설정 1행.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `ID` | BIGINT PK | |
| `USER_ID` | BIGINT FK UNIQUE | 소속 사용자 (1:1) |
| `AI_PROVIDER` | VARCHAR(20) | OpenAI / Claude / OpenRouter |
| `AI_MODEL` | VARCHAR(50) | 사용 모델 |
| `HISTORY_CONTEXT_COUNT` | INT | 대화 이력 전달 건수 (기본 15) |
| `STEP_TIMEOUT_SEC` | INT | 스텝 타임아웃 (기본 30) |
| `RECIPE_TIMEOUT_SEC` | INT | 레시피 전체 타임아웃 (기본 300) |
| `CREATED_AT` / `UPDATED_AT` | DATETIME | audit |

**인덱스**: `UQ_USER_SETTING_USER` : UNIQUE (`USER_ID`)

- 미설정 시 시스템 기본값 사용 (행 없으면 기본값)

---

## 확장 고려

- OTP: `IS_OTP_ENABLED`, `OTP_SECRET` 컬럼 추가로 확장 가능 (지금 안 둠)
- 사용자 초대(admin.md): 임시 비밀번호 발급은 APP_USER에 `MUST_CHANGE_PASSWORD` 플래그 추가로 확장 가능
- 소셜 로그인 등은 별도 `USER_IDENTITY` 테이블로 확장 가능 (지금 불필요)
