package com.testforge.demo.booking;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 인증이 필요한 경로에 {@link SessionInterceptor}를 등록한다.
 * 예약 생성(POST)·예약 취소 등 변경 API만 세션 쿠키를 요구하고, 조회/로그인은 공개.
 *
 * <p>주의: 조회(GET)는 공개해야 하므로 인터셉터를 특정 쓰기 경로에만 건다.
 * 좌석 조회와 예약 단건 조회는 GET 공개라 인터셉터 경로에서 제외하고,
 * 쓰기 전용 경로만 보호한다. 예약 취소 경로도 쓰기 계열이라 보호 대상이다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final BookingStore store;

    public WebConfig(BookingStore store) {
        this.store = store;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 쓰기 계열만 보호: 예약 생성, 예약 취소.
        // 조회(GET /seats, /seats/{id}, /bookings/{id})는 공개.
        registry.addInterceptor(new SessionInterceptor(store))
                .addPathPatterns("/bookings", "/bookings/*/cancel");
    }
}
