package com.testforge.common;

/**
 * DB에 문자열 코드로 저장되는 enum이 구현하는 공통 인터페이스.
 *
 * <p>코드값({@link #getCode()})과 사람이 읽는 한글 설명({@link #getDescription()})을
 * 함께 노출하여, enum 자체가 "저장 코드 ↔ 의미"를 문서화하도록 한다.
 * DB 매핑은 각 필드의 {@code @Enumerated(EnumType.STRING)}을 그대로 사용한다
 * (별도 AttributeConverter 도입 없음).
 */
public interface EnumColumn {

    /** DB에 저장되는 코드값. (현재는 enum name()과 동일) */
    String getCode();

    /** 화면/로그에서 사람이 읽는 한글 설명. */
    String getDescription();
}
