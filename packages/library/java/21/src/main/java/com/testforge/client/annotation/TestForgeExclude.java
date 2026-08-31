package com.testforge.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API를 ai-test-forge 목록에서 제외한다.
 * 내부/관리용 API를 AI가 보지 못하게 할 때 사용.
 *
 * 메서드 또는 클래스(컨트롤러 전체)에 부착 가능.
 * OpenAPI 확장 필드 {@code x-test-forge-exclude} 로 변환된다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeExclude {

    /** 제외 사유 (선택, 문서화 목적) */
    String reason() default "";
}
