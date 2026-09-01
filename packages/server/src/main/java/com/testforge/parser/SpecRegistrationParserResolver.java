package com.testforge.parser;

import com.testforge.common.error.ApiException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 주어진 계약(schema) 버전을 지원하는 {@link SpecRegistrationParser}를 선택한다.
 * 파서는 Spring 컨텍스트에서 발견되므로, 새 버전 추가 시 새 파서 빈만 등록하면
 * 이 리졸버는 손대지 않아도 된다.
 */
@Component
public class SpecRegistrationParserResolver {

    private final List<SpecRegistrationParser> parsers;

    public SpecRegistrationParserResolver(List<SpecRegistrationParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * 매칭되는 파서가 없으면 UNSUPPORTED_SCHEMA_VERSION(400) {@link ApiException}을 던진다.
     */
    public SpecRegistrationParser resolve(String schemaVersion) {
        return parsers.stream()
                .filter(p -> p.supports(schemaVersion))
                .findFirst()
                .orElseThrow(() -> ApiException.unsupportedSchemaVersion(schemaVersion));
    }
}
