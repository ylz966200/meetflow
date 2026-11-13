package com.ylz.futuremade.assistant.service;

import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 执行受限的模型工具调用循环，业务方负责处理工具结果。
 */
@Component
public class ToolCallingExecutor {
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
                throw new IllegalStateException("模型输出因长度限制被截断");
            if (!response.hasToolCalls()) return response.getResult().getOutput().getText();
            var assistant = response.getResult().getOutput();
            messages.add(assistant);
            List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
            for (var call : assistant.getToolCalls()) {
                if (++count > maxCalls) throw new IllegalStateException("工具调用超过限制");
                var callback = byName.get(call.name());
                if (callback == null) throw new IllegalStateException("模型请求了未授权的工具");
                String result = callback.call(call.arguments());
                handler.accept(call, result);
                responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result));
            }
            messages.add(ToolResponseMessage.builder().responses(responses).build());
        }
    }
}
