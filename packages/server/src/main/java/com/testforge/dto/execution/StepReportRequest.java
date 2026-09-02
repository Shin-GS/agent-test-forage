package com.testforge.dto.execution;

import com.testforge.entity.execution.enums.ExecutionStepStatus;

import java.util.Map;

/**
 * 스텝 실행 결과 보고. FE가 브라우저에서 한 스텝(API 호출/스크립트 등)을 실행한 뒤 그 결과를 서버에
 * 보고한다. 서버는 EXECUTION_STEP을 갱신하고 {@code extractedValues}를 실행 전역 context에 누적한다
 * (execution.md 스텝별 저장 정책, structure.md 스텝 간 데이터 전달).
 *
 * @param status          스텝 결과 상태 (SUCCESS/FAILED/SKIPPED — PENDING 불가)
 * @param summary         결과 한 줄 요약 (진행 표시용, 30자 내외)
 * @param userInput       사용자 입력값 (액션 피커 응답 등, 없으면 null)
 * @param response        원시 응답 (1MB 초과 시 서버가 잘라 저장, 없으면 null)
 * @param errorMessage    실패 시 에러 메시지 (없으면 null)
 * @param extractedValues 이 스텝에서 추출한 변수 (context에 누적할 key-value, 없으면 null)
 */
public record StepReportRequest(
        ExecutionStepStatus status,
        String summary,
        Object userInput,
        Object response,
        String errorMessage,
        Map<String, Object> extractedValues) {
}
