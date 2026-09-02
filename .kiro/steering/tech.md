---
sourceId: tech
sourceVersion: "1.0"
sourceUpdatedAt: 2026-08-28
inclusion: always
---

# 기술 스택 (확정)

프로젝트의 확정 기술 스택. 버전은 여기서 관리하며, 다른 문서는 이 문서를 참조한다.

## 모노레포 구조

```
packages/
├── server/              # 에이전트 서버 (BE)
├── web/                 # 프론트엔드 (FE)
└── library/             # 스펙 등록 클라이언트 라이브러리
    └── java/
        └── 21/          # Java 21 (최초 버전)
```

- 각 패키지는 독립 빌드 (통합 빌드 도구 없음)
- `docs/`는 packages 밖 루트 (전 패키지 공통)

## server (BE)

| 항목 | 버전/선택 |
|------|-----------|
| 언어 | Java 25 (LTS) |
| 프레임워크 | Spring Boot 4.0 |
| Spring Framework | 7.0 (Boot 4.0 번들) |
| AI | OpenAI 호환 API 직접 호출 (RestClient). OpenAI/OpenRouter 등 |
| DB | MySQL |
| 빌드 | Gradle (Kotlin DSL) |
| 실시간 | SSE (Global SSE 1개) |

- AI 모델: reasoning(예 gpt-4o) + fast(예 gpt-4o-mini). base-url/key/model을 설정으로 교체 (OpenRouter 우선)
- Tool Calling: OpenAI 호환 function tools + tool_calls 직접 파싱. IntentResolver 인터페이스 뒤에 구현
  (RuleBased 목 / OpenAiCompatible 실제). Spring AI는 현 규모에선 불필요(IntentResolver가 이미 확장 경계).
  investigate 루프가 복잡해지거나 임베딩/RAG(시맨틱 레시피 검색)가 필요해지면 재검토 — IntentResolver
  뒤에서 부분 도입 가능
- investigate 반복 루프도 직접 제어(최대 5회/120초 + SSE 진행). 다음 조각

## web (FE)

| 항목 | 버전/선택 |
|------|-----------|
| 언어 | TypeScript |
| 프레임워크 | React 19 |
| 빌드 | Vite |
| 스타일 | TailwindCSS (tokens.css 변수 매핑) |
| 상태 (전역) | Zustand |
| 상태 (서버) | @tanstack/react-query |
| 패키지 매니저 | pnpm |

- 디자인 토큰: `docs/design/shared/tokens.css` → tailwind.config 매핑
- 레시피 API 실행: 브라우저에서 외부 서버 직접 호출 (fetch credentials: include)

## library/java/21

| 항목 | 버전/선택 |
|------|-----------|
| 언어 | Java 21 (LTS) |
| 빌드 | Gradle |

- 역할: OpenAPI 스펙 자동 수집 + ai-test-forge 등록 + heartbeat + CORS 자동 설정
- 다양한 언어/버전 지원 예정 (java/8, php 등 추후 — 회사가 다언어/다버전 사용)

## LTS 원칙

- Java는 LTS만 사용 (8, 11, 17, 21, 25)
- server = Java 25, library 최초 = Java 21 (테스트 서버 버전에 맞춤)

## 패키지 컨벤션 (server)

레이어(계층) 최상위 + 그 아래 도메인 하위 구조. 새 도메인(chat 등) 추가 시 동일 규칙 적용.

```
com.testforge
├─ controller/{domain}/   ← 도메인별 (HealthController 등 공용은 controller/ 직속)
├─ service/{domain}/
├─ repository/{domain}/
├─ entity/{domain}/       ← 엔티티, 그 하위 enums/ 에 도메인 enum
│  └─ common/             ← BaseEntity 등 공용 엔티티
├─ dto/{domain}/          ← 공용 DTO는 dto/common/
├─ parser/                ← 최상위 유지 (도메인으로 안 나눔)
├─ scheduler/             ← 최상위 유지
├─ config/                ← 공용
├─ common/                ← EnumColumn, error/ 등 횡단 공용
└─ utils/                 ← 순수 헬퍼
```

- DB에 저장되거나 코드/설명 쌍이 필요한 enum은 `common.EnumColumn`(getCode/getDescription) 구현 + `@Enumerated(STRING)` (컨버터 미사용)
- 주석은 한글 기본, 코드는 영어

## 로컬 빌드 주의 (Windows, 비ASCII 경로)

- 프로젝트 경로에 비ASCII 문자(예: 사용자 홈 `신경섭`)가 있으면 Windows에서 Gradle test worker classpath가 손상되어 `test` 태스크가 실패할 수 있음
- `server` 패키지는 이를 위해 `-PasciiBuildDir` opt-in 플래그 제공 (기본 체크아웃/CI에는 무영향)
- 로컬 테스트 실행 시: `GRADLE_USER_HOME=C:\gradle-home` 지정 + `-PasciiBuildDir=C:\tf-build` 플래그 사용
- `compileJava`/`assemble`은 플래그 없이도 정상 동작

## 미확정 / 추후

- AI 프로바이더/모델의 런타임 변경(설정 페이지, DB 저장)은 추후 — 지금은 프로퍼티(.env)
- MySQL 버전, 배포 인프라는 추후
- 라이브러리 추가 언어/버전은 필요 시 확장
