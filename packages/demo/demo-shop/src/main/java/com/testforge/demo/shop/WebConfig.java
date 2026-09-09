package com.testforge.demo.shop;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 인증이 필요한 경로에 {@link SessionInterceptor}를 등록한다.
 * orders/payments 계열 API만 세션 쿠키를 요구하고, 상품 조회/로그인은 공개.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ShopStore store;

    public WebConfig(ShopStore store) {
        this.store = store;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // SessionInterceptor가 GET/OPTIONS는 통과시키므로, prefix 전체를 걸어도
        // 조회(GET /coupons, GET /shipments/{id}, GET /cart)는 공개로 유지되고
        // 쓰기 메서드(POST 등)만 세션 검증 대상이 된다.
        registry.addInterceptor(new SessionInterceptor(store))
                .addPathPatterns("/orders/**", "/payments/**",
                        "/coupons/**", "/shipments/**", "/cart/**");
    }
}
