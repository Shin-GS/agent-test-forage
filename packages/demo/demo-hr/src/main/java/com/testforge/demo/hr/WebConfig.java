package com.testforge.demo.hr;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 인증이 필요한 경로에 {@link SessionInterceptor}를 등록한다.
 * 직원 등록(POST), 휴가 신청(POST), 휴가 승인(POST)만 세션 쿠키를 요구하고, 조회(GET)/로그인은 공개.
 *
 * <p>주의: /employees, /leaves 는 GET(공개 조회)과 POST(인증 쓰기)가 같은 경로를 공유한다.
 * 그래서 경로 패턴만으로는 GET 조회까지 보호돼 버린다. 이를 피하기 위해 인터셉터를 해당 경로에 걸되,
 * {@link SessionInterceptor}가 GET/OPTIONS 메서드를 항상 통과시켜 조회는 공개로 유지한다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final HrStore store;

    public WebConfig(HrStore store) {
        this.store = store;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 쓰기 계열만 보호: 직원 등록, 휴가 신청, 휴가 승인.
        // 인터셉터가 GET/OPTIONS 은 통과시키므로 같은 경로의 조회(GET)는 공개로 유지된다.
        registry.addInterceptor(new SessionInterceptor(store))
                .addPathPatterns("/employees", "/leaves", "/leaves/*/approve");
    }
}
