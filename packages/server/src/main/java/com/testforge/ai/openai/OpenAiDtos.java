package com.testforge.ai.openai;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 호환 Chat Completions API의 요청/응답 DTO 모음 (OpenAI·OpenRouter 공통 규약).
 * RestClient가 Jackson으로 직렬화/역직렬화한다. 필요한 필드만 정의하고, 알 수 없는 응답 필드는 무시한다
 * (OpenAiClient에서 매퍼에 FAIL_ON_UNKNOWN_PROPERTIES=false 설정).
 */
public final class OpenAiDtos {

    private OpenAiDtos() {
    }

    // ── 요청 ──

    /** chat/completions 요청 본문 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatRequest(
            String model,
            List<ChatMessage> messages,
            List<Tool> tools,
            // "required"면 반드시 tool 하나 선택, "auto"면 선택적,
            // {type:"function",function:{name}}이면 특정 함수 강제 (investigate 마지막 턴의 chat 강제)
            Object tool_choice,
            Double temperature) {
    }

    /**
     * 대화 메시지. 기본은 (role, content)이며, investigate 루프의 tool 결과 재주입을 위해
     * assistant의 {@code tool_calls}와 role:tool 결과({@code tool_call_id} 매칭)를 지원한다
     * (OpenAI 호환 규약: tool 메시지는 직전 assistant tool_calls와 id로 짝지어야 한다).
     * {@code NON_NULL}로 사용하지 않는 필드는 직렬화에서 빠진다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatMessage(
            String role,
            String content,
            // assistant가 tool을 호출한 경우의 tool_calls (그 외 null)
            List<ToolCall> tool_calls,
            // role:tool 메시지가 대응하는 assistant tool_call의 id (그 외 null)
            String tool_call_id) {

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content, null, null);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content, null, null);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content, null, null);
        }

        /** assistant가 tool_calls를 낸 메시지 (tool 결과 재주입 시 직전 assistant로 짝지음) */
        public static ChatMessage assistantToolCalls(List<ToolCall> toolCalls) {
            return new ChatMessage("assistant", null, toolCalls, null);
        }

        /** role:tool 결과 메시지 (assistant tool_call의 id에 매칭). content=조회 결과(데이터) */
        public static ChatMessage tool(String toolCallId, String content) {
            return new ChatMessage("tool", content, null, toolCallId);
        }
    }

    /** tool 정의 (type=function) */
    public record Tool(String type, FunctionDef function) {
        public static Tool function(FunctionDef def) {
            return new Tool("function", def);
        }
    }

    /** function 정의: 이름 + 설명 + 파라미터 JSON Schema(Map) */
    public record FunctionDef(String name, String description, Map<String, Object> parameters) {
    }

    // ── 응답 ──

    /** chat/completions 응답 본문 (필요 필드만) */
    public record ChatResponse(List<Choice> choices) {
    }

    public record Choice(ResponseMessage message) {
    }

    /** 응답 메시지: content(텍스트) 또는 tool_calls(도구 호출) */
    public record ResponseMessage(String content, List<ToolCall> tool_calls) {
    }

    public record ToolCall(String id, String type, FunctionCall function) {
    }

    /** 실제 호출된 function: 이름 + 인자(JSON 문자열) */
    public record FunctionCall(String name, String arguments) {
    }
}
