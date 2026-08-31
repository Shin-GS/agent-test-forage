package com.testforge.client.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API 실행 전 사용자 확인을 요구한다.
 * 결제/발송 등 되돌릴 수 없는 API에 사용.
 *
 * OpenAPI 확장 필드 {@code x-test-forge-confirm} 으로 변환된다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeConfirm {

    /** 사용자에게 보여줄 확인 메시지 */
    String message() default "이 작업은 되돌릴 수 없습니다. 실행하시겠습니까?";
}
