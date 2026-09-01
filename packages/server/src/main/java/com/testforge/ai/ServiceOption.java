package com.testforge.ai;

/**
 * AI 컨텍스트/결과에서 다루는 서비스(등록된 스펙)의 최소 표현.
 *
 * <ul>
 *   <li>컨텍스트: 서비스 미지정 시 AI에게 전달하는 "사용 가능한 서비스" 목록의 한 항목
 *       (이름 + 한 줄 설명 — ai-config.md, 레시피는 미전달)</li>
 *   <li>결과: {@code select_service}가 추천하는 서비스 항목</li>
 * </ul>
 *
 * <p>{@code label}은 사용자에게 보여줄 표시명(기본은 name과 동일), {@code apiSpecId}는
 * 선택 확정 시 대화방 서비스로 설정할 스펙 ID다.
 */
public record ServiceOption(
        // 스펙 ID (대화방 서비스 설정 대상)
        Long apiSpecId,
        // 서비스 이름 (매칭/식별용)
        String name,
        // 사용자 표시명 (없으면 name 사용)
        String label,
        // 한 줄 설명 (없으면 null)
        String description) {

    public static ServiceOption of(Long apiSpecId, String name, String description) {
        return new ServiceOption(apiSpecId, name, name, description);
    }
}
