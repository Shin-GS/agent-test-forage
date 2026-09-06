package com.testforge.ai.connector;

import java.util.List;

/**
 * 커넥터 1회 조회 결과 (investigation.md api_spec 커넥터 조회 정의).
 *
 * <ul>
 *   <li>{@code text}: AI에게 role:tool 메시지로 재주입할 조회 텍스트(데이터). 간접 프롬프트 인젝션
 *       방어를 위해 이 텍스트는 <b>데이터로만</b> 취급되어야 하며, 가드 문구는 주입 시점(InvestigateLoop)에서
 *       붙인다.</li>
 *   <li>{@code references}: 조회한 소스의 출처 링크 목록. 최종 답변 메시지의 references payload로 저장된다
 *       (messaging.md references 스키마). 조회 근거가 없으면 빈 리스트(억지 인용 금지).</li>
 *   <li>{@code found}: 조회로 유효한 근거를 얻었는지. false면 "못 찾음"으로 취급(할루시네이션 금지).</li>
 * </ul>
 *
 * @param text       AI 재주입용 조회 텍스트 (데이터, never null)
 * @param references 출처 링크 목록 (never null, 없으면 빈 리스트)
 * @param found      유효 근거 확보 여부
 */
public record ConnectorResult(
        String text,
        List<Reference> references,
        boolean found) {

    public ConnectorResult {
        if (text == null) {
            text = "";
        }
        references = references == null ? List.of() : List.copyOf(references);
    }

    /** 근거를 찾은 결과 */
    public static ConnectorResult found(String text, List<Reference> references) {
        return new ConnectorResult(text, references, true);
    }

    /** 못 찾은 결과 (references 없음) */
    public static ConnectorResult notFound(String text) {
        return new ConnectorResult(text, List.of(), false);
    }

    /**
     * 출처 참고 자료 1건 (messaging.md references payload 항목).
     *
     * @param source 소스 식별자 (1단계는 "api_spec")
     * @param label  버튼 표시명 (예: "POST /api/v1/users")
     * @param url    클릭 대상. 1단계 api_spec은 사이드 패널 스펙 상세를 여는 내부 식별자
     *               (예: "/specs/1/endpoints/42"), 외부 URL 이동이 아님
     */
    public record Reference(String source, String label, String url) {
    }
}
