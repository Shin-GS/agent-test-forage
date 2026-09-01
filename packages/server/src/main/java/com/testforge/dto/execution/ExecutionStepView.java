package com.testforge.dto.execution;

import com.testforge.dto.common.StatusView;

import java.time.LocalDateTime;

/**
 * 스텝 실행 결과 뷰 (EXECUTION_STEP). 진행 표시/히스토리 상세에서 사용.
 * JSON 필드(userInput/response)는 저장 문자열을 객체로 파싱해 내린다.
 */
public record ExecutionStepView(
        Long id,
        Integer stepIndex,
        String stepName,
        StatusView stepType,
        StatusView status,
        String summary,
        Object userInput,
        Object response,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {
}
