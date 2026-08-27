---
sourceId: security-coding
sourceVersion: "1.0"
sourceUpdatedAt: 2026-07-01
inclusion: always
---

# 보안 중시 코딩 규칙

## 필수 (모든 코드)

- [ ] 사용자 입력은 서버에서 검증 (클라이언트 검증은 UX용, 신뢰하지 않음)
- [ ] SQL은 파라미터 바인딩 사용 (문자열 조합/연결 금지)
- [ ] 시크릿은 환경변수로 관리 (코드 내 하드코딩 금지)
- [ ] 에러 응답에 스택 트레이스/내부 구현 미포함 (프로덕션)
- [ ] 로그에 민감 정보(토큰, 비밀번호, 개인정보) 미포함
- [ ] 외부 입력을 명령어에 직접 삽입하지 않음 (command injection 방지)

## API 엔드포인트 추가 시

- [ ] 인증 필수 여부 확인 (public/private 명시)
- [ ] 리소스 소유자 체크 — 수평 권한 (내 데이터만 접근 가능한가?)
- [ ] 역할 기반 접근 제어 — 수직 권한 (admin만 가능한 기능인가?)
- [ ] Rate limiting 고려 (무한 호출 방어)
- [ ] 입력 크기 제한 (payload size limit)
- [ ] Content-Type 검증

## 파일 처리 시

- [ ] 경로 traversal 방지 (`../` 차단, 허용된 디렉토리 내로 제한)
- [ ] 파일 타입 검증 (확장자 + MIME type + magic bytes)
- [ ] 업로드 크기 제한
- [ ] 파일명 sanitize (특수문자, 유니코드 정규화)

## 인증/세션 관련

- [ ] 비밀번호는 bcrypt/argon2로 해싱 (SHA/MD5 금지)
- [ ] JWT 토큰에 민감 정보 미포함 (payload는 누구나 디코딩 가능)
- [ ] 세션 타임아웃 설정
- [ ] CSRF 토큰 적용 (상태 변경 요청)

## 외부 통신

- [ ] HTTPS only (HTTP 통신 금지)
- [ ] 타임아웃 설정 (무한 대기 방지)
- [ ] 응답 검증 (외부 API 응답도 신뢰하지 않음)
- [ ] SSRF 방지 (사용자 입력 URL을 내부 네트워크로 요청 금지)

## 프론트엔드

- [ ] XSS 방지 (dangerouslySetInnerHTML 사용 최소화, 사용 시 sanitize)
- [ ] 민감 정보를 localStorage/sessionStorage에 저장하지 않음
- [ ] 외부 스크립트 삽입 방지 (CSP 고려)

## 피해야 할 패턴

- `eval()`, `new Function()` 사용
- 사용자 입력을 HTML에 직접 삽입 (innerHTML)
- 암호화 직접 구현 (검증된 라이브러리 사용)
- 에러 메시지로 시스템 내부 구조 노출
- 디버그 엔드포인트를 프로덕션에 남김
