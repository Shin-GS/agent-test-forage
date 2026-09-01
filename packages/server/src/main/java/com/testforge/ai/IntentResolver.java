package com.testforge.ai;

/**
 * 사용자 발화 + 대화방 컨텍스트를 받아 실행할 tool을 결정하는 추상화
 * (intent-classification.md의 "1회 AI 호출로 의도 분석 + 분기").
 *
 * <p>이 인터페이스는 <b>구현 교체</b>를 전제로 한다. 프로토타입/키 미확보 단계에서는 규칙 기반
 * 목({@link RuleBasedIntentResolver})으로 전체 흐름(락·SSE·tool 핸들러)을 검증하고, OpenAI 키
 * 확보 후 Spring AI(ChatClient + ToolCallback) 구현으로 교체한다. ChatProcessor는 이 인터페이스에만
 * 의존하므로 상위 흐름은 변경되지 않는다.
 *
 * <p>구현은 다음 원칙을 지킨다(시스템 프롬프트 정책과 동일):
 * <ul>
 *   <li>레시피 목록에 없는 작업은 매칭하지 않는다 → no_match</li>
 *   <li>확실하지 않으면 추측하지 않는다 → clarify</li>
 *   <li>서비스 미지정이면 select_service 또는 chat만 반환 (레시피 정보가 없으므로)</li>
 * </ul>
 */
public interface IntentResolver {

    /**
     * 발화/컨텍스트를 분석해 실행할 tool과 파라미터를 결정한다.
     *
     * @param context 발화 + 서비스/레시피/이력 컨텍스트
     * @return 선택된 tool과 파라미터 (never null)
     */
    IntentResult resolve(IntentContext context);
}
