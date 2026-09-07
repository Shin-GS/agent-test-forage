---
status: draft
last-updated: 2026-09-19
---

# AI 설정 (인프라)

## 기술 스택 방향

- **OpenAI 호환 API 직접 호출(RestClient)** — 현 규모에선 Spring AI 없이 직접 호출. 근거: 우리의 확장
  경계는 이미 `IntentResolver` 인터페이스이므로 Spring AI의 프로바이더 추상화(ChatModel)는 중복이다.
  tool calling은 OpenAI 호환 스펙(function tools + tool_calls)이 안정적이고 tool이 7종으로 고정이라 직접
  파싱이 관리 가능하다. Spring AI 2.0(Boot4) 성숙도 리스크도 회피한다. 프로바이더가 늘면
  `IntentResolver` 구현체를 추가한다(코드 격리).
  - **Spring AI 재검토 조건**: (1) investigate의 tool 반복 루프가 직접 관리하기 복잡해질 때
    (ToolCallingManager 가치), (2) 임베딩 기반 시맨틱 레시피 검색/RAG가 필요해질 때(EmbeddingModel +
    VectorStore 통합 가치). 현 규모(레시피 수백~천 개)는 서비스별 필터 + 전체 목록을 프롬프트에 넣어도
    토큰 한도(128K) 내라 임베딩 불필요. 재검토 시 IntentResolver 뒤에서 부분 도입 가능.
- **프로바이더 전환은 설정으로** — base-url + api-key만 바꾸면 OpenAI/OpenRouter 등 OpenAI 호환
  엔드포인트를 전환. 지금은 프로퍼티(.env), 추후 설정 페이지(DB)로 확장(settings.md). 읽기 창구는
  `AiSettings` 하나로 일원화하여 소스가 바뀌어도 호출 코드는 불변.
- **SSE 스트리밍** — AI 응답 및 investigate 조회 진행 상태를 SSE로 FE에 실시간 전달
- 모델/버전은 설정으로 교체. 구조는 특정 모델/버전/프로바이더에 종속되지 않게 설계

## 모델 구성

| 역할 | 모델(OpenRouter 모델명) | 용도 |
|------|------|------|
| reasoning | `openai/gpt-4o` (또는 동급) | 의도 분석, 플랜 구성, 정보 조회 판단 |
| fast | `openai/gpt-4o-mini` (또는 동급) | AI 생성(필드 값), 이력 요약, 결과 요약, 서비스 설명 초안 |

- **Provider는 OpenRouter 고정.** OpenRouter가 OpenAI 호환 단일 엔드포인트로 여러 모델(ChatGPT 계열 등)을
  제공하므로, 모델은 OpenRouter 모델명(`openai/gpt-4o` 등)으로 지정한다. tool calling 지원 모델만 사용.
- **모델/설정 변경은 서버 설정 파일(.env/application.yml)로만.** 사용자·관리자 모두 UI에서 못 바꾼다
  (settings.md: 설정 페이지는 읽기 전용). DB에도 저장하지 않는다 — 설정 파일이 유일한 소스.

---

## AI 크레딧/한도 소진 처리

OpenRouter는 크레딧/데일리 한도를 넘으면 **HTTP 402(Payment Required)** 또는 **429(Too Many Requests)**
를 반환한다. 이는 일시적 서버 오류와 성격이 다르다(재시도해도 소용없거나, 한도 회복까지 대기 필요).

### 처리 정책

| 상황 | 감지 | 사용자 처리 |
|------|------|------------|
| 크레딧/한도 소진 | HTTP 402 / 429 | **전용 안내 메시지** ("AI 사용 한도에 도달했습니다. 잠시 후 다시 시도해 주세요") |
| 그 외 AI 오류 | 4xx/5xx, 파싱 실패 | 일반 오류 안내 ("처리 중 문제가 발생했어요") |

- **일반 오류와 구분한다.** 크레딧 소진은 원인이 명확하므로 뭉뚱그리지 않고 전용 메시지로 안내한다.
- **규칙 기반 목으로 폴백하지 않는다.** 목은 품질이 낮아 "AI가 되는 척"이 되어 오히려 혼란이다.
  한도 도달 사실을 그대로 알리고, 대화방은 idle로 종결한다(입력 잠금 해제).
- 서버 로그에는 상태코드/응답 본문을 남겨 운영자가 한도 소진을 인지하게 한다(키는 로그에 남기지 않음).
- 재시도: 402는 재시도 무의미(크레딧 없음). 429는 헤더의 재시도 안내가 있으면 참고하되, 프로토타입은
  "잠시 후/한도 회복 후 다시"로 안내만 한다(자동 재시도 없음).

---

## 정보 조회 (investigate) 루프

정책/기능 질문에 답하기 위해 AI가 정보 소스를 반복 조회하는 agentic loop. 상세 흐름·안전장치·보안은 [investigation.md](../chat/scenarios/investigation.md) 참조.

### 커넥터 (조회 소스)

외부 소스를 "커넥터"로 추상화하여 확장 가능하게 설계. 신규 소스 추가 시 커넥터 인터페이스만 구현.

| 커넥터 | 설명 | 단계 |
|--------|------|------|
| `api_spec` | 등록된 스펙 조회 (스키마, 어노테이션 힌트). 내부 DB 조회 | ✅ **1단계 구현** |
| `confluence` | Confluence 위키 문서 조회. Atlassian Cloud REST(CQL) + 서버 시크릿(Basic auth: email+토큰) | ✅ **2단계 구현 (확정)** |
| `figma` | Figma 디자인 조회 | ⏳ 추후 (key 확보 + 사용성 검증 후) |

- `api_spec`(내부 DB)과 `confluence`(외부 Atlassian Cloud REST) 모두 실제 구현한다. confluence는 `CONFLUENCE_BASE_URL`/`CONFLUENCE_EMAIL`/`CONFLUENCE_API_TOKEN`(서버 시크릿, Basic auth)으로 호출하고, 서비스별 `confluenceSpaceKey`로 조회 space를 한정한다.
- `investigate` tool `source` enum(`api_spec`/`confluence`)의 **두 값 모두 유효**하다. AI 소스 선택 기준(발화 면 판단)·검색/재주입/references 규칙은 [investigation.md 소스 판단 경계](../chat/scenarios/investigation.md#소스-판단-경계-api_spec-vs-confluence) 및 [confluence 커넥터 조회 정의](../chat/scenarios/investigation.md#confluence-커넥터-조회-정의-2단계) 참조.

### 루프 제약

| 항목 | 값 | 이유 |
|------|----|----|
| 최대 조회 횟수 | 5회 | 무한 조회 방지 |
| 전체 타임아웃 (루프) | 120초 | 응답 지연 방지 |
| 커넥터 개별 타임아웃 (`api_spec` / `confluence`) | 5초 (기본값) | 단일 소스가 전체를 잡아먹지 않게 (2층 타임아웃 하위 층). confluence는 외부 REST라 5초 초과 시 그 조회만 실패 처리 |
| 승인 | 불필요 | 읽기 전용 (외부 데이터 생성 없음) |

- **루프 카운터 = 커넥터 조회 횟수(최대 5회)**로 확정한다(AI 호출 횟수 아님). **5회째 조회 결과를 받은 뒤의 AI 호출은 `investigate` tool을 제거하고 `tool_choice=chat`을 강제**한다(실제 조회 최대 5회, AI 호출 최대 6회). 정상 조회·미지원 source 스킵·중복 캐시 재사용 등 **모든 조회 시도가 카운터를 소비**한다(우회 불가).
  - 의사코드: `조회수>=5 → 다음 AI 호출은 tools에서 investigate 제외 + tool_choice=chat`
- **커넥터 개별 타임아웃은 미설정 시 기본값**(`api_spec`·`confluence`=5초)을 적용한다. **개별 타임아웃 < 루프 전체 타임아웃(120초)** 원칙을 지켜, 단일 조회가 루프 전체를 소진하지 않게 한다.
- 상세 종료 보장·폴백 계층은 [investigation.md 루프 안전장치](../chat/scenarios/investigation.md#루프-안전장치-핵심) 참조.

### 루프 안전장치

- **2층 타임아웃**: 커넥터 개별 조회 타임아웃 + 루프 전체(120초) 타임아웃. 개별 조회 타임아웃은 그 조회만 실패 처리(전체 중단 아님).
- **중복 `(source, query)` 감지**: 같은 질의 반복 시 캐시 결과 재사용 + "이미 조회함" 신호로 AI가 다른 각도로 유도(진전 없는 횟수 소진 방지). 캐시 영속화는 2단계.
- **마지막 턴 chat 강제**: 5회째(마지막) 조회 후 AI 호출은 `investigate` tool을 빼고 `tool_choice`로 `chat`만 강제 → 수집 정보로 반드시 답하게(무응답 루프 방지). 정보 부족 시 "못 찾음" 정직 안내.
- **최종 답변 폴백 계층(AI 비의존)**: 5회 제한과 120초 타임아웃 중 **먼저 도달한 것이 우선**(종결 경로 동일). 타임아웃 도달이나 chat 강제 호출 자체가 실패/타임아웃이면 **AI 호출 없이 FE 고정 안내로 폴백**한다("조회 시간이 초과되었습니다" / "정보를 찾지 못했습니다"). 무응답 방지 최종 방어선은 AI 비의존 경로다.
- **종결 보장**: 어떤 예외/타임아웃에도 try/finally로 대화방 `idle` 복귀(락 잔존 방지). `finally`에서 진행 블록이 `running`으로 남았으면 `failed`/`timeout`으로 확정 발행(유령 진행 블록 방지).
- 못 찾음/타임아웃/전 커넥터 실패 → "정보를 찾지 못했습니다" 정직 안내(할루시네이션·억지 답변 금지).

### 루프 제어 (직접 구현)

OpenAI 호환 API를 직접 호출하므로, investigate 루프도 우리가 직접 관리한다: "tool_calls가 있으면
커넥터 실행 → 결과를 messages에 추가 → 재호출"을 최대 5회/타임아웃 120초까지 반복하고, 매 조회마다
SSE로 진행 상태(INVESTIGATE_PROGRESS 메시지 갱신)를 보낸다. 단순 tool 선택(1회 호출)과 달리 investigate만
이 반복 루프를 쓴다. **전용 서비스(`InvestigateLoop`)로 분리**하고 `IntentResolver.resolve()`는 단발 tool
선택 책임을 유지한다(단발 경로 오염 방지).

---

## 토큰 절약 전략

### 단계적 호출

한번에 모든 정보를 보내지 않고, 가벼운 판단 → 필요한 것만 추가 로드.

### 프롬프트 캐싱

- 시스템 프롬프트 + 레시피 목록을 동일한 prefix로 구성
- OpenAI는 동일 prefix 반복 호출 시 자동 캐싱 (비용 50% 절감)
- 변하는 부분(이력 + 발화)은 suffix로

### 이력 압축

- 전달 대상: 설정된 "대화 이력 전달 수"(기본 15건, [설정](../pages/settings.md))
- 그중 최근 3~5건은 원문 + 그 이전은 요약 1문단으로 압축
- 요약은 매 5턴마다 fast 모델로 생성해서 BE에 저장
- 레시피 실행 결과: 스텝별 한 줄 요약 + extract된 key-value만 (원시 JSON 제외)

### 레시피 목록 필터링 (AI 호출 전 BE에서 처리)

- 대화방 서비스 설정됨 → 해당 서비스 레시피만 전달
- 서비스 미지정 → 서비스 목록(30개 이름만) 전달하여 먼저 서비스 확정
- AI에게 500개 전체를 보내는 일은 없음

### 레시피 변수 스키마 전달 (발화값 추출용)

- 레시피 목록을 전달할 때 각 레시피의 **사용자 입력 변수 스키마**(key/label/type/required + 있으면 description)를 함께 전달한다.
- 목적: AI가 발화("1번 상품 2개")에서 `extractedValues`를 뽑을 때 **어떤 key에 어떤 타입으로 담을지** 알 수 있게 한다. 변수 스키마가 없으면 AI는 값을 추출해도 매핑할 key를 모른다.
- 토큰 절약 원칙 유지: 변수 스키마는 추출에 필요한 최소 필드만(원시 정의 전체·스텝 매핑 제외). 현 규모(서비스당 15~20개 레시피)에선 부담 없음.
- 추출된 값은 실행 시작 전 변수 `type`에 맞게 파싱/정규화한다(예: number는 숫자로). 추출이 비거나 틀려도 액션 피커로 사용자가 보정한다(best effort).

### Function Calling

- AI 응답을 JSON으로 강제 (불필요한 텍스트 출력 방지)
- 출력 토큰 절약 + 파싱 에러 방지

---

## 프로토타입 제약

- 플랜은 1개 서비스의 레시피만 대상 (서비스 간 플랜은 추후)
- 프롬프트는 코드에 하드코딩 (추후 DB/설정으로 이동 가능)
- 이력 요약은 단순 잘라내기로 시작, 추후 AI 요약 도입

---

## 시나리오별 상세

각 분기의 전체 흐름(프롬프트 + UI + 사용자 액션)은 시나리오 문서 참조:

- [의도 분류 + 레시피 매칭](../chat/scenarios/intent-classification.md)
- [서비스 선택](../chat/scenarios/service-selection.md)
- [플랜 제안](../chat/scenarios/plan-proposal.md)
- [레시피 실행](../chat/scenarios/recipe-execution.md)
- [AI 필드 생성](../chat/scenarios/ai-generation.md)
- [결과 요약](../chat/scenarios/result-summary.md)
- [정보 조회 루프](../chat/scenarios/investigation.md)
