---
status: confirmed
last-updated: 2026-09-12
ref: docs/db/recipe.md, docs/specs/common/auth.md, docs/specs/pages/recipe-editor.md
---

# 레시피 버전 관리

## 방식

레시피 수정 이력을 별도 테이블([RECIPE_VERSION](../../db/recipe.md#recipe_version))로 스냅샷 관리한다.
수정 저장마다 직전 상태를 스냅샷으로 남기고, 특정 버전을 확인하거나 그 시점으로 복원할 수 있다.

## 기능

- 이전 버전 목록 조회
- 특정 버전 내용 확인
- 이전 버전으로 복원

## API

버전 API 경로는 레시피 리소스 하위에 중첩한다(복수형 리소스 `versions`, 버전 번호는 경로 파라미터).

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/v1/recipes/{id}/versions` | 버전 목록 (커서 페이징) |
| GET | `/api/v1/recipes/{id}/versions/{versionNo}` | 특정 버전 내용 |
| POST | `/api/v1/recipes/{id}/versions/{versionNo}/restore` | 해당 버전으로 복원 |

- 대상 레시피가 **소프트 삭제**(`DELETED_AT` 있음)되었거나 존재하지 않으면 모든 버전 API는 **404**로 응답한다(남의 개인 레시피 접근도 404, [404 vs 403 원칙](../common/auth.md#404존재-은폐-vs-403권한-부족-원칙)).

### GET `/api/v1/recipes/{id}/versions` — 버전 목록

- 커서 기반 페이징(CursorPage 패턴, [메시지 목록 조회](../common/messaging.md#메시지-목록-조회-커서-페이징)와 동일 형태).
- 정렬: `VERSION_NO DESC`(최신 버전이 위). 커서는 마지막 항목의 `versionNo`, 조건은 `versionNo < :cursor`.
- 요청: `?cursor={nextCursor}&size={n}` (`size` 기본 20, 최대 50)
- 응답: `{ items, nextCursor, hasNext }`
  - `items[]`: `{ versionNo, createdAt }` — 목록은 메타만 반환(스냅샷 본문 미포함, 경량)
  - `nextCursor`: 다음 페이지 커서(불투명 값). `hasNext=false`면 null
- `cursor`는 불투명 값으로 취급 — FE는 내부 형식을 해석하지 말고 응답의 `nextCursor`를 그대로 다음 요청에 전달.

### GET `/api/v1/recipes/{id}/versions/{versionNo}` — 특정 버전 내용

- 해당 버전 스냅샷(`SNAPSHOT_JSON`)을 펼쳐 반환한다.
- 응답 형태: **`RecipeDetailResponse` 호환** + `versionNo`, `createdAt` 추가.
  - 즉 현재 레시피 상세와 동일한 필드 구조(메타 + 스텝 + 변수 + 결과정의 + 결과템플릿 등)에 버전 식별 정보를 얹은 형태.
  - `RecipeDetailResponse` 호환이므로 요청자 기준 편집 가능 여부 `canEdit`(권한 힌트, [auth.md 레시피 권한 매트릭스](../common/auth.md#레시피-권한-매트릭스))를 포함한다. FE는 이를 근거로 미리보기의 [복원] 버튼 노출을 게이팅한다.
  - 편집 페이지의 미리보기가 현재 상세 렌더링 컴포넌트를 그대로 재사용할 수 있게 한다.
- 존재하지 않는 `versionNo`면 **404**.

### POST `/api/v1/recipes/{id}/versions/{versionNo}/restore` — 복원

- 대상 버전을 **새 버전으로 커밋**한다(아래 복원 정책).
- 응답: 복원 후의 최신 레시피 상세(`RecipeDetailResponse` 호환, `canEdit` 권한 힌트 포함) + 재검증 결과(`validationStatus` / `validationMessage`).

## 복원 정책

- 복원은 **덮어쓰기가 아니라 새 버전 커밋**이다. 대상 버전의 스냅샷을 현재 레시피에 반영하되, 그 반영 자체가 새로운 수정으로 이력에 남는다(선형 이력 유지, 되돌리기도 이력에 남음).
- **스냅샷 전체를 통짜로 반영**한다. 대상 버전 스냅샷(`SNAPSHOT_JSON`)의 모든 구성 요소 — 메타의 대상 서비스(`apiSpecId`) + 스텝별 `apiSpecId`/`endpointId` + 사용자 입력 변수 + 결과 정의 + 결과 템플릿 — 를 그대로 `RECIPE`에 반영한다. 일부 필드만 선택 복원하지 않는다.
- 절차([db/recipe.md 버전 번호 규칙](../../db/recipe.md#버전-번호-규칙)과 정합):
  1. 복원 직전의 현재 상태를 `RECIPE_VERSION`에 `VERSION_NO = 현재값`으로 스냅샷
  2. 대상 버전 스냅샷을 `RECIPE`에 반영
  3. `CURRENT_VERSION += 1`
- **복원 시 재검증**한다(스펙 호환성/순환 참조/필수 필드 등, [authoring.md 유효성 검증](authoring.md#유효성-검증)).
  - 재검증 결과가 `INVALID`여도 **복원은 허용**한다. 상태만 `VALIDATION_STATUS = INVALID` + `VALIDATION_MESSAGE`로 표시한다.
  - 이유: 과거 버전은 그 사이 스펙 변경(DEPRECATED/삭제)으로 무효화됐을 수 있으나, 사용자가 내용을 확인·수정할 수 있도록 일단 복원하는 편이 낫다. INVALID 레시피는 실행 전 유효성 경고로 걸러진다.

## 권한

버전 API의 권한은 레시피 본체 권한과 동일하다([auth.md 레시피 권한 매트릭스](../common/auth.md#레시피-권한-매트릭스)).

| 액션 | 공통(COMMON) | 개인(PRIVATE) |
|------|--------------|----------------|
| 버전 목록 조회 | 전원 | 소유자 본인만 |
| 특정 버전 조회 | 전원 | 소유자 본인만 |
| 복원 | ADMIN만 | 소유자 본인만 |

- 남의 개인 레시피의 버전에 접근하면 **404**(존재 은폐), 공통 레시피 복원을 non-admin이 시도하면 **403**([404 vs 403 원칙](../common/auth.md#404존재-은폐-vs-403권한-부족-원칙)).
- `userId` / `role`은 세션에서 도출한다(위조 금지).

## 편집 페이지 흐름

버전 기록 진입점, 미리보기 drawer, 복원 확인 흐름은 [레시피 편집 페이지 — 버전 기록](../pages/recipe-editor.md#버전-기록) 참조.
