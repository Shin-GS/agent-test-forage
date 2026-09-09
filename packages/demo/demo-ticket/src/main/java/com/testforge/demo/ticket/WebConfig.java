package com.testforge.demo.ticket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 인증이 필요한 경로에 {@link SessionInterceptor}를 등록한다.
 * 티켓 하위 경로 전체에 인터셉터를 걸되, 인터셉터 내부에서 GET/OPTIONS는 통과시키므로
 * 실제로는 티켓 생성/상태변경, 코멘트 작성 등 변경 요청만 세션 쿠키를 요구한다.
 * 로그인/로그인 페이지는 인터셉터 경로 밖이라 공개다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TicketStore store;

    public WebConfig(TicketStore store) {
        this.store = store;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SessionInterceptor(store))
                .addPathPatterns("/tickets/**");
    }
}
