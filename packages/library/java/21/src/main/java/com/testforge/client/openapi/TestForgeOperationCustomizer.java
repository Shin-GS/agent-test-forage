package com.testforge.client.openapi;

import com.testforge.client.annotation.TestForgeConfirm;
import com.testforge.client.annotation.TestForgeExclude;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TestForge 어노테이션을 OpenAPI 확장 필드(x-test-forge-*)로 변환한다.
 *
 * springdoc의 OperationCustomizer 구현. compileOnly 의존성이므로
 * 호스트 앱 클래스패스에 springdoc이 없으면 @ConditionalOnClass로 빈 미등록.
 *
 * 지원 어노테이션 (프로토타입 2종):
 * - @TestForgeExclude → x-test-forge-exclude
 * - @TestForgeConfirm → x-test-forge-confirm
 */
public class TestForgeOperationCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        // 클래스 레벨 @TestForgeExclude → 컨트롤러 전체 제외
        TestForgeExclude classExclude = handlerMethod.getBeanType().getAnnotation(TestForgeExclude.class);
        if (classExclude != null) {
            addExclude(operation, classExclude);
            return operation;
        }

        // 메서드 레벨 @TestForgeExclude
        TestForgeExclude methodExclude = handlerMethod.getMethodAnnotation(TestForgeExclude.class);
        if (methodExclude != null) {
            addExclude(operation, methodExclude);
        }

        // 메서드 레벨 @TestForgeConfirm
        TestForgeConfirm confirm = handlerMethod.getMethodAnnotation(TestForgeConfirm.class);
        if (confirm != null) {
            Map<String, String> value = new LinkedHashMap<>();
            value.put("message", confirm.message());
            operation.addExtension("x-test-forge-confirm", value);
        }

        return operation;
    }

    private void addExclude(Operation operation, TestForgeExclude exclude) {
        if (exclude.reason().isEmpty()) {
            operation.addExtension("x-test-forge-exclude", true);
        } else {
            Map<String, String> value = new LinkedHashMap<>();
            value.put("reason", exclude.reason());
            operation.addExtension("x-test-forge-exclude", value);
        }
    }
}
