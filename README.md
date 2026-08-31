# AI Test Forge

AI 기반 API 워크플로우 실행 플랫폼. 채팅으로 레시피를 실행하여 테스트 데이터를 생성한다.

## 모노레포 구조

```
packages/
├── server/              # 에이전트 서버 (Spring Boot 4 + Java 25 + Spring AI 2.0)
├── web/                 # 프론트엔드 (React 19 + Vite + TS + TailwindCSS v4)
└── library/             # 스펙 등록 클라이언트 라이브러리
    └── java/
        └── 21/          # Java 21 (최초 버전)
docs/
├── specs/               # 기획 문서
├── design/              # 디자인 명세 (HTML/CSS, 브라우저에서 확인)
└── test/                # QA 수동 테스트 체크리스트
```

각 패키지는 독립 빌드한다. 통합 빌드 도구는 두지 않는다.

## 기술 스택

기술 스택 상세/버전은 `.kiro/steering/tech.md`에서 관리한다.

| 패키지 | 스택 |
|--------|------|
| server | Java 25, Spring Boot 4.0, Spring AI 2.0, MySQL, SSE |
| web | React 19, Vite, TypeScript, TailwindCSS v4, Zustand, React Query |
| library/java/21 | Java 21, Gradle |

## 실행

### server
```bash
cd packages/server
./gradlew bootRun          # http://localhost:8080
# 헬스체크: GET /api/v1/health
```

### web
```bash
cd packages/web
pnpm install
pnpm dev                   # http://localhost:5173
```

### library/java/21
```bash
cd packages/library/java/21
./gradlew assemble         # jar 생성
# 참고: 로컬에 Java 21이 없으면 test 태스크가 실패할 수 있음 (컴파일은 release=21로 정상).
#       테스트 실행 시 Java 21 설치 권장.
```

## 라이브러리 지원 매트릭스

| 언어 | 버전 | 상태 |
|------|------|------|
| Java | 21 | 뼈대 (개발 예정) |
| Java | 8, 11, 17 | 추후 |
| PHP | — | 추후 |

회사가 다양한 언어/버전을 사용하므로 언어·버전별로 라이브러리를 확장한다.

## 호출 주체 구분

| 작업 | 호출 주체 |
|------|----------|
| 레시피 API 실행 | FE 브라우저 (외부 서버 직접 호출, CORS 자동 허용) |
| 정보 조회 (investigate) | BE (서버 시크릿 토큰으로 Jira 등 조회) |

## 검증용 데모 서버

스펙 등록/CORS/인증/레시피 실행 검증용 외부 데모 서버는 이 레포 외부의 별도 프로젝트로 관리한다.

## 문서

- 기획: `docs/specs/README.md`
- 디자인: `docs/design/README.md`
- QA: `docs/test/README.md`
