package com.testforge.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * 레시피의 JSON 필드(스텝/변수/태그 등)를 다루는 작은 Jackson 헬퍼.
 * SpecQueryService가 new ObjectMapper()를 쓰는 것과 일관되게 공용 매퍼 빈에 의존하지 않는다.
 *
 * <p>요청/응답 DTO는 JSON 필드를 범용 {@link Object}(Map/List로 역직렬화됨)로 다룬다.
 * Spring 7 Jackson 통합이 DTO 필드 타입으로 {@code JsonNode}를 직접 받지 못하기 때문이다.
 */
public final class RecipeJsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RecipeJsonUtil() {
    }

    /** 요청으로 받은 객체(Map/List 등)를 저장용 문자열로 직렬화한다. null이면 null 반환. */
    public static String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize JSON value", e);
        }
    }

    /** 저장된 JSON 문자열을 범용 객체(Map/List)로 파싱한다. null/빈/실패 시 null 반환. */
    public static Object toObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 스텝 JSON 문자열을 List&lt;Map&lt;String, Object&gt;&gt;로 파싱한다.
     * null/빈 값이면 빈 리스트, 최상위가 배열이 아니거나 파싱 실패 시 {@link IllegalArgumentException}.
     */
    public static List<Map<String, Object>> parseSteps(String stepsJson) {
        if (stepsJson == null || stepsJson.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(stepsJson, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("Steps must be a JSON array of step objects", e);
        }
    }

    /** 태그 JSON 문자열을 List&lt;String&gt;으로 파싱한다. null/빈/실패 시 빈 리스트. */
    public static List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(tagsJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
