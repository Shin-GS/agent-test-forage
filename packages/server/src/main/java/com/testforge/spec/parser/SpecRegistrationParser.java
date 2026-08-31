package com.testforge.spec.parser;

import com.testforge.spec.dto.RegisterRequest;

/**
 * 버전이 있는 {@link RegisterRequest}를 내부 {@link NormalizedSpec} 모델로
 * 변환하는 전략.
 *
 * <p>계약 버전마다 별도 구현을 둔다. 새 버전 지원은 새 파서 빈을 추가하는 것으로
 * 끝나며 기존 코드는 수정하지 않는다 (개방-폐쇄 원칙).
 */
public interface SpecRegistrationParser {

    /** 주어진 계약(schema) 버전을 이 파서가 처리할 수 있는지 여부 */
    boolean supports(String schemaVersion);

    /** 원본 요청을 버전 무관 내부 모델로 정규화 */
    NormalizedSpec parse(RegisterRequest raw);
}
