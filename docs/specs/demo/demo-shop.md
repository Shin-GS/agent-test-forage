---
status: draft
last-updated: 2026-09-03
---

# demo-shop (이커머스 데모 서버)

상품 조회 → 주문 생성 → 결제 → 조회로 이어지는 이커머스 흐름을 제공하는 가상 서버.
레시피 엔진의 스텝 체이닝(앞 스텝 결과를 뒤 스텝이 사용)을 검증하는 것이 핵심.

- 로컬 도메인: `shop.test-forge.local` (포트 `9101`)
- baseUrl(식별 키): `http://shop.test-forge.local:9101`
- 세션 쿠키: `SHOP_SESSION` (로컬 `SameSite=Lax`, 서브도메인 same-site 전제 — [local-setup.md](./local-setup.md))
- 상태: 인메모리(`ConcurrentHashMap`), 기동 시 상품/사용자 초기 데이터 자동 시드
- 등록: 라이브러리 경유 자동 등록 (서비스명 `demo-shop`)

## 서비스 메타 (serviceInfo — AI 매칭 컨텍스트)

등록 시 라이브러리가 함께 전송한다(registration.md serviceInfo). AI 서비스 선택 변별력에 직결.

```yaml
ai-test-forge:
  name: "demo-shop"
  service:
    description: "온라인 쇼핑몰 API (상품/주문/결제)"
    domain: "커머스"
    capabilities: ["상품조회", "주문", "결제", "주문취소"]
    notes: "데모 서버, 실 결제 없음"
```

## 인증

인증 프로필은 **로그인을 대행하지 않는다**. `loginPageUrl`(로그인 페이지)만 등록해 두고,
레시피 실행 중 401/403이 나면 "여기서 로그인하세요"라고 그 URL을 사용자에게 안내한다.
실제 로그인은 사용자가 그 페이지에서 직접 수행해 세션 쿠키를 얻는다.

- `POST /login` → 성공 시 `Set-Cookie: SHOP_SESSION=...; SameSite=Lax` 발급 (사용자가 브라우저에서 직접 로그인하는 페이지가 호출)
- 이후 인증 필요한 API는 이 쿠키를 요구. 없거나 만료 시 **401** (항상 통과 금지 — 인증 프로필 안내 검증용)
- 인증 프로필 등록 예: `{ name: "일반", loginPageUrl: "http://shop.test-forge.local:9101/login-page" }`

> 레시피 안에 "로그인 API 스텝"을 넣지 않는다. 레시피의 사전 조건은 "로그인된 세션 쿠키 보유"이며,
> 미보유 시 401 → 인증 프로필의 loginPageUrl 안내로 흐른다.

## API 카탈로그

각 엔드포인트는 OpenAPI에 명확한 한글 summary/description + example을 붙인다(AI 매칭 품질 직결).

| 인증 | Method | Path | 설명 |
|------|--------|------|------|
| 👤 | POST | `/login` | 로그인. 세션 쿠키 발급. req `{username, password}`. **사용자 로그인용(레시피가 호출하지 않음)** |
| — | GET | `/products` | 상품 목록 조회. `?keyword=` 선택 |
| — | GET | `/products/{id}` | 상품 단건 조회 |
| 🔒 | POST | `/orders` | 주문 생성. req `{productId, quantity}` → res `{orderId, status, amount}` |
| 🔒 | GET | `/orders/{id}` | 주문 조회 |
| 🔒 | POST | `/orders/{id}/cancel` | 주문 취소 |
| 🔒 ⚠️ | POST | `/payments` | 결제. req `{orderId, method}` → res `{paymentId, status}`. **`@TestForgeConfirm`** (실행 전 사용자 확인) |
| 🔒 | GET | `/payments/{id}` | 결제 조회 |

- 🔒 = 세션 쿠키 필요, ⚠️ = 되돌릴 수 없는 작업(`@TestForgeConfirm` 부착 → 실행 전 확인), 👤 = 사용자 로그인용(레시피 비호출)
- 내부/관리용 API가 생기면 `@TestForgeExclude`로 AI 노출에서 제외한다.

### 응답 예시

```
POST /orders  {productId: 1, quantity: 2}
→ 201 {orderId: 1001, status: "CREATED", amount: 39800}

POST /payments  {orderId: 1001, method: "CARD"}
→ 201 {paymentId: 5001, status: "PAID", orderId: 1001}

GET /orders/1001
→ 200 {orderId: 1001, status: "PAID", amount: 39800, productId: 1, quantity: 2}
```

### 상태 전이 (주문)

```
CREATED --결제--> PAID --취소--> CANCELLED
   └────────────취소────────────┘
```

## 초기 시드 데이터 (기동 시 코드로 주입)

- 사용자: `demo` / `demo1234` (로그인용)
- 상품 5개: 예) id=1 무선마우스(19900), id=2 키보드(49000), id=3 모니터(230000), id=4 USB허브(15000), id=5 웹캠(58000)

> 주의: 시드는 서버 기동 시 자신의 인메모리 초기화 시점에 코드로 채운다(데모 서버 자체 편의).
> 레시피/에이전트 쪽 데이터는 이와 무관하게 전부 API로 만든다.

## 레시피 정의

아래 정의를 기반으로 `POST /api/v1/recipes`를 호출해 생성한다.
`{{...}}` 변수 문법과 스텝 타입은 recipe/structure.md 규약을 따른다.

> 공통 사전 조건: 모든 레시피는 **로그인된 세션 쿠키 보유**를 전제한다(레시피에 로그인 스텝 없음).
> 쿠키 미보유 시 첫 인증 API에서 401 → 인증 프로필 loginPageUrl 안내로 흐른다.

### 레시피 1: 상품 주문하기 (기본 흐름 · 스텝 체이닝)

- **목적**: 상품 주문의 기본 흐름. API 스텝 간 변수 전달 검증.
- **대상 서비스**: demo-shop
- **사용자 입력 변수**: `productId`(숫자), `quantity`(숫자, 기본 1)
- **스텝**:
  1. `[API]` 주문 생성 — `POST /orders` body `{productId: {{userInput.productId}}, quantity: {{userInput.quantity}}}`, extract `orderId`
  2. `[API]` 주문 확인 — `GET /orders/{{orderId}}`
- **결과**: 생성된 주문의 orderId/status/amount 요약

사용자 입력 변수 스키마 예시 (레시피 메타에 선언 → 실행 시작 직전 액션 피커로 렌더링. 스키마 형식은 [action-picker.md 변수 정의 스키마](../chat/action-picker.md#변수-정의-스키마) 참조):

```json
{
  "variables": [
    {
      "key": "productId",
      "label": "상품 ID",
      "type": "number",
      "required": true,
      "min": 1,
      "placeholder": "상품 ID (예: 1)"
    },
    {
      "key": "quantity",
      "label": "수량",
      "type": "number",
      "required": true,
      "default": 1,
      "min": 1,
      "max": 100
    }
  ]
}
```

- 스텝의 `{{userInput.productId}}` / `{{userInput.quantity}}`는 위 선언과 1:1 대응한다(선언되지 않은 `userInput` 키 사용 금지).
- 자동 실행 모드에서 "마우스 2개 주문해줘"처럼 발화로 값이 충족되면 액션 피커 없이 바로 실행된다. 필수값(`productId`)이 비면 실행 직전 액션 피커로 미충족 필드만 물어본다. (규칙: [execution.md](../recipe/execution.md#자동-실행-모드에서-값-부족-처리-확정))

### 레시피 2: 주문하고 결제까지 (조건 분기 포함)

- **목적**: 주문→결제 연결 + 조건 분기 검증.
- **사용자 입력 변수**: `productId`, `quantity`, `payMethod`(CARD/BANK)
- **스텝**:
  1. `[API]` 주문 생성 — `POST /orders`, extract `orderId`, `orderStatus`
  2. `[API]` 결제 — `POST /payments` body `{orderId: {{orderId}}, method: {{userInput.payMethod}}}`, extract `payStatus`
     - condition: `{{orderStatus}} === "CREATED"` (주문이 생성된 경우에만 결제)
     - 주의: 결제 API는 `@TestForgeConfirm` 대상 → 실행 전 사용자 확인 카드 노출
  3. `[API]` 최종 주문 확인 — `GET /orders/{{orderId}}`
- **결과**: 결제 완료 여부 + 최종 주문 상태

### 레시피 3: 주문 취소 (스크립트 판단 포함)

- **목적**: 스크립트 스텝(조건 판단) 검증.
- **사용자 입력 변수**: `orderId`(숫자)
- **스텝**:
  1. `[API]` 주문 조회 — `GET /orders/{{userInput.orderId}}`, extract `orderStatus`
  2. `[스크립트]` 취소 가능 판단 — `context.orderStatus`가 CANCELLED가 아니면 `{cancelable: true}` 반환
  3. `[API]` 주문 취소 — `POST /orders/{{userInput.orderId}}/cancel`
     - condition: `{{cancelable}} === true`
- **결과**: 취소 성공 여부
- ⚠️ 가정: 스크립트 반환값(`cancelable`)을 후속 스텝에서 `{{cancelable}}`로 참조 가능하다고 전제.
  structure.md 변수 전달 표에는 스크립트 반환값의 `{{}}` 참조가 명시돼 있지 않으므로,
  실제 실행 엔진이 스크립트 반환 객체를 변수 스코프에 병합하는지 실행 단계에서 검증 필요.

### 레시피 4: 첫 구매 온보딩 (서브레시피 조합)

- **목적**: 서브레시피 호출 검증. "상품 주문하기"(레시피1)를 서브로 호출.
- **사용자 입력 변수**: `productId`
- **스텝**:
  1. `[레시피]` 상품 주문하기(레시피1)를 서브레시피로 호출 (quantity=1 고정)
- **결과**: 온보딩 주문 완료 안내 (서브레시피 결과의 orderId/status)
- 주의: 서브레시피는 레시피1이 먼저 생성돼 recipeId가 있어야 참조 가능(생성 순서: 1 → 4).
- 서브레시피 호출만으로 단순화(서브레시피 검증이 목적). 존재하지 않는 목록 API 참조를 피함.

## 검증 시나리오 (AI 발화 → 기대 tool)

| 발화 | 기대 동작 |
|------|-----------|
| "마우스 하나 주문해줘" | execute_recipe(레시피1) 또는 show_candidates |
| "주문하고 결제까지 해줘" | execute_recipe(레시피2) |
| "1001번 주문 취소해줘" | execute_recipe(레시피3) |
| (서비스 미지정) "주문할래" | select_service (demo-shop 등 후보 제시) |
| "환자 예약 잡아줘" | no_match 또는 다른 서비스 (shop엔 없음) |

> 위 시나리오는 정상 실행 경로만 표기. 세션 쿠키 미보유 상태의 최초 실행은 첫 인증 API에서
> 401 → 인증 프로필 loginPageUrl 안내가 선행될 수 있다.

## 구현 시 유의 (실물 기준으로 반영)

- **인증 안내 선행**: 레시피는 로그인 쿠키를 전제하므로, 최초 실행은 401 → loginPageUrl 안내 흐름이 낄 수 있다. 구현/실행 검증 시 이 경로를 함께 확인한다.
- **상품명 → productId 변환 부재**: 레시피1/2는 사용자가 `productId`를 직접 입력하는 전제다. "마우스 주문해줘" 같은 자연어와의 괴리를 줄이려면 `GET /products?keyword=`로 검색 후 id를 추출하는 스텝을 앞에 붙인 "상품 검색 후 주문" 레시피(추후 레시피5)를 추가한다. 지금 단계(스텝 타입 커버)에서는 직접 입력으로 충분.
