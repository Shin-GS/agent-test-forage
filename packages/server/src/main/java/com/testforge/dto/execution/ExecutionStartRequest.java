package com.testforge.dto.execution;

import com.testforge.entity.execution.enums.ExecutionMode;

import java.util.List;
import java.util.Map;

/**
 * 실행 시작 요청 (단일/플랜 공통). execute_recipe 카드의 [자동 실행]/[직접 입력] 또는 plan 카드의
 * [자동 실행]이 트리거한다. 실제 스텝 실행은 FE 브라우저가 수행하고, 이 요청은 서버에 "실행을 시작한다"고
 * 알려 스냅샷/레코드 생성 + 대화방 executing 전이를 하게 한다.
 *
 * <p><b>단일 = N=1 플랜 통일:</b> 단일 실행과 플랜 실행은 동일 엔드포인트를 쓰며 {@code recipeIds}의
 * 길이로만 구분된다(1개면 SINGLE, 여러 개면 PLAN). {@code recipeIds}의 순서가 곧 실행 순서다.
 * 각 레시피는 canView로 접근 권한을 검증한다. (userId는 세션에서 도출하므로 요청 바디에 없다.)
 *
 * @param recipeIds      순차 실행할 레시피 ID 배열 (순서 = 실행 순서). 최소 1개
 * @param mode           실행 모드 (null이면 AUTO)
 * @param messageId      실행을 촉발한 카드 메시지 ID (optional). 새로고침 복원 시 진행 블록을 촉발 메시지
 *                       위치에 배치하는 데 사용된다. null이면 미연결.
 * @param initialContext 발화에서 추출한 초기 입력값. 첫 레시피 변수 기본값을 덮어써 시드된다. null이면 기본값만 시드.
 * @param recipeInputs   레시피별 사전 편집값(plan.md). 배열 인덱스 = {@code recipeIds} 인덱스 = 실행 순서(sequence).
 *                       미편집 자리는 빈 맵 {@code {}}. null이면 전체 미편집. 각 레시피 RUNNING 전이 시 자기
 *                       sequence 값이 시드되며 발화값(initialContext)보다 우선한다.
 */
public record ExecutionStartRequest(
        List<Long> recipeIds,
        ExecutionMode mode,
        Long messageId,
        Map<String, Object> initialContext,
        List<Map<String, Object>> recipeInputs) {
}
