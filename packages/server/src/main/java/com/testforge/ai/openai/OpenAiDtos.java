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
            // "required"면 반드시 tool 하나 선택, "auto"면 선택적
            Object tool_choice,
            Double temperature) {
    }

    /** 대화 메시지 (role: system/user/assistant) */
    public record ChatMessage(String role, String content) {
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
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
