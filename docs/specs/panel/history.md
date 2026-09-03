---
status: confirmed
last-updated: 2026-09-06
---

# 작업 히스토리

## 개요

레시피 실행 이력을 기록하고 조회하는 기능.

## 저장 정책

- 레시피 실행 건만 저장
- 대화방 삭제해도 히스토리는 독립적으로 유지
- 실행은 서버에 저장되며 대화 메시지와도 연결된다(`EXECUTION.MESSAGE_ID` → 실행의 PROGRESS 메시지). 대화 진입/새로고침 시 진행/결과 블록은 PROGRESS/RESULT 메시지 로드로 복원된다. 메시지가 화면의 진실이라면 EXECUTION은 독립된 사실/히스토리 계층이라, **대화를 삭제해도 실행 기록은 유지**된다. 상세: [recipe/execution.md 새로고침 복원](../recipe/execution.md#브라우저-새로고침--탭-닫기) · [messaging.md EXECUTION 계층](../common/messaging.md#execution-계층사실히스토리)

## 표시 정보

- 실행 시각
- 레시피명
- 생성된 데이터 요약
- 성공/실패 상태

## 패널 내 히스토리

사이드 패널 기본 화면에서 최근 실행 건을 표시.
항목 클릭 시 패널 내에서 실행 결과 상세 보기.

## 별도 페이지 (전체 히스토리)

상세: [pages/history-full.md](../pages/history-full.md)
