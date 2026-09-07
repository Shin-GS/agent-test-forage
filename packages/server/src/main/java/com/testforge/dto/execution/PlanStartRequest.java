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
 * <p><b>값 사전 편집(plan.md):</b> 제안 카드의 [값 지정] 아코디언에서 레시피별로 편집한 입력값을
 * {@code recipeInputs}로 함께 전달한다. 각 레시피가 RUNNING으로 전이될 때 그 sequence의 편집값이
 * 시드되어(발화값보다 우선), required가 모두 채워지면 pre-run 액션 피커 없이 자동 실행이 이어진다.
 *
 * @param userId         실행 사용자 (SSE 대상, 서버에서 세션값으로 덮어씀)
 * @param recipeIds      순차 실행할 레시피 ID 배열 (순서 = 실행 순서). 최소 1개
 * @param mode           실행 모드 (null이면 AUTO)
 * @param messageId      실행을 촉발한 plan 카드 메시지 ID (optional)
 * @param initialContext 발화에서 추출한 초기 입력값. 첫 레시피 변수 기본값을 덮어써 시드된다. null이면 기본값만 시드
 * @param recipeInputs   레시피별 사전 편집값. 배열 인덱스 = {@code recipeIds} 인덱스 = 실행 순서(sequence).
 *                       미편집 자리는 빈 맵 {@code {}}. null이면 전체 미편집(기존 동작과 동일).
 *                       각 레시피 RUNNING 전이 시 자기 sequence 값이 시드되며 발화값(initialContext)보다 우선한다.
 */
public record PlanStartRequest(
        Long userId,
        List<Long> recipeIds,
        ExecutionMode mode,
        Long messageId,
        Map<String, Object> initialContext,
        List<Map<String, Object>> recipeInputs) {
}
