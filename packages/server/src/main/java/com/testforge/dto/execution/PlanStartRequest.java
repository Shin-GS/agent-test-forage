package com.testforge.dto.execution;

import com.testforge.entity.execution.enums.ExecutionMode;

import java.util.List;
import java.util.Map;

/**
 * 플랜 실행 시작 요청. plan 카드의 [실행] 버튼이 트리거한다(여러 레시피를 순서대로 실행).
 * {@code recipeIds}의 순서가 곧 실행 순서다. 첫 레시피만 RUNNING + 스텝 생성으로 시작하고 나머지는
 * PENDING으로 대기하며, 각 레시피가 끝날 때마다 다음 레시피로 전이한다(reportStep 오케스트레이션).
 *
 * <p>단일 실행({@link ExecutionStartRequest})과 시작 로직을 공유하며, {@code recipeIds}가 1개면
 * 단일 실행과 동일하게 수렴한다(TYPE 표시만 SINGLE). 각 레시피는 canView로 접근 권한을 검증한다.
 *
 * @param userId         실행 사용자 (SSE 대상, 서버에서 세션값으로 덮어씀)
 * @param recipeIds      순차 실행할 레시피 ID 배열 (순서 = 실행 순서). 최소 1개
 * @param mode           실행 모드 (null이면 AUTO)
 * @param messageId      실행을 촉발한 plan 카드 메시지 ID (optional)
 * @param initialContext 발화에서 추출한 초기 입력값. 첫 레시피 변수 기본값을 덮어써 시드된다. null이면 기본값만 시드
 */
public record PlanStartRequest(
        Long userId,
        List<Long> recipeIds,
        ExecutionMode mode,
        Long messageId,
        Map<String, Object> initialContext) {
}
