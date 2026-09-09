package com.testforge.demo.bank;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 인증이 필요한 경로에 {@link SessionInterceptor}를 등록한다.
 * 이체(POST /transfers)만 세션 쿠키를 요구하고, 계좌/거래내역/이체 조회(GET)와 로그인은 공개다.
 *
 * <p>인터셉터를 이체 경로에만 걸되, 인터셉터 내부에서 GET 요청은 통과시키므로
 * 이체 조회(GET /transfers/{id})는 영향받지 않는다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final BankStore store;

    public WebConfig(BankStore store) {
        this.store = store;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 이체 경로만 보호. 인터셉터가 GET 은 통과시키므로 실질적으로 POST /transfers 만 인증 대상.
        registry.addInterceptor(new SessionInterceptor(store))
                .addPathPatterns("/transfers");
    }
}
