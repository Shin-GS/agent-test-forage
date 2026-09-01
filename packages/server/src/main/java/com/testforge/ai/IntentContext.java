package com.testforge.ai;

import java.util.List;

/**
 * IntentResolver 입력. 사용자 발화 + 대화방 컨텍스트를 담아 tool 선택에 필요한 정보를 모은다
 * (intent-classification.md의 "컨텍스트 분기").
 *
 * <p><b>서비스 지정 여부에 따른 분기:</b>
 * <ul>
 *   <li>서비스 지정({@code apiSpecId != null}): {@code recipes}에 해당 서비스 레시피 목록,
 *       {@code services}는 빈 리스트</li>
 *   <li>서비스 미지정({@code apiSpecId == null}): {@code services}에 서비스 목록,
 *       {@code recipes}는 빈 리스트 → resolver는 {@code select_service}/{@code chat}만 선택 가능</li>
 * </ul>
 */
public record IntentContext(
        // 발화 소유자 (SSE 대상)
        Long userId,
        // 대화방 ID
        Long conversationId,
        // 사용자 발화 (마지막 메시지 content)
        String utterance,
        // 대화방 서비스 (지정 스펙 ID, 미지정이면 null)
        Long apiSpecId,
        // 사용 가능한 레시피 (서비스 지정 시에만 채워짐)
        List<RecipeCandidate> recipes,
        // 사용 가능한 서비스 (서비스 미지정 시에만 채워짐)
        List<ServiceOption> services,
        // 참조 중인 레시피 ID (참조 태그, 없으면 null)
        String referenceId,
        // 최근 대화 이력 (오래된 → 최신 순, 현재 발화 제외)
        List<HistoryTurn> history) {

    /** 서비스 지정 여부 */
    public boolean hasService() {
        return apiSpecId != null;
    }

    /**
     * 대화 이력 한 턴의 최소 표현 (role + content). 토큰 절약을 위해 원시 metadata는 제외한다.
     */
    public record HistoryTurn(
            // "user" | "assistant" | "system"
            String role,
            // 본문 (요약/원문)
            String content) {
    }
}
