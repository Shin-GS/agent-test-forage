package com.testforge.dto;

/**
 * Heartbeat 응답: { "action": "none" | "resend" }
 * - none: 변경 없음 (heartbeat 시각만 갱신됨)
 * - resend: 해시 불일치/미등록 → 라이브러리가 /register 재호출 필요
 */
public record HeartbeatResponse(
        // 라이브러리가 취할 동작 (none | resend)
        String action) {

    public static final String NONE = "none";
    public static final String RESEND = "resend";

    public static HeartbeatResponse none() {
        return new HeartbeatResponse(NONE);
    }

    public static HeartbeatResponse resend() {
        return new HeartbeatResponse(RESEND);
    }
}
