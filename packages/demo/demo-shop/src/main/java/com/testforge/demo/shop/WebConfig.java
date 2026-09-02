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
        registry.addInterceptor(new SessionInterceptor(store))
                .addPathPatterns("/orders/**", "/payments/**");
    }
}
