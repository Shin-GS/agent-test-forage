package com.testforge.demo.blog;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 인증이 필요한 경로에 {@link SessionInterceptor}를 등록한다.
 * 글 작성(POST/DELETE)·댓글 작성 등 변경 API만 세션 쿠키를 요구하고, 조회/로그인은 공개.
 *
 * <p>주의: 조회(GET)는 공개해야 하므로 인터셉터를 특정 쓰기 경로에만 건다.
 * /posts, /posts/{id}, /posts/{id}/comments 는 GET 공개라 인터셉터 경로에서 제외하고,
 * 쓰기 전용 경로만 보호한다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final BlogStore store;

    public WebConfig(BlogStore store) {
        this.store = store;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 쓰기 계열만 보호: 글 발행/삭제, 댓글 작성.
        // 조회(GET /posts, /posts/{id}, /tags 등)는 공개.
        registry.addInterceptor(new SessionInterceptor(store))
                .addPathPatterns("/posts/*/publish", "/posts/*/comments", "/posts/*/delete");
    }
}
