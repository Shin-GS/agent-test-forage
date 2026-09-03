package com.testforge.dto.execution;

import com.testforge.entity.execution.enums.ExecutionMode;

import java.util.Map;

/**
 * 단일 레시피 실행 시작 요청. execute_recipe 카드의 [자동 실행]/[직접 입력] 버튼이 트리거한다.
 * 실제 스텝 실행은 FE 브라우저가 수행하고, 이 요청은 서버에 "실행을 시작한다"고 알려
 * 스냅샷/레코드 생성 + 대화방 executing 전이를 하게 한다.
 *
 * <p>플랜 실행(recipeIds 여러 개)은 다음 조각에서 추가한다. 이번 조각은 단일 레시피만 다룬다.
 *
 * @param userId         실행 사용자 (SSE 대상)
 * @param recipeId       실행할 레시피 ID
 * @param mode           실행 모드 (null이면 AUTO)
 * @param messageId      실행을 촉발한 execution_mode 카드 메시지 ID (optional). 새로고침 복원 시
 *                       진행 블록을 촉발 메시지 위치에 배치하는 데 사용된다. null이면 미연결.
 * @param initialContext 발화에서 추출한 초기 입력값. 레시피 변수 기본값을 덮어써
 *                       실행 context({@code userInput})에 시드된다. null이면 기본값만 시드.
 */
public record ExecutionStartRequest(
        Long userId,
        Long recipeId,
        ExecutionMode mode,
        Long messageId,
        Map<String, Object> initialContext) {
}
