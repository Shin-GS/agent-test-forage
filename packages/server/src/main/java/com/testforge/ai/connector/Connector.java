package com.testforge.ai.connector;

/**
 * 정보 조회 소스 추상화 (investigation.md 커넥터 스코프). investigate 루프가 소스별 조회를 이 인터페이스
 * 뒤로 위임하여, 새 소스(confluence/figma 등)를 구현체 추가만으로 확장할 수 있게 한다.
 *
 * <p>{@link ApiSpecConnector}(내부 DB 조회)와 {@link ConfluenceConnector}(Atlassian Cloud REST 조회)를 실제
 * 구현하고, figma 등은 인터페이스만 열어둔다(추후). 각 커넥터는 자신의 {@link #source()} 식별자를
 * 노출하고, InvestigateLoop이 AI가 반환한 source 값과 대조해 해당 커넥터로 라우팅한다. 지원 커넥터가
 * 없으면 "미지원 스킵"으로 처리된다 (카운터는 소비 — 카운터 우회 차단).
 *
 * <p><b>보안(SSRF 방지):</b> 조회 범위는 <b>현재 대화방 서비스에 연결된 스펙</b>으로 한정한다. 임의 스펙·
 * 임의 URL 조회는 불가하다(investigation.md 조회 범위 제한).
 */
public interface Connector {

    /** 이 커넥터가 담당하는 소스 식별자 (AI가 반환하는 source 값과 매칭). 예: "api_spec" */
    String source();

    /**
     * 대화방 서비스(apiSpecId) 범위 안에서 {@code query}로 정보를 조회한다.
     *
     * @param apiSpecId 현재 대화방 서비스(스펙) ID — 조회 범위 한정용 (null 불가: 미지정은 상위에서 차단)
     * @param query     조회 키워드/질문
     * @return 조회 텍스트 + 출처 references (근거 없으면 found=false)
     */
    ConnectorResult query(Long apiSpecId, String query);
}
