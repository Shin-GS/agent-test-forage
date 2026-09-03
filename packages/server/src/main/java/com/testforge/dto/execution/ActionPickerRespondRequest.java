package com.testforge.dto.execution;

import java.util.Map;

/**
 * 액션 피커 입력 완료 응답 요청 (action-picker.md). 사용자가 액션 피커에서 값을 채워 제출하면
 * 일반 메시지 API가 아닌 이 별도 API로 구조화된 값을 전송한다. 서버는 값을 실행 context의
 * {@code userInput.*}에 병합한 뒤 대화방을 {@code WAITING_INPUT → EXECUTING}으로 전환하고
 * 실행을 재개한다.
 *
 * @param executionId 대상 실행 ID
 * @param stepIndex   값을 수집한 사용자 입력 스텝 인덱스. 프로토타입 pre-run 일괄 수집이면 {@code -1}
 *                    (pre-run 마커). 값 병합에는 사용하지 않지만 스키마상 수신한다.
 * @param values      수집한 입력값 맵 (변수 key → 값). userInput 하위에 병합된다.
 */
public record ActionPickerRespondRequest(
        Long executionId,
        Integer stepIndex,
        Map<String, Object> values) {
}
