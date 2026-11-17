package com.ylz.futuremade.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 执行模型工具调用循环，达到次数提醒阈值后记录日志并继续执行。
 */
@Component
public class ToolCallingExecutor {
    private static final Logger log = LoggerFactory.getLogger(ToolCallingExecutor.class);
    private final AiModelGateway models;

    public ToolCallingExecutor(AiModelGateway models) {
        this.models = models;
    }

    @FunctionalInterface
    public interface ToolResultHandler {
        void accept(AssistantMessage.ToolCall call, String result) throws Exception;
    }

    public String execute(String system, String content, ToolCallback[] callbacks, int maxCalls, ToolResultHandler handler) throws Exception {
        var options = ToolCallingChatOptions.builder().toolCallbacks(callbacks).internalToolExecutionEnabled(false).build();
        var byName = Arrays.stream(callbacks).collect(Collectors.toMap(c -> c.getToolDefinition().name(), c -> c));
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(system));
        messages.add(new UserMessage(content));
        int count = 0;
        while (true) {
            var response = models.call(new Prompt(messages, options));
            if (response.hasFinishReasons(Set.of("length", "LENGTH")))
                throw new ModelOutputTruncatedException();
            if (!response.hasToolCalls()) return response.getResult().getOutput().getText();
            var assistant = response.getResult().getOutput();
            messages.add(assistant);
            List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
            for (var call : assistant.getToolCalls()) {
                var callback = byName.get(call.name());
                if (callback == null) throw new IllegalStateException("模型请求了未授权的工具");
                count++;
                if (count == maxCalls + 1) {
                    log.warn("本次 AI 任务的工具调用次数已超过提醒阈值（{} 次），继续执行", maxCalls);
                }
                String result;
                boolean validArguments = true;
                try {
                    result = callback.call(call.arguments());
                } catch (RuntimeException ex) {
                    JsonProcessingException formatError = findJsonError(ex);
                    if (formatError == null) throw ex;
                    String field = errorField(formatError);
                    log.warn("AI 工具参数格式错误，工具={}，字段={}；请模型修正后重试", call.name(), field);
                    result = "工具参数格式无效（字段：" + field + "）。请按照工具参数定义传入正确的 JSON 类型；整数编号必须是单个数字，不要使用范围或说明文字。";
                    validArguments = false;
                }
                if (validArguments) handler.accept(call, result);
                responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result));
            }
            messages.add(ToolResponseMessage.builder().responses(responses).build());
        }
    }

    /** 仅将参数反序列化错误反馈给模型，业务异常仍按原路径处理。 */
    private JsonProcessingException findJsonError(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof JsonProcessingException jsonError) return jsonError;
            if (cause.getCause() == cause) break;
        }
        return null;
    }

    /** 只输出字段路径，不输出模型参数值或会议正文。 */
    private String errorField(JsonProcessingException exception) {
        if (exception instanceof JsonMappingException mapping) {
            for (var reference : mapping.getPath()) {
                String name = reference.getFieldName();
                if (name != null && name.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")) return name;
            }
        }
        return "未知字段";
    }
}
