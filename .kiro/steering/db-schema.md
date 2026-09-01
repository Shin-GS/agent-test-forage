---
sourceId: db-schema
sourceVersion: "1.0"
sourceUpdatedAt: 2026-08-28
inclusion: fileMatch
fileMatchPattern: "**/packages/server/**,**/*.sql,**/domain/**,**/entity/**,**/repository/**"
---

# DB 스키마 참조

BE(server) 코드나 엔티티/리포지토리/SQL 작업 시 아래 DB 설계 문서를 먼저 참조한다.
스키마 변경이 필요하면 코드보다 문서를 먼저 갱신한다.

## 설계 문서

- 인덱스/관계도/원칙: #[[file:docs/db/README.md]]
- 스펙(외부 서버) 도메인: #[[file:docs/db/spec.md]]
- 사용자 도메인: #[[file:docs/db/user.md]]
- 레시피 도메인: #[[file:docs/db/recipe.md]]
- 실행/히스토리 도메인: #[[file:docs/db/execution.md]]
- 대화 도메인: #[[file:docs/db/conversation.md]]

## 핵심 원칙 (요약)

| 원칙 | 적용 |
|------|------|
| 네이밍 | 테이블 UPPER_SNAKE_CASE 단수형, PK=`ID`, FK=`{테이블}_ID`, 인덱스 `IDX_*`/`UQ_*` |
| 예약어 회피 | `USER` → `APP_USER` |
| JSON 컬럼 | 자유 구조 (스텝, operation, 스냅샷, 메타데이터) |
| 정규화 | 조회·필터·재개 필요한 것 (API 엔드포인트, 실행 스텝, 메시지) |
| Soft delete | 모든 삭제는 소프트 삭제 (`DELETED_AT`). 데이터/FK 연결을 물리적으로 지우거나 끊지 않음 |
| 공통 audit | 모든 테이블 `CREATED_AT`/`UPDATED_AT` (`@MappedSuperclass BaseEntity`) |
| Enum | `@Enumerated(STRING)`, 문자열 저장 |

## 반드시 지킬 것

- API 엔드포인트는 재등록 upsert 시 **PK(ID) 유지** (레시피가 논리 참조하므로)
- 실행 히스토리는 실행 시점 **레시피 스냅샷**을 저장 (원본 독립)
- 스펙 식별 키는 `BASE_URL` 단독 (environment 없음 — 인스턴스 환경별 분리)
- 히스토리는 대화 삭제와 독립 — 조회가 `USER_ID` 기준이라 독립. **연결(`EXECUTION.CONVERSATION_ID`)은 끊지 않고 유지** (소프트 삭제 원칙)
- **소프트 삭제 원칙**: 삭제는 `DELETED_AT`만 설정. row와 FK 연결은 유지 — "히스토리 독립" 같은 요구는 조회 기준으로 달성하지, 연결을 NULL로 끊어서 달성하지 않는다

## 규칙

코드 구현이 문서와 어긋나면, 임의로 코드를 진행하지 말고 문서를 먼저 갱신하거나 사용자에게 확인한다.
