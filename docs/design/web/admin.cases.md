---
status: done
last-updated: 2026-08-28
ref: docs/specs/pages/admin.md
---

# 관리자 페이지 케이스

## 참조 기획

- [관리자 페이지](../../specs/pages/admin.md)

## 레이아웃

별도 페이지. 전체 화면, 테이블 뷰 기반.
관리자 전용 (일반 사용자에게 미노출).

## 케이스 목록

| # | 케이스 | 설명 |
|---|--------|------|
| 1 | 스펙 관리 목록 | 등록된 서비스 + 상태(ACTIVE/STALE) + API 수 |
| 2 | 스펙 상세 | 서비스 설명(편집) + API 엔드포인트 목록 + 인증 프로필 |
| 3 | 사용자 관리 | 사용자 목록 + 역할/상태 + 초대 |

## 서비스 설명 (Case 2)

- description / domain / capabilities / notes 표시
- [편집] 버튼으로 관리자 직접 수정
- "관리자 수정됨" 뱃지 (yml 값보다 우선)
- yml 값 변경 시 "변경 감지" 경고 + 변경 내용 보기 (강제 덮어쓰기 X)
- 참조: [스펙 등록 방식](../../specs/spec/registration.md)

## 스펙 상태 / 수동 관리

- 스펙 상태: ACTIVE / STALE / **INACTIVE**(관리자 수동 비활성)
- 목록에서 INACTIVE 스펙은 흐리게 표시 (예: URL 변경으로 남은 좀비 스펙)
- 상세에서 [비활성화]/[활성화] 토글 + [삭제]
- API 목록에 **DEPRECATED 뱃지** (스펙에서 사라진 API, 참조 레시피는 경고)
- 참조: [스펙 등록 - 재등록 정합성](../../specs/spec/registration.md)

## 접근성

- role 변경은 confirm 필요
- 삭제 액션은 모달 확인
- 상태 뱃지에 텍스트 포함 (색상만으로 구분하지 않음)
