---
status: done
last-updated: 2026-08-28
ref: docs/specs/pages/settings.md
---

# 설정 페이지 케이스

## 참조 기획

- [설정 페이지](../../specs/pages/settings.md)

## 레이아웃

별도 페이지. 전체 화면, 중앙 정렬 form.

## 케이스 목록

| # | 케이스 | 설명 |
|---|--------|------|
| 1 | 기본 상태 | AI 설정 + 실행 설정 + 계정 섹션 |
| 2 | 비밀번호 변경 | 비밀번호 변경 폼 펼침 |
| 3 | 저장 완료 | 저장 성공 토스트 |

## 접근성

- 각 섹션 heading으로 구분
- form 필드에 label 연결
- 저장 성공 시 aria-live 알림
