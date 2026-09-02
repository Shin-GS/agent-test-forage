---
status: draft
last-updated: 2026-09-02
---

# 데모 서버 (검증용 가상 서버)

ai-test-forge의 레시피 실행 엔진을 실제 사용자 흐름으로 검증하기 위한 가상 데모 서버 모음.
목(mock)이 아니라 실제 HTTP 서버로 동작하며, 라이브러리를 통해 에이전트 서버에 스펙을 자동 등록한다.

## 원칙

- **모든 데이터·레시피·확인은 API로만** — DB 직접 삽입/조회 금지. 실제 사용자 흐름을 그대로 탄다.
- **인메모리 상태 저장** — DB/JPA 없이 `ConcurrentHashMap` 등으로 상태 보관. 재기동 시 초기화.
- **라이브러리 경유 등록** — `packages/library/java/21` 의존성을 붙여 기동 시 자동 등록(등록 API 직접 호출 아님).
- **쿠키 기반 세션 인증** — 서버별로 다른 세션 쿠키명. 서브도메인 same-site 전제라 `SameSite=Lax`(HTTPS 불필요). 인증 프로필은 로그인 대행이 아니라 `loginPageUrl` 안내용.
- **업종이 서로 다른 서버** — select_service 변별력 + 도메인 간 plan 조합 검증.

## 서버 목록

| 서버 | 업종 | 로컬 도메인 | 포트 | 세션 쿠키명 | 상태 |
|------|------|------------|------|------------|------|
| demo-shop | 이커머스 (상품/주문/결제) | shop.test-forge.local | 9101 | `SHOP_SESSION` | 1차 (먼저 완성) |
| demo-hospital | 병원 예약 (환자/진료예약/의사) | hosp.test-forge.local | 9102 | `HOSP_SESSION` | 2차 (복제) |
| demo-banking | 은행 (계좌/이체/거래내역) | bank.test-forge.local | 9103 | `BANK_SESSION` | 2차 (복제) |

> 서브도메인(same-site) 구성으로 쿠키 인증을 재현한다. 로컬 hosts 설정: [local-setup.md](./local-setup.md)

## 구현 순서

1. **데모 기획 문서** (현재 단계) — 서버별 API 카탈로그 + 레시피 정의
2. **라이브러리 세부 확인** — `TestForgeProperties`, `RegisterRequest` 등 연동 스펙
3. **demo-shop 구현** — 인메모리 서버 + 명확한 OpenAPI(example 포함) + 라이브러리 붙여 등록
4. **등록 확인** — `GET /api/v1/specs`로 demo-shop 등록 확인
5. **레시피 생성** — 문서 정의 기반으로 `POST /api/v1/recipes` 호출 (목록 조회로 중복 확인)
6. **검증** — AI에게 발화 → 레시피 실행 확인
7. **hospital/banking 복제** — 패턴 확립 후 확장

## 레시피 생성 흐름 (API로만)

문서는 "무엇을 만들지"의 정의이고, 생성·확인은 전부 API로 한다.

1. `GET /api/v1/specs` → 데모 서버 스펙이 등록됐는지 확인, apiSpecId 획득
2. `GET /api/v1/specs/{id}` → 실제 등록된 엔드포인트 목록 확인 (문서와 대조)
3. `GET /api/v1/recipes?apiSpecId={id}` → 이미 같은 레시피가 있는지 중복 확인
4. 없으면 `POST /api/v1/recipes` → 레시피 생성
5. `GET /api/v1/recipes?apiSpecId={id}` → 생성 결과 재확인

## 문서

- [로컬 실행 가이드 (hosts/서브도메인/쿠키)](./local-setup.md)
- [demo-shop](./demo-shop.md) — 이커머스
- demo-hospital — 병원 예약 (2차)
- demo-banking — 은행 (2차)
- scenarios — 서버 간 조합 시나리오 (2차)
